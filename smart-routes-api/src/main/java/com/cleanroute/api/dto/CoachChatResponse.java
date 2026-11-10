package com.cleanroute.api.dto;

import java.util.List;

/**
 * The Coach's reply.
 *
 * @param citations sources the knowledge-base search returned for this answer (built by the server)
 * @param toolsUsed names of the tools the Coach called, in order
 */
public record CoachChatResponse(String answer, List<Citation> citations, List<String> toolsUsed) {

    public record Citation(String title, String url) {
    }
}
