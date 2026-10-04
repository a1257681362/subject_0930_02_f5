package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.NoticeAckRequest;
import com.evops.aqua.tide.dto.WindowCloseRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.entity.TideWindowClosure;
import com.evops.aqua.tide.entity.TideWindowNotice;
import com.evops.aqua.tide.mapper.TideWindowClosureMapper;
import com.evops.aqua.tide.mapper.TideWindowNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 窗口临时关闭与通知回执：关闭原因必填；关闭后向全部受影响班组发送通知（每班组一行），
 * 班组必须回执，只追加；同窗口只允许关闭一次，requestId 幂等返回首次关闭结果。
 */
@Service
public class TideNoticeService {

    private final TideWindowClosureMapper closureMapper;
    private final TideWindowNoticeMapper noticeMapper;
    private final TideWindowService windowService;
    private final TideScheduleService scheduleService;
    private final TideEvidenceService evidenceService;

    public TideNoticeService(TideWindowClosureMapper closureMapper,
                             TideWindowNoticeMapper noticeMapper,
                             TideWindowService windowService,
                             TideScheduleService scheduleService,
                             TideEvidenceService evidenceService) {
        this.closureMapper = closureMapper;
        this.noticeMapper = noticeMapper;
        this.windowService = windowService;
        this.scheduleService = scheduleService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closeWindow(Long windowId, WindowCloseRequest request) {
        TideWindowClosure duplicated = closureMapper.selectOne(
                new QueryWrapper<TideWindowClosure>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return closureDetail(duplicated);
        }
        TideWindow window = windowService.getOrThrow(windowId);
        if (TideWindow.STATUS_CLOSED.equals(window.getWindowStatus())) {
            throw new IllegalArgumentException("窗口已临时关闭，不能重复关闭：" + window.getWindowNo());
        }
        TideSchedule schedule = scheduleService.findByWindowId(windowId);

        TideWindowClosure closure = new TideWindowClosure();
        closure.setWindowId(windowId);
        closure.setRequestId(request.getRequestId());
        closure.setCloseReason(request.getCloseReason());
        closure.setClosedBy(request.getClosedBy());
        closure.setClosedTime(LocalDateTime.now());
        closureMapper.insert(closure);

        // 受影响班组：窗口已排班则通知其作业班组；未排班窗口无可通知班组
        List<TideWindowNotice> notices = new ArrayList<>();
        if (schedule != null && !TideSchedule.STATUS_CANCELLED.equals(schedule.getScheduleStatus())) {
            TideWindowNotice notice = new TideWindowNotice();
            notice.setClosureId(closure.getId());
            notice.setScheduleId(schedule.getId());
            notice.setCrewId(schedule.getCrewId());
            notice.setNoticeStatus(TideWindowNotice.STATUS_SENT);
            notice.setSentTime(LocalDateTime.now());
            noticeMapper.insert(notice);
            notices.add(notice);

            TideSchedule before = scheduleService.getOrThrow(schedule.getId());
            schedule.setScheduleStatus(TideSchedule.STATUS_CANCELLED);
            schedule.setChangeReason("窗口临时关闭：" + request.getCloseReason());
            scheduleService.cancelForClosure(schedule);

            evidenceService.record(TideChangeEvidence.ENTITY_NOTICE, notice.getId(),
                    TideChangeEvidence.ACTION_NOTICE_SEND, request.getRequestId(),
                    request.getClosedBy(), before, notice,
                    "窗口关闭通知已发送：窗口=" + window.getWindowNo()
                            + "，班组id=" + schedule.getCrewId() + "，排班=" + schedule.getScheduleNo()
                            + "，关闭原因=" + request.getCloseReason());
        }

        window.setWindowStatus(TideWindow.STATUS_CLOSED);
        window.setFrozenFlag(true);
        windowService.updateStatusAndCrew(window);

        evidenceService.record(TideChangeEvidence.ENTITY_CLOSURE, closure.getId(),
                TideChangeEvidence.ACTION_WINDOW_CLOSE, request.getRequestId(),
                request.getClosedBy(), null, closure,
                "临时关闭换水窗口：" + window.getWindowNo()
                        + "，时段[" + window.getWindowStart() + "," + window.getWindowEnd()
                        + ")，受影响班组数=" + notices.size() + "，原因=" + request.getCloseReason());

        Map<String, Object> result = closureDetail(closure);
        return result;
    }

    /** 班组回执：同一通知重复回执返回首次回执（幂等，不覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public TideWindowNotice acknowledge(Long noticeId, NoticeAckRequest request) {
        TideWindowNotice notice = getNoticeOrThrow(noticeId);
        if (TideWindowNotice.STATUS_ACKED.equals(notice.getNoticeStatus())) {
            return notice;
        }
        TideWindowNotice before = copyNotice(notice);
        notice.setNoticeStatus(TideWindowNotice.STATUS_ACKED);
        notice.setAckBy(request.getAckBy());
        notice.setAckTime(LocalDateTime.now());
        notice.setAckRemark(request.getAckRemark());
        noticeMapper.updateById(notice);

        evidenceService.record(TideChangeEvidence.ENTITY_NOTICE, notice.getId(),
                TideChangeEvidence.ACTION_NOTICE_ACK, null, request.getAckBy(),
                before, notice,
                "班组确认已知悉窗口关闭通知：通知id=" + notice.getId()
                        + "，班组id=" + notice.getCrewId());
        return notice;
    }

    public TideWindowNotice getNoticeOrThrow(Long id) {
        TideWindowNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new IllegalArgumentException("关闭通知不存在，id=" + id);
        }
        return notice;
    }

    public List<TideWindowNotice> listNotices(Long crewId, String status) {
        QueryWrapper<TideWindowNotice> wrapper = new QueryWrapper<>();
        wrapper.eq(crewId != null, "crew_id", crewId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "notice_status", status);
        wrapper.orderByAsc("id");
        return noticeMapper.selectList(wrapper);
    }

    public TideWindowClosure getClosureOrThrow(Long id) {
        TideWindowClosure closure = closureMapper.selectById(id);
        if (closure == null) {
            throw new IllegalArgumentException("窗口关闭记录不存在，id=" + id);
        }
        return closure;
    }

    public List<TideWindowClosure> listClosures() {
        return closureMapper.selectList(new QueryWrapper<TideWindowClosure>().orderByAsc("id"));
    }

    private Map<String, Object> closureDetail(TideWindowClosure closure) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("closure", closure);
        detail.put("notices", noticeMapper.selectList(
                new QueryWrapper<TideWindowNotice>().eq("closure_id", closure.getId()).orderByAsc("id")));
        return detail;
    }

    private TideWindowNotice copyNotice(TideWindowNotice s) {
        TideWindowNotice c = new TideWindowNotice();
        c.setId(s.getId());
        c.setClosureId(s.getClosureId());
        c.setScheduleId(s.getScheduleId());
        c.setCrewId(s.getCrewId());
        c.setNoticeStatus(s.getNoticeStatus());
        c.setSentTime(s.getSentTime());
        c.setAckBy(s.getAckBy());
        c.setAckTime(s.getAckTime());
        return c;
    }
}
