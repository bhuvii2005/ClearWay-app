package com.cleanroute.api.controller;

import com.cleanroute.api.dto.NotificationDto;
import com.cleanroute.api.service.NotificationService;
import com.cleanroute.api.util.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The caller's stored alerts. Live alerts arrive over WebSocket at /user/queue/alerts. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestParam(defaultValue = "30") int limit) {
        return notificationService.list(CurrentUser.id(), limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationService.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(CurrentUser.id()));
    }
}
