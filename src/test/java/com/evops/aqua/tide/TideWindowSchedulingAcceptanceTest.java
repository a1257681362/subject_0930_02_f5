package com.evops.aqua.tide;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.entity.TideWindowNotice;
import com.evops.aqua.tide.mapper.TideChangeEvidenceMapper;
import com.evops.aqua.tide.mapper.TideScheduleMapper;
import com.evops.aqua.tide.mapper.TideWindowMapper;
import com.evops.aqua.tide.mapper.TideWindowNoticeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 潮汐窗口换水排班验收。
 * 主线：海区潮表 → 候选（半开）→ 减禁作天气/交班次 → 可执行窗口（统一口径归属）
 * → 同设备同时段单池互斥（相接允许）→ 发布后修改走调度审批、顺延不改原计划
 * → 临时关闭通知班组并留回执 → 重算冻结已排班窗口 → 全程留证。
 * 专项：直接把潮点调到 00:00 等临界时刻重算，换水窗口必须仍按"半开起始时刻所在自然日"同一口径归属。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TideWindowSchedulingAcceptanceTest {

    private static final String D0 = "2026-10-09";
    private static final String D1 = "2026-10-10";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PondMapper pondMapper;
    @Autowired
    private TideWindowMapper windowMapper;
    @Autowired
    private TideScheduleMapper scheduleMapper;
    @Autowired
    private TideWindowNoticeMapper noticeMapper;
    @Autowired
    private TideChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondA;
    private Long pondB;
    private Long pondC;
    private Long pondD;
    private Long pondE;
    private Long crew1;
    private Long crew2;
    private Long crew3;
    private Long crew4;
    private Long equip1;
    private Long equip2;

    private JsonNode lastGenerate;

    private Long schA1;
    private Long schA2;
    private Long schB1;
    private Long schC1;
    private Long changeId;
    private Long noticeId;

    @Test
    void acceptanceTideWindowScheduling() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建档：五口池分属四个海区、候选规则、禁作天气、班组班次、换水设备", () -> {
            pondA = createPond("TIDE-PA", "潮汐甲池", "Z01");
            pondB = createPond("TIDE-PB", "潮汐乙池", "Z02");
            pondC = createPond("TIDE-PC", "潮汐丙池", "Z02");
            pondD = createPond("TIDE-PD", "潮汐丁池", "Z03");
            pondE = createPond("TIDE-PE", "潮汐戊池", "Z04");
            check(pondMapper.selectCount(new QueryWrapper<Pond>().likeRight("pond_code", "TIDE-")) == 5L,
                    "五口潮汐池应全部落库");

            post("/api/aqua/tide/window-rules", ruleBody("Z01", "一区规则", 60, 120));
            // Z02 潮前0偏移：08:00 高潮候选恰好 [08:00,09:00)，用于验证与 [06,08) 排班端点相接
            post("/api/aqua/tide/window-rules", ruleBody("Z02", "二区规则", 0, 60));
            post("/api/aqua/tide/window-rules", ruleBody("Z03", "三区规则", 60, 120));
            post("/api/aqua/tide/window-rules", ruleBody("Z04", "四区规则", 60, 120));

            crew1 = post("/api/aqua/tide/crews", crewBody("C1", "甲班")).path("data").path("id").asLong();
            crew2 = post("/api/aqua/tide/crews", crewBody("C2", "乙班")).path("data").path("id").asLong();
            crew3 = post("/api/aqua/tide/crews", crewBody("C3", "无班次班")).path("data").path("id").asLong();
            crew4 = post("/api/aqua/tide/crews", crewBody("C4", "临界班")).path("data").path("id").asLong();

            // 甲班：跨零点班（覆盖 00:00 高潮候选）+ 10日白班 + 10日午晚班
            post("/api/aqua/tide/crew-shifts", shiftBody(crew1, "2026-10-08T23:00:00", "2026-10-09T02:00:00"));
            post("/api/aqua/tide/crew-shifts", shiftBody(crew1, "2026-10-10T04:00:00", "2026-10-10T12:00:00"));
            post("/api/aqua/tide/crew-shifts", shiftBody(crew1, "2026-10-10T12:00:00", "2026-10-10T22:00:00"));
            // 乙班：10日大全天班
            post("/api/aqua/tide/crew-shifts", shiftBody(crew2, "2026-10-10T06:00:00", "2026-10-10T22:00:00"));
            // 临界班：覆盖跨零点重算
            post("/api/aqua/tide/crew-shifts", shiftBody(crew4, "2026-10-09T22:00:00", "2026-10-11T00:00:00"));

            // Z01 禁作天气：[05,08) 切掉早高潮候选头部；[08,09) 与候选端点相接
            post("/api/aqua/tide/weather-bans",
                    banBody("Z01", "2026-10-10T05:00:00", "2026-10-10T06:00:00", "RAIN", "暴雨禁作"));
            post("/api/aqua/tide/weather-bans",
                    banBody("Z01", "2026-10-10T08:00:00", "2026-10-10T09:00:00", "THUNDER", "与窗口端点相接"));
            // Z03 禁作天气：全覆盖候选
            post("/api/aqua/tide/weather-bans",
                    banBody("Z03", "2026-10-10T09:00:00", "2026-10-10T12:00:00", "TYPHOON", "台风全程禁作"));
            // Z04 禁作天气：用于临界重算（[06,07)）
            post("/api/aqua/tide/weather-bans",
                    banBody("Z04", "2026-10-10T06:00:00", "2026-10-10T07:00:00", "RAIN", "临界裁剪"));

            equip1 = post("/api/aqua/tide/equipment", equipmentBody("PUMP-1", "1号换水泵")).path("data").path("id").asLong();
            equip2 = post("/api/aqua/tide/equipment", equipmentBody("PUMP-2", "2号换水泵")).path("data").path("id").asLong();
        });

        checkpoint("CP2", "导入潮汐表：Z01 含 00:00/06:00/14:00 高潮；Z02 含 08:00/15:00；Z03 全程禁作；Z04 可调临界", () -> {
            JsonNode z01d0 = post("/api/aqua/tide/tide-tables", tideBody("TT-Z01-D0", "Z01", D0,
                    point(1, "HIGH", D0 + "T00:00:00", 380),
                    point(2, "LOW", D0 + "T06:00:00", 120)));
            assertSuccess(z01d0, "导入Z01潮表D0");
            JsonNode z01d1 = post("/api/aqua/tide/tide-tables", tideBody("TT-Z01-D1", "Z01", D1,
                    point(1, "HIGH", D1 + "T06:00:00", 410),
                    point(2, "HIGH", D1 + "T14:00:00", 430)));
            assertSuccess(z01d1, "导入Z01潮表D1");
            assertSuccess(post("/api/aqua/tide/tide-tables", tideBody("TT-Z02-D1", "Z02", D1,
                    point(1, "HIGH", D1 + "T08:00:00", 400),
                    point(2, "HIGH", D1 + "T15:00:00", 420))), "导入Z02潮表");
            assertSuccess(post("/api/aqua/tide/tide-tables", tideBody("TT-Z03-D1", "Z03", D1,
                    point(1, "HIGH", D1 + "T10:00:00", 390))), "导入Z03潮表");
            assertSuccess(post("/api/aqua/tide/tide-tables", tideBody("TT-Z04-D1", "Z04", D1,
                    point(1, "HIGH", D1 + "T08:00:00", 395))), "导入Z04潮表");

            // 重复导入同海区同日为覆盖，证据留两次
            assertSuccess(post("/api/aqua/tide/tide-tables", tideBody("TT-Z01-D1", "Z01", D1,
                    point(1, "HIGH", D1 + "T06:00:00", 410),
                    point(2, "HIGH", D1 + "T14:00:00", 430))), "覆盖导入Z01潮表D1");
            long importEvidence = evidenceCount(TideChangeEvidence.ACTION_TIDE_IMPORT, null);
            check(importEvidence >= 5, "潮汐表导入/覆盖应逐次留证，实际=" + importEvidence);
        });

        checkpoint("CP3", "甲池生成窗口：半开天气裁剪、端点相接不裁、00:00 高潮归属前一日", () -> {
            lastGenerate = post("/api/aqua/tide/windows/generate",
                    generateBody(pondA, "2026-10-08", "2026-10-11"));
            assertSuccess(lastGenerate, "甲池生成窗口");
            check(lastGenerate.path("data").path("createdCount").asInt() == 3,
                    "甲池应生成3个可执行窗口（跨零点/早潮/午后潮），实际="
                            + lastGenerate.path("data").path("createdCount").asInt());

            List<TideWindow> windows = windowsOf(pondA);
            check(windows.size() == 3, "甲池窗口落库数应为3，实际=" + windows.size());

            TideWindow w0 = windows.get(0);
            check("2026-10-08T23:00".equals(stamp(w0.getWindowStart())),
                    "00:00高潮候选应按潮前60分从23:00起，实际=" + w0.getWindowStart());
            check("2026-10-09T02:00".equals(stamp(w0.getWindowEnd())),
                    "跨零点窗口应到次日02:00，实际=" + w0.getWindowEnd());
            check("2026-10-08".equals(w0.getPlanDate().toString()),
                    "统一口径：窗口归属日=半开起始时刻所在自然日，00:00潮点窗口必须归属10-08，实际="
                            + w0.getPlanDate());

            TideWindow w1 = windows.get(1);
            check("2026-10-10T06:00".equals(stamp(w1.getWindowStart())),
                    "禁作[05,06)半开相减后窗口应06:00起，实际=" + w1.getWindowStart());
            check("2026-10-10T08:00".equals(stamp(w1.getWindowEnd())),
                    "禁作[08,09)与候选端点相接，不得裁剪，窗口应仍到08:00，实际=" + w1.getWindowEnd());
            check("2026-10-10".equals(w1.getPlanDate().toString()), "早潮窗口归属10-10");

            TideWindow w2 = windows.get(2);
            check("2026-10-10T13:00".equals(stamp(w2.getWindowStart()))
                    && "2026-10-10T16:00".equals(stamp(w2.getWindowEnd())),
                    "午后潮窗口应为[13:00,16:00)，实际=[" + w2.getWindowStart() + "," + w2.getWindowEnd() + ")");

            for (TideWindow w : windows) {
                JsonNode detail = get("/api/aqua/tide/windows/" + w.getId());
                check(detail.path("data").path("belongDateConsistent").asBoolean(false),
                        "窗口" + w.getWindowNo() + "归属日口径不一致");
            }
        });

        checkpoint("CP4", "临界专项：SQL 把潮点调到 00:00 与天气边界，重算后窗口仍按同一口径归属", () -> {
            // Z02 两池先生成窗口：[08,09) 与 [14,16)
            JsonNode genB = post("/api/aqua/tide/windows/generate", generateBody(pondB, D1, D1));
            JsonNode genC = post("/api/aqua/tide/windows/generate", generateBody(pondC, D1, D1));
            check(genB.path("data").path("createdCount").asInt() == 2
                    && genC.path("data").path("createdCount").asInt() == 2,
                    "Z02两池各应生成2个窗口");

            // Z03 候选被天气全程覆盖：不生成窗口，只留排除说明
            JsonNode genD = post("/api/aqua/tide/windows/generate", generateBody(pondD, D1, D1));
            check(genD.path("data").path("createdCount").asInt() == 0, "全程禁作海区不应产出窗口");
            check(genD.path("data").path("excludedCount").asInt() == 1
                    && genD.path("data").path("excluded").get(0).path("reason").asText().contains("禁作天气"),
                    "被排除候选必须逐条说明禁作天气原因");

            // Z04 初始窗口 [07,10) 归属10-10
            JsonNode genE = post("/api/aqua/tide/windows/generate", generateBody(pondE, "2026-10-09", D1));
            List<TideWindow> eWindows = windowsOf(pondE);
            check(eWindows.size() == 1 && "2026-10-10T07:00".equals(stamp(eWindows.get(0).getWindowStart())),
                    "Z04初始窗口应为[07:00,10:00)");

            Long z04TableId = jdbcTemplate.queryForObject(
                    "SELECT id FROM t_tide_table WHERE sea_area='Z04' AND tide_date='2026-10-10'",
                    Long.class);
            // 把潮点直接调到临界 00:00
            jdbcTemplate.update("UPDATE t_tide_point SET point_time=? WHERE tide_table_id=? AND point_seq=1",
                    Timestamp.valueOf(LocalDateTime.of(2026, 10, 10, 0, 0)), z04TableId);
            JsonNode reGen = post("/api/aqua/tide/windows/generate", generateBody(pondE, "2026-10-09", D1));
            check(reGen.path("data").path("createdCount").asInt() == 1, "临界重算应产出1个窗口");
            List<TideWindow> eWindows2 = windowsOf(pondE);
            TideWindow cross = eWindows2.get(0);
            check("2026-10-09T23:00".equals(stamp(cross.getWindowStart()))
                    && "2026-10-10T02:00".equals(stamp(cross.getWindowEnd())),
                    "潮点调到00:00后候选应为[10-09 23:00,10-10 02:00)，实际=["
                            + cross.getWindowStart() + "," + cross.getWindowEnd() + ")");
            check("2026-10-09".equals(cross.getPlanDate().toString()),
                    "临界归属：跨零点窗口必须按起始时刻归属10-09，实际=" + cross.getPlanDate());
            check(cross.getPlanDate().equals(cross.getWindowStart().toLocalDate()),
                    "归属日与半开起始日必须同一口径");

            // 再把潮点调到与天气禁作边界相切的 07:00（候选[06,09)，禁作[06,07)）
            jdbcTemplate.update("UPDATE t_tide_point SET point_time=? WHERE tide_table_id=? AND point_seq=1",
                    Timestamp.valueOf(LocalDateTime.of(2026, 10, 10, 7, 0)), z04TableId);
            JsonNode reGen2 = post("/api/aqua/tide/windows/generate", generateBody(pondE, "2026-10-09", D1));
            check(reGen2.path("data").path("createdCount").asInt() == 1, "边界相切重算应产出1个窗口");
            TideWindow boundary = windowsOf(pondE).get(0);
            check("2026-10-10T07:00".equals(stamp(boundary.getWindowStart()))
                    && "2026-10-10T09:00".equals(stamp(boundary.getWindowEnd())),
                    "半开相减边界应得[07:00,09:00)，实际=["
                            + boundary.getWindowStart() + "," + boundary.getWindowEnd() + ")");
            check("2026-10-10".equals(boundary.getPlanDate().toString()), "边界窗口归属10-10");
        });

        checkpoint("CP5", "设备互斥：同设备重叠拒绝并留失败证据，端点相接允许；班组无班次拒绝", () -> {
            // 甲池早潮 [06,08)=index1、午后潮 [13,16)=index2 排给1号泵（index0 为跨零点窗口）
            schA1 = prepareSchedule(windowIdAt(pondA, 1), equip1, crew1, null, null, "scheduler-a");
            publish(schA1, "scheduler-a");
            schA2 = prepareSchedule(windowIdAt(pondA, 2), equip1, crew1, null, null, "scheduler-a");
            publish(schA2, "scheduler-a");

            // 乙池 [08,09) 与甲池早潮端点相接：1号泵允许
            schB1 = prepareSchedule(windowIdAt(pondB, 0), equip1, crew2, null, null, "scheduler-b");
            publish(schB1, "scheduler-b");

            // 丙池 [14,16) 与甲池午后潮[13,16) 在1号泵上重叠：拒绝
            JsonNode conflict = post("/api/aqua/tide/schedules", prepareBody(
                    windowIdAt(pondC, 1), equip1, crew2, null, null, "scheduler-c"));
            check(!conflict.path("success").asBoolean(true)
                            && conflict.path("message").asText().contains("同一时间只能服务一口池"),
                    "同设备重叠排班必须被拦截，实际=" + conflict.path("message").asText());
            check(evidenceCount(TideChangeEvidence.ACTION_SCHEDULE_CONFLICT, null) >= 1,
                    "设备冲突必须独立事务留证（业务回滚证据不丢）");

            // 无班次班组排班：拒绝
            JsonNode noShift = post("/api/aqua/tide/schedules", prepareBody(
                    windowIdAt(pondC, 1), equip2, crew3, null, null, "scheduler-c"));
            check(!noShift.path("success").asBoolean(true)
                            && noShift.path("message").asText().contains("班次"),
                    "作业时段无完整班次覆盖必须拦截，实际=" + noShift.path("message").asText());

            // 2号泵同时间段服务丙池：允许
            schC1 = prepareSchedule(windowIdAt(pondC, 1), equip2, crew2, null, null, "scheduler-c");
            publish(schC1, "scheduler-c");

            long pump1Count = scheduleMapper.selectCount(new QueryWrapper<TideSchedule>()
                    .eq("equipment_id", equip1)
                    .in("schedule_status", TideSchedule.STATUS_PUBLISHED, TideSchedule.STATUS_DRAFT));
            check(pump1Count == 3L, "1号泵应只有3张有效排班（相接不冲突），实际=" + pump1Count);
        });

        checkpoint("CP6", "发布后修改经调度审批：驳回维持原计划，批准才改期；顺延不改原计划", () -> {
            // 未发布排班不允许申请修改
            JsonNode draftSchedule = post("/api/aqua/tide/schedules", prepareBody(
                    windowIdAt(pondB, 1), equip2, crew2, null, null, "scheduler-b"));
            Long draftId = draftSchedule.path("data").path("id").asLong();
            JsonNode changeOnDraft = post("/api/aqua/tide/schedules/" + draftId + "/changes",
                    changeBody("2026-10-10T15:00:00", "2026-10-10T16:00:00", "试试", "u1", "REQ-DRAFT"));
            check(!changeOnDraft.path("success").asBoolean(true), "草稿排班修改不应走审批通道");

            // 已发布排班申请修改 → 待审批
            JsonNode changeResp = post("/api/aqua/tide/schedules/" + schA1 + "/changes",
                    changeBody("2026-10-10T06:30:00", "2026-10-10T07:30:00", "工人请假", "foreman", "REQ-1"));
            assertSuccess(changeResp, "提交修改申请");
            changeId = changeResp.path("data").path("id").asLong();
            check(TideSchedule.STATUS_ADJUST_PENDING.equals(scheduleMapper.selectById(schA1).getScheduleStatus()),
                    "申请后排班应为待审批状态");

            // requestId 幂等
            JsonNode changeDup = post("/api/aqua/tide/schedules/" + schA1 + "/changes",
                    changeBody("2026-10-10T06:30:00", "2026-10-10T07:30:00", "工人请假", "foreman", "REQ-1"));
            check(changeDup.path("data").path("id").asLong() == changeId, "修改申请requestId应幂等");

            // 驳回不填意见：拒绝
            JsonNode rejectNoRemark = post("/api/aqua/tide/schedules/changes/" + changeId + "/approve",
                    approveBody("REJECTED", "dispatcher", null));
            check(!rejectNoRemark.path("success").asBoolean(true), "驳回必须填审批意见");

            // 驳回：维持原计划
            assertSuccess(post("/api/aqua/tide/schedules/changes/" + changeId + "/approve",
                    approveBody("REJECTED", "dispatcher", "不同意，潮时不合适")), "驳回修改");
            TideSchedule afterReject = scheduleMapper.selectById(schA1);
            check(TideSchedule.STATUS_PUBLISHED.equals(afterReject.getScheduleStatus()),
                    "驳回后排班应回到已发布");
            check(stamp(afterReject.getScheduledStart()).equals("2026-10-10T06:00")
                            && stamp(afterReject.getOriginalStart()).equals("2026-10-10T06:00"),
                    "驳回后计划与原计划都应不变");

            // 再次申请并批准
            JsonNode change2 = post("/api/aqua/tide/schedules/" + schA1 + "/changes",
                    changeBody("2026-10-10T06:30:00", "2026-10-10T07:30:00", "设备检修改点", "foreman", "REQ-2"));
            Long changeId2 = change2.path("data").path("id").asLong();
            assertSuccess(post("/api/aqua/tide/schedules/changes/" + changeId2 + "/approve",
                    approveBody("APPROVED", "dispatcher", "同意改期")), "批准修改");
            TideSchedule afterApprove = scheduleMapper.selectById(schA1);
            check(TideSchedule.STATUS_RESCHEDULED.equals(afterApprove.getScheduleStatus()),
                    "批准后排班应为已顺延改期状态");
            check(stamp(afterApprove.getScheduledStart()).equals("2026-10-10T06:30")
                            && stamp(afterApprove.getScheduledEnd()).equals("2026-10-10T07:30"),
                    "批准后应按申请时段执行");
            check(stamp(afterApprove.getOriginalStart()).equals("2026-10-10T06:00")
                            && stamp(afterApprove.getOriginalEnd()).equals("2026-10-10T08:00"),
                    "审批改期不得改变原计划，实际原计划=["
                            + afterApprove.getOriginalStart() + "," + afterApprove.getOriginalEnd() + ")");

            // 自动顺延：向后直接生效，原计划不变
            assertSuccess(post("/api/aqua/tide/schedules/" + schB1 + "/postpone",
                    postponeBody("2026-10-10T08:30:00", "2026-10-10T09:00:00", "临时等水", "scheduler-b")),
                    "自动顺延半小时");
            TideSchedule afterPostpone = scheduleMapper.selectById(schB1);
            check(stamp(afterPostpone.getScheduledStart()).equals("2026-10-10T08:30"),
                    "顺延后执行时间应更新");
            check(stamp(afterPostpone.getOriginalStart()).equals("2026-10-10T08:00")
                            && stamp(afterPostpone.getOriginalEnd()).equals("2026-10-10T09:00"),
                    "顺延不得改变原计划");

            // 顺延不能提前
            JsonNode backward = post("/api/aqua/tide/schedules/" + schB1 + "/postpone",
                    postponeBody("2026-10-10T08:00:00", "2026-10-10T08:30:00", "想提前", "scheduler-b"));
            check(!backward.path("success").asBoolean(true)
                            && backward.path("message").asText().contains("顺延只能向后"),
                    "顺延只能向后，提前必须拦截");

            // 顺延撞设备：乙池顺到 [08:30,09:00) 已占，再顺与甲池改期[06:30,07:30)不撞；构造与丙池14点段无关，
            // 用乙池午后窗口[14,16)排1号泵会撞甲池[13,16)，直接准备即冲突——顺延冲突用甲池改期窗口已覆盖，
            // 此处校验乙池不能顺到与甲池改期重叠：乙池窗口是[08,09)，顺延不得越窗，改为同设备相接验证已在CP5。
        });

        checkpoint("CP7", "临时关闭窗口：通知受影响班组、回执留痕、排班取消，requestId 幂等", () -> {
            // 关闭午后潮窗口（index2，排班 schA2）；早潮窗口保持已排班供 CP8 冻结核对
            Long w2Id = windowIdAt(pondA, 2);
            JsonNode close = post("/api/aqua/tide/windows/" + w2Id + "/close",
                    closeBody("突降雷暴", "duty-manager", "CLOSE-1"));
            assertSuccess(close, "关闭窗口");
            check(close.path("data").path("notices").size() == 1,
                    "应向作业班组发送1条通知，实际=" + close.path("data").path("notices").size());
            noticeId = close.path("data").path("notices").get(0).path("id").asLong();
            check(noticeMapper.selectById(noticeId).getCrewId().equals(crew1),
                    "通知应发给甲班");

            TideWindow closed = windowMapper.selectById(w2Id);
            check(TideWindow.STATUS_CLOSED.equals(closed.getWindowStatus())
                    && Boolean.TRUE.equals(closed.getFrozenFlag()), "关闭后窗口应冻结");
            check(TideSchedule.STATUS_CANCELLED.equals(scheduleMapper.selectById(schA2).getScheduleStatus()),
                    "关闭窗口应联动取消其排班，实际="
                            + scheduleMapper.selectById(schA2).getScheduleStatus());

            // 班组回执
            assertSuccess(post("/api/aqua/tide/notices/" + noticeId + "/ack",
                    ackBody("甲班班长", "已知悉，人员已撤")), "班组回执");
            TideWindowNotice acked = noticeMapper.selectById(noticeId);
            check(TideWindowNotice.STATUS_ACKED.equals(acked.getNoticeStatus())
                    && "甲班班长".equals(acked.getAckBy()) && acked.getAckTime() != null,
                    "回执应记录回执人与时间");
            JsonNode ackAgain = post("/api/aqua/tide/notices/" + noticeId + "/ack",
                    ackBody("甲班班长", "再次回执"));
            check("已知悉，人员已撤".equals(
                    noticeMapper.selectById(noticeId).getAckRemark()), "重复回执不能覆盖首次回执");
            check(ackAgain.path("data").path("ackBy").asText().equals("甲班班长"), "重复回执幂等返回");

            // 重复关闭（新 requestId）拒绝；同 requestId 幂等返回首次
            JsonNode closeAgain = post("/api/aqua/tide/windows/" + w2Id + "/close",
                    closeBody("又关一次", "duty-manager", "CLOSE-2"));
            check(!closeAgain.path("success").asBoolean(true), "同窗口不能重复关闭");
            JsonNode closeDup = post("/api/aqua/tide/windows/" + w2Id + "/close",
                    closeBody("突降雷暴", "duty-manager", "CLOSE-1"));
            check(closeDup.path("data").path("closure").path("id").asLong() > 0,
                    "关闭请求requestId应幂等返回首次记录");
        });

        checkpoint("CP8", "重算冻结：已排班/已关闭窗口保留不重建，候选/可执行窗口按新潮表同口径重建", () -> {
            List<Long> beforeIds = new ArrayList<>();
            for (TideWindow w : windowsOf(pondA)) {
                beforeIds.add(w.getId());
            }
            JsonNode reGen = post("/api/aqua/tide/windows/generate",
                    generateBody(pondA, "2026-10-08", "2026-10-11"));
            assertSuccess(reGen, "甲池重算");
            check(reGen.path("data").path("frozenKeptCount").asInt() == 2,
                    "早潮已排班+午后潮已关闭应冻结保留2个，实际="
                            + reGen.path("data").path("frozenKeptCount").asInt());
            TideWindow scheduled = windowMapper.selectById(windowIdAt(pondA, 1));
            TideWindow closed = windowMapper.selectById(windowIdAt(pondA, 2));
            check(scheduled != null && TideWindow.STATUS_SCHEDULED.equals(scheduled.getWindowStatus())
                            && beforeIds.contains(scheduled.getId()),
                    "已排班窗口必须按原记录冻结保留");
            check(closed != null && TideWindow.STATUS_CLOSED.equals(closed.getWindowStatus())
                            && beforeIds.contains(closed.getId()),
                    "已关闭窗口必须按原记录冻结保留");
            List<TideWindow> now = windowsOf(pondA);
            check(now.size() == 3, "冻结2个+跨零点窗口重建1个，总数应为3，实际=" + now.size());
            check("2026-10-08".equals(now.get(0).getPlanDate().toString())
                            && "2026-10-08T23:00".equals(stamp(now.get(0).getWindowStart())),
                    "重建窗口仍按统一半开口径归属");
        });

        checkpoint("CP9", "全程留证：导入/重算/准备/发布/审批/顺延/关闭/通知/回执/冲突均可追溯", () -> {
            String[] actions = {
                    TideChangeEvidence.ACTION_TIDE_IMPORT,
                    TideChangeEvidence.ACTION_WINDOW_RECALC,
                    TideChangeEvidence.ACTION_SCHEDULE_PREPARE,
                    TideChangeEvidence.ACTION_SCHEDULE_PUBLISH,
                    TideChangeEvidence.ACTION_CHANGE_REQUEST,
                    TideChangeEvidence.ACTION_CHANGE_REJECT,
                    TideChangeEvidence.ACTION_CHANGE_APPROVE,
                    TideChangeEvidence.ACTION_POSTPONE,
                    TideChangeEvidence.ACTION_WINDOW_CLOSE,
                    TideChangeEvidence.ACTION_NOTICE_SEND,
                    TideChangeEvidence.ACTION_NOTICE_ACK,
                    TideChangeEvidence.ACTION_SCHEDULE_CONFLICT
            };
            for (String action : actions) {
                long c = evidenceCount(action, null);
                check(c >= 1, "动作 " + action + " 应至少留1条证据，实际=" + c);
            }
            List<TideChangeEvidence> all = evidenceMapper.selectList(null);
            for (TideChangeEvidence e : all) {
                check(e.getOperator() != null && !e.getOperator().trim().isEmpty(),
                        "证据不能缺操作者，id=" + e.getId());
            }
            JsonNode byRequest = get("/api/aqua/tide/evidence?requestId=REQ-1");
            check(byRequest.path("data").size() >= 1, "按requestId应能追溯修改申请证据");
        });

        printReport();
    }

    // ---------------- helpers ----------------

    private Long createPond(String code, String name, String seaArea) {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", seaArea);
        body.put("waterDepthM", new BigDecimal("2.50"));
        body.put("salinityPpt", new BigDecimal("30.0"));
        JsonNode resp = post("/api/aqua/ponds", body);
        assertSuccess(resp, "建池" + code);
        return resp.path("data").path("id").asLong();
    }

    private Map<String, Object> ruleBody(String area, String name, int before, int after) {
        Map<String, Object> m = new HashMap<>();
        m.put("seaArea", area);
        m.put("ruleName", name);
        m.put("targetPointType", "HIGH");
        m.put("beforeMinutes", before);
        m.put("afterMinutes", after);
        return m;
    }

    private Map<String, Object> crewBody(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("crewCode", code);
        m.put("crewName", name);
        m.put("leader", name + "长");
        return m;
    }

    private Map<String, Object> shiftBody(Long crewId, String start, String end) {
        Map<String, Object> m = new HashMap<>();
        m.put("crewId", crewId);
        m.put("shiftStart", start);
        m.put("shiftEnd", end);
        return m;
    }

    private Map<String, Object> banBody(String area, String start, String end, String type, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("seaArea", area);
        m.put("banStart", start);
        m.put("banEnd", end);
        m.put("weatherType", type);
        m.put("reason", reason);
        m.put("operator", "weather-officer");
        return m;
    }

    private Map<String, Object> equipmentBody(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("equipmentCode", code);
        m.put("equipmentName", name);
        return m;
    }

    private Map<String, Object> point(int seq, String type, String time, Integer height) {
        Map<String, Object> m = new HashMap<>();
        m.put("pointSeq", seq);
        m.put("pointType", type);
        m.put("pointTime", time);
        m.put("heightCm", height);
        return m;
    }

    @SafeVarargs
    private final Map<String, Object> tideBody(String no, String area, String date, Map<String, Object>... points) {
        Map<String, Object> m = new HashMap<>();
        m.put("tideTableNo", no);
        m.put("seaArea", area);
        m.put("tideDate", date);
        m.put("source", "验收模拟潮表");
        m.put("points", list(points));
        m.put("operator", "tide-editor");
        return m;
    }

    private Map<String, Object> generateBody(Long pondId, String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("pondId", pondId);
        m.put("startDate", from);
        m.put("endDate", to);
        m.put("minWindowMinutes", 30);
        m.put("operator", "window-planner");
        return m;
    }

    private Map<String, Object> prepareBody(Long windowId, Long equipId, Long crewId,
                                            String start, String end, String operator) {
        Map<String, Object> m = new HashMap<>();
        m.put("windowId", windowId);
        m.put("equipmentId", equipId);
        m.put("crewId", crewId);
        if (start != null) {
            m.put("scheduledStart", start);
        }
        if (end != null) {
            m.put("scheduledEnd", end);
        }
        m.put("operator", operator);
        return m;
    }

    private Long prepareSchedule(Long windowId, Long equipId, Long crewId,
                                 String start, String end, String operator) {
        JsonNode resp = post("/api/aqua/tide/schedules",
                prepareBody(windowId, equipId, crewId, start, end, operator));
        assertSuccess(resp, "准备排班");
        return resp.path("data").path("id").asLong();
    }

    private void publish(Long scheduleId, String by) {
        Map<String, Object> m = new HashMap<>();
        m.put("publishedBy", by);
        assertSuccess(post("/api/aqua/tide/schedules/" + scheduleId + "/publish", m), "发布排班");
    }

    private Map<String, Object> changeBody(String start, String end, String reason, String by, String reqId) {
        Map<String, Object> m = new HashMap<>();
        m.put("requestedStart", start);
        m.put("requestedEnd", end);
        m.put("changeReason", reason);
        m.put("requestedBy", by);
        m.put("requestId", reqId);
        return m;
    }

    private Map<String, Object> approveBody(String status, String approver, String remark) {
        Map<String, Object> m = new HashMap<>();
        m.put("approveStatus", status);
        m.put("approver", approver);
        if (remark != null) {
            m.put("approveRemark", remark);
        }
        return m;
    }

    private Map<String, Object> postponeBody(String start, String end, String reason, String operator) {
        Map<String, Object> m = new HashMap<>();
        m.put("newStart", start);
        m.put("newEnd", end);
        m.put("reason", reason);
        m.put("operator", operator);
        return m;
    }

    private Map<String, Object> closeBody(String reason, String by, String reqId) {
        Map<String, Object> m = new HashMap<>();
        m.put("closeReason", reason);
        m.put("closedBy", by);
        m.put("requestId", reqId);
        return m;
    }

    private Map<String, Object> ackBody(String by, String remark) {
        Map<String, Object> m = new HashMap<>();
        m.put("ackBy", by);
        m.put("ackRemark", remark);
        return m;
    }

    @SafeVarargs
    private final <T> List<T> list(T... values) {
        List<T> l = new ArrayList<>();
        for (T v : values) {
            l.add(v);
        }
        return l;
    }

    private List<TideWindow> windowsOf(Long pondId) {
        return windowMapper.selectList(new QueryWrapper<TideWindow>()
                .eq("pond_id", pondId).orderByAsc("window_start"));
    }

    private Long windowIdAt(Long pondId, int index) {
        return windowsOf(pondId).get(index).getId();
    }

    private String stamp(LocalDateTime t) {
        return t.toString().substring(0, 16);
    }

    private long evidenceCount(String action, Long entityId) {
        QueryWrapper<TideChangeEvidence> wrapper = new QueryWrapper<TideChangeEvidence>().eq("action", action);
        if (entityId != null) {
            wrapper.eq("entity_id", entityId);
        }
        Long c = evidenceMapper.selectCount(wrapper);
        return c == null ? 0L : c;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 潮汐窗口换水排班验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=============================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode post(String url, Object body) {
        try {
            return objectMapper.readTree(call(HttpMethod.POST, url, body).getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode get(String url) {
        try {
            return objectMapper.readTree(call(HttpMethod.GET, url, null).getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
