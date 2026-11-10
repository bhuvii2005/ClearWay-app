package com.cleanroute.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * One message to the Coach.
 *
 * @param conversationId client-generated id; history is kept per signed-in user and this id
 * @param message        the question, 1-2,000 characters
 * @param timeZone       optional IANA time zone of the user (for dates and best hours); default UTC
 */
public record CoachChatRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 64) String timeZone) {
}
