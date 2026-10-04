package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 禁作天气：按海区登记禁作时段，半开 [banStart, banEnd)。
 */
@TableName("t_tide_weather_ban")
public class TideWeatherBan extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private String seaArea;
    private LocalDateTime banStart;
    private LocalDateTime banEnd;
    private String weatherType;
    private String reason;
    private String status;

    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDateTime getBanStart() { return banStart; }
    public void setBanStart(LocalDateTime banStart) { this.banStart = banStart; }
    public LocalDateTime getBanEnd() { return banEnd; }
    public void setBanEnd(LocalDateTime banEnd) { this.banEnd = banEnd; }
    public String getWeatherType() { return weatherType; }
    public void setWeatherType(String weatherType) { this.weatherType = weatherType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
