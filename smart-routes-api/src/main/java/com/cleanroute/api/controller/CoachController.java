package com.cleanroute.api.controller;

import com.cleanroute.api.ai.CoachService;
import com.cleanroute.api.dto.CoachChatRequest;
import com.cleanroute.api.dto.CoachChatResponse;
import com.cleanroute.api.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClearWay Coach: answers questions about the caller's routes and the air, using read-only tools
 * and the knowledge base. Requires a Bearer token. 400 for invalid input, 429 (with Retry-After)
 * over the hourly limit, 503 when the AI provider is unavailable.
 */
@RestController
@RequestMapping("/api/v1/coach")
public class CoachController {

    private final CoachService coach;

    public CoachController(CoachService coach) {
        this.coach = coach;
    }

    @PostMapping("/chat")
    public CoachChatResponse chat(@Valid @RequestBody CoachChatRequest request) {
        CoachService.CoachReply reply = coach.chat(CurrentUser.id(), request.conversationId(), request.message(),
                request.timeZone());
        return new CoachChatResponse(reply.answer(),
                reply.citations().stream().map(c -> new CoachChatResponse.Citation(c.title(), c.url())).toList(),
                reply.toolsUsed());
    }
}
