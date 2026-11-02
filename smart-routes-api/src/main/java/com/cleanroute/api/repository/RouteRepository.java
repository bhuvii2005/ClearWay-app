package com.cleanroute.api.repository;

import com.cleanroute.api.entity.Route;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID> {

    List<Route> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Route> findByIdAndUserId(UUID id, UUID userId);

    /** Ids of monitored routes, read page by page by the scheduler. */
    @Query("SELECT r.id FROM Route r WHERE r.isActive = true")
    Slice<UUID> findActiveRouteIds(Pageable pageable);
}
