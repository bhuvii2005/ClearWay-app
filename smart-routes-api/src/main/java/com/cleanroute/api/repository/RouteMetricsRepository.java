package com.cleanroute.api.repository;

import com.cleanroute.api.entity.RouteMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RouteMetricsRepository extends JpaRepository<RouteMetrics, UUID> {

    List<RouteMetrics> findByRouteIdAndTimestampAfterOrderByTimestampAsc(UUID routeId, ZonedDateTime after);

    @Query("SELECT AVG(m.pollutionScore) FROM RouteMetrics m WHERE m.route.id = :routeId")
    Double findAveragePollutionScoreByRouteId(@Param("routeId") UUID routeId);
}
