package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.CrewRequest;
import com.evops.aqua.tide.dto.CrewShiftRequest;
import com.evops.aqua.tide.dto.EquipmentRequest;
import com.evops.aqua.tide.dto.TideTableImportRequest;
import com.evops.aqua.tide.dto.TideWindowRuleRequest;
import com.evops.aqua.tide.dto.WeatherBanRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideCrew;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideEquipment;
import com.evops.aqua.tide.entity.TidePoint;
import com.evops.aqua.tide.entity.TideTable;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.entity.TideWindowRule;
import com.evops.aqua.tide.mapper.TideCrewMapper;
import com.evops.aqua.tide.mapper.TideCrewShiftMapper;
import com.evops.aqua.tide.mapper.TideEquipmentMapper;
import com.evops.aqua.tide.mapper.TidePointMapper;
import com.evops.aqua.tide.mapper.TideTableMapper;
import com.evops.aqua.tide.mapper.TideWeatherBanMapper;
import com.evops.aqua.tide.mapper.TideWindowRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 潮汐排班基础数据：海区潮汐表与潮点、候选展开规则、禁作天气、班组班次、换水设备。
 * 潮汐表按海区+日期重复导入即覆盖（先删后插，同事务留证）——
 * 把潮时调到临界时间后重算窗口，归属仍走同一半开口径。
 */
@Service
public class TideMasterDataService {

    private final TideTableMapper tideTableMapper;
    private final TidePointMapper tidePointMapper;
    private final TideWindowRuleMapper ruleMapper;
    private final TideWeatherBanMapper weatherBanMapper;
    private final TideCrewMapper crewMapper;
    private final TideCrewShiftMapper shiftMapper;
    private final TideEquipmentMapper equipmentMapper;
    private final TideEvidenceService evidenceService;

    public TideMasterDataService(TideTableMapper tideTableMapper,
                                 TidePointMapper tidePointMapper,
                                 TideWindowRuleMapper ruleMapper,
                                 TideWeatherBanMapper weatherBanMapper,
                                 TideCrewMapper crewMapper,
                                 TideCrewShiftMapper shiftMapper,
                                 TideEquipmentMapper equipmentMapper,
                                 TideEvidenceService evidenceService) {
        this.tideTableMapper = tideTableMapper;
        this.tidePointMapper = tidePointMapper;
        this.ruleMapper = ruleMapper;
        this.weatherBanMapper = weatherBanMapper;
        this.crewMapper = crewMapper;
        this.shiftMapper = shiftMapper;
        this.equipmentMapper = equipmentMapper;
        this.evidenceService = evidenceService;
    }

    // ---- 潮汐表 ----

    /** 导入（重复导入同海区同日为覆盖：删除旧潮点与表头后重建，同事务留证）。 */
    @Transactional(rollbackFor = Exception.class)
    public TideTable importTideTable(TideTableImportRequest request) {
        TideTable existed = tideTableMapper.selectOne(new QueryWrapper<TideTable>()
                .eq("sea_area", request.getSeaArea())
                .eq("tide_date", request.getTideDate()));
        TideTable before = existed == null ? null : copyTable(existed);
        if (existed != null) {
            tidePointMapper.delete(new QueryWrapper<TidePoint>().eq("tide_table_id", existed.getId()));
            tideTableMapper.deleteById(existed.getId());
        }
        TideTable table = new TideTable();
        table.setTideTableNo(request.getTideTableNo());
        table.setSeaArea(request.getSeaArea());
        table.setTideDate(request.getTideDate());
        table.setTideType(request.getTideType());
        table.setSource(request.getSource());
        table.setStatus(TideTable.STATUS_ACTIVE);
        table.setRemark(request.getRemark());
        tideTableMapper.insert(table);

        List<Integer> seqs = new ArrayList<>();
        for (TideTableImportRequest.PointInput input : request.getPoints()) {
            String type = input.getPointType() == null ? "" : input.getPointType().trim().toUpperCase();
            if (!TidePoint.TYPE_HIGH.equals(type) && !TidePoint.TYPE_LOW.equals(type)) {
                throw new IllegalArgumentException("潮点类型只能是 HIGH/LOW，实际=" + input.getPointType());
            }
            if (!input.getPointTime().toLocalDate().equals(request.getTideDate())) {
                throw new IllegalArgumentException("潮点时刻必须落在潮汐日期当天：" + input.getPointTime());
            }
            if (seqs.contains(input.getPointSeq())) {
                throw new IllegalArgumentException("潮点序号不能重复：" + input.getPointSeq());
            }
            seqs.add(input.getPointSeq());
            TidePoint point = new TidePoint();
            point.setTideTableId(table.getId());
            point.setPointSeq(input.getPointSeq());
            point.setPointType(type);
            point.setPointTime(input.getPointTime());
            point.setHeightCm(input.getHeightCm());
            tidePointMapper.insert(point);
        }
        evidenceService.record(TideChangeEvidence.ENTITY_TIDE_TABLE, table.getId(),
                TideChangeEvidence.ACTION_TIDE_IMPORT, table.getTideTableNo(),
                request.getOperator(), before, table,
                (existed == null ? "导入" : "覆盖导入") + "潮汐表：海区=" + table.getSeaArea()
                        + "，日期=" + table.getTideDate() + "，潮点数=" + request.getPoints().size());
        return table;
    }

    public TideTable getTideTable(String seaArea, LocalDate date) {
        return tideTableMapper.selectOne(new QueryWrapper<TideTable>()
                .eq("sea_area", seaArea).eq("tide_date", date));
    }

    public List<TidePoint> listPoints(Long tideTableId) {
        return tidePointMapper.selectList(new QueryWrapper<TidePoint>()
                .eq("tide_table_id", tideTableId).orderByAsc("point_seq"));
    }

    // ---- 候选展开规则 ----

    @Transactional(rollbackFor = Exception.class)
    public TideWindowRule saveRule(TideWindowRuleRequest request) {
        if (request.getBeforeMinutes() < 0 || request.getAfterMinutes() <= 0) {
            throw new IllegalArgumentException("潮前偏移须>=0、潮后偏移须>0");
        }
        TideWindowRule rule = ruleMapper.selectOne(
                new QueryWrapper<TideWindowRule>().eq("sea_area", request.getSeaArea()));
        TideWindowRule before = rule == null ? null : copyRule(rule);
        if (rule == null) {
            rule = new TideWindowRule();
            rule.setSeaArea(request.getSeaArea());
        }
        rule.setRuleName(request.getRuleName());
        String type = request.getTargetPointType() == null ? TidePoint.TYPE_HIGH
                : request.getTargetPointType().trim().toUpperCase();
        if (!TidePoint.TYPE_HIGH.equals(type) && !TidePoint.TYPE_LOW.equals(type)) {
            throw new IllegalArgumentException("候选目标潮点类型只能是 HIGH/LOW");
        }
        rule.setTargetPointType(type);
        rule.setBeforeMinutes(request.getBeforeMinutes());
        rule.setAfterMinutes(request.getAfterMinutes());
        rule.setStatus(TideWindowRule.STATUS_ACTIVE);
        rule.setRemark(request.getRemark());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        evidenceService.record(TideChangeEvidence.ENTITY_RULE, rule.getId(),
                before == null ? "RULE_CREATE" : "RULE_UPDATE", rule.getSeaArea(),
                request.getOperator(), before, rule,
                "换水候选展开规则：海区=" + rule.getSeaArea()
                        + "，目标潮点=" + rule.getTargetPointType()
                        + "，[" + rule.getBeforeMinutes() + "," + rule.getAfterMinutes() + ")分钟");
        return rule;
    }

    public TideWindowRule getRule(String seaArea) {
        return ruleMapper.selectOne(new QueryWrapper<TideWindowRule>()
                .eq("sea_area", seaArea).eq("status", TideWindowRule.STATUS_ACTIVE));
    }

    // ---- 禁作天气 ----

    @Transactional(rollbackFor = Exception.class)
    public TideWeatherBan createWeatherBan(WeatherBanRequest request) {
        if (!request.getBanEnd().isAfter(request.getBanStart())) {
            throw new IllegalArgumentException("禁作结束必须严格晚于开始（半开区间）");
        }
        TideWeatherBan ban = new TideWeatherBan();
        ban.setSeaArea(request.getSeaArea());
        ban.setBanStart(request.getBanStart());
        ban.setBanEnd(request.getBanEnd());
        ban.setWeatherType(request.getWeatherType());
        ban.setReason(request.getReason());
        ban.setStatus(TideWeatherBan.STATUS_ACTIVE);
        weatherBanMapper.insert(ban);
        evidenceService.record(TideChangeEvidence.ENTITY_WEATHER, ban.getId(),
                "WEATHER_BAN_ADD", null, request.getOperator(), null, ban,
                "登记禁作天气：海区=" + ban.getSeaArea() + "，类型=" + ban.getWeatherType()
                        + "，时段=" + ban.getBanStart() + "~" + ban.getBanEnd());
        return ban;
    }

    public List<TideWeatherBan> listWeatherBans(String seaArea, LocalDate from, LocalDate to) {
        QueryWrapper<TideWeatherBan> wrapper = new QueryWrapper<>();
        wrapper.eq("sea_area", seaArea).eq("status", TideWeatherBan.STATUS_ACTIVE);
        if (from != null) {
            wrapper.ge("ban_end", from.atStartOfDay());
        }
        if (to != null) {
            wrapper.lt("ban_start", to.plusDays(1).atStartOfDay());
        }
        wrapper.orderByAsc("ban_start");
        return weatherBanMapper.selectList(wrapper);
    }

    // ---- 班组 / 班次 ----

    @Transactional(rollbackFor = Exception.class)
    public TideCrew createCrew(CrewRequest request) {
        Long exists = crewMapper.selectCount(
                new QueryWrapper<TideCrew>().eq("crew_code", request.getCrewCode()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("班组编号已存在：" + request.getCrewCode());
        }
        TideCrew crew = new TideCrew();
        crew.setCrewCode(request.getCrewCode());
        crew.setCrewName(request.getCrewName());
        crew.setLeader(request.getLeader());
        crew.setStatus(TideCrew.STATUS_ACTIVE);
        crew.setRemark(request.getRemark());
        crewMapper.insert(crew);
        return crew;
    }

    public TideCrew getCrewOrThrow(Long id) {
        TideCrew crew = crewMapper.selectById(id);
        if (crew == null) {
            throw new IllegalArgumentException("班组不存在，id=" + id);
        }
        return crew;
    }

    public List<TideCrew> listCrews() {
        return crewMapper.selectList(new QueryWrapper<TideCrew>().orderByAsc("id"));
    }

    @Transactional(rollbackFor = Exception.class)
    public TideCrewShift createShift(CrewShiftRequest request) {
        getCrewOrThrow(request.getCrewId());
        if (!request.getShiftEnd().isAfter(request.getShiftStart())) {
            throw new IllegalArgumentException("班次结束必须严格晚于开始（半开区间）");
        }
        TideCrewShift shift = new TideCrewShift();
        shift.setCrewId(request.getCrewId());
        shift.setShiftStart(request.getShiftStart());
        shift.setShiftEnd(request.getShiftEnd());
        shift.setShiftType(request.getShiftType() == null ? TideCrewShift.TYPE_NORMAL : request.getShiftType());
        shift.setRemark(request.getRemark());
        shiftMapper.insert(shift);
        return shift;
    }

    public List<TideCrewShift> listShifts(Long crewId) {
        return shiftMapper.selectList(new QueryWrapper<TideCrewShift>()
                .eq(crewId != null, "crew_id", crewId).orderByAsc("shift_start"));
    }

    // ---- 设备 ----

    @Transactional(rollbackFor = Exception.class)
    public TideEquipment createEquipment(EquipmentRequest request) {
        Long exists = equipmentMapper.selectCount(
                new QueryWrapper<TideEquipment>().eq("equipment_code", request.getEquipmentCode()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("换水设备编号已存在：" + request.getEquipmentCode());
        }
        TideEquipment equipment = new TideEquipment();
        equipment.setEquipmentCode(request.getEquipmentCode());
        equipment.setEquipmentName(request.getEquipmentName());
        equipment.setSeaArea(request.getSeaArea());
        equipment.setStatus(TideEquipment.STATUS_ACTIVE);
        equipment.setRemark(request.getRemark());
        equipmentMapper.insert(equipment);
        return equipment;
    }

    public TideEquipment getEquipmentOrThrow(Long id) {
        TideEquipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new IllegalArgumentException("换水设备不存在，id=" + id);
        }
        if (!TideEquipment.STATUS_ACTIVE.equals(equipment.getStatus())) {
            throw new IllegalArgumentException("换水设备已停用：" + equipment.getEquipmentCode());
        }
        return equipment;
    }

    public List<TideEquipment> listEquipment() {
        return equipmentMapper.selectList(new QueryWrapper<TideEquipment>().orderByAsc("id"));
    }

    private TideTable copyTable(TideTable s) {
        TideTable c = new TideTable();
        c.setId(s.getId());
        c.setTideTableNo(s.getTideTableNo());
        c.setSeaArea(s.getSeaArea());
        c.setTideDate(s.getTideDate());
        c.setTideType(s.getTideType());
        c.setSource(s.getSource());
        c.setStatus(s.getStatus());
        c.setRemark(s.getRemark());
        return c;
    }

    private TideWindowRule copyRule(TideWindowRule s) {
        TideWindowRule c = new TideWindowRule();
        c.setId(s.getId());
        c.setSeaArea(s.getSeaArea());
        c.setRuleName(s.getRuleName());
        c.setTargetPointType(s.getTargetPointType());
        c.setBeforeMinutes(s.getBeforeMinutes());
        c.setAfterMinutes(s.getAfterMinutes());
        c.setStatus(s.getStatus());
        c.setRemark(s.getRemark());
        return c;
    }
}
