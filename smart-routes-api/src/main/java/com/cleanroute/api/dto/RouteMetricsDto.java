package com.cleanroute.api.dto;

import com.cleanroute.api.entity.RouteMetrics;

import java.time.ZonedDateTime;

/** One scoring run of a saved route. */
public record RouteMetricsDto(
        ZonedDateTime timestamp,
        Double pollutionScore,
        Double aqi,
        Double pm25,
        Double pm10,
        Double no2,
        Double ozone) {

    public static RouteMetricsDto from(RouteMetrics m) {
        return new RouteMetricsDto(m.getTimestamp(), m.getPollutionScore(), m.getAqi(),
                m.getPm25(), m.getPm10(), m.getNo2(), m.getOzone());
    }
}
