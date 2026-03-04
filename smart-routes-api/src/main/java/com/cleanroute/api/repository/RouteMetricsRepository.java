package com.cleanroute.api.repository;

import com.cleanroute.api.entity.RouteMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RouteMetricsRepository extends JpaRepository<RouteMetrics, UUID> {
    List<RouteMetrics> findByRouteIdOrderByTimestampDesc(UUID routeId);
}
