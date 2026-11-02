package com.cleanroute.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exposure history of a saved route over the last {@code days} days.
 *
 * @param hourlyAverages mean score per hour of day (0-23) in the requested time zone
 * @param bestHour       hour of day with the lowest mean score, or null without data
 * @param worstHour      hour of day with the highest mean score, or null without data
 */
public record RouteHistoryDto(
        UUID routeId,
        int days,
        String timeZone,
        List<RouteMetricsDto> points,
        Map<Integer, Double> hourlyAverages,
        Integer bestHour,
        Integer worstHour) {
}
