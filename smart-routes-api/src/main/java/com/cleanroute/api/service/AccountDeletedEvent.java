package com.cleanroute.api.service;

import java.util.UUID;

/** Published after a user deletes their account, so in-memory state about them can be dropped. */
public record AccountDeletedEvent(UUID userId) {
}
