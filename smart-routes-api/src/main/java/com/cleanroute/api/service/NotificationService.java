package com.cleanroute.api.service;

import com.cleanroute.api.dto.NotificationDto;
import com.cleanroute.api.entity.Notification;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.repository.NotificationRepository;
import com.cleanroute.api.repository.RouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores alerts and delivers them to the owning user over WebSocket (/user/queue/alerts).
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    public static final String ALERT_QUEUE = "/queue/alerts";

    private final NotificationRepository notificationRepository;
    private final RouteRepository routeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               RouteRepository routeRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.routeRepository = routeRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public NotificationDto send(Route route, Notification.Kind kind, String message, Double score) {
        Notification n = new Notification();
        n.setUserId(route.getUser().getId());
        n.setRouteId(route.getId());
        n.setKind(kind);
        n.setMessage(message);
        n.setPollutionScore(score);
        n = notificationRepository.save(n);

        NotificationDto dto = NotificationDto.from(n, route.getName());
        try {
            messagingTemplate.convertAndSendToUser(n.getUserId().toString(), ALERT_QUEUE, dto);
        } catch (RuntimeException e) {
            // The alert is stored either way; the user sees it on next load.
            logger.warn("Live delivery of notification {} failed: {}", n.getId(), e.getMessage());
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID userId, int limit) {
        List<Notification> items = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        Map<UUID, String> routeNames = routeRepository.findAllById(items.stream()
                        .map(Notification::getRouteId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Route::getId, Route::getName, (a, b) -> a));
        return items.stream()
                .map(n -> NotificationDto.from(n, routeNames.get(n.getRouteId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.setRead(true);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }
}
