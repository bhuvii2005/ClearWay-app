package com.cleanroute.api.ai;

import com.cleanroute.api.exception.ApiException;
import com.cleanroute.api.service.AccountDeletedEvent;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Answers one Coach message. Spring AI's ChatClient runs the tool loop (its ToolCallingAdvisor) on
 * top of {@link LlmGateway}, so every model round trip goes through the gateway's budget, retry and
 * circuit breaker, and the ToolCallingManager stops after {@value #MAX_TOOL_CALLS} tool calls.
 * The user's identity goes to the tools only through the ToolContext; citations come from the
 * chunks the knowledge-base tool actually returned.
 */
@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    static final int MAX_TOOL_CALLS = 5;
    static final String TOOL_LIMIT_ANSWER = "Sorry, I couldn't finish looking that up. "
            + "Please try a simpler or more specific question.";
    static final String EMPTY_ANSWER = "Sorry, I couldn't come up with an answer. Please try rephrasing your question.";

    private final LlmGateway gateway;
    private final CoachRateLimiter rateLimiter;
    private final CoachMemory memory;
    private final Object[] tools;
    private final ChatClient chatClient;
    private final String systemTemplate;
    private final int maxInputChars;
    private final Clock clock;

    @Autowired
    public CoachService(LlmGateway gateway, CoachRateLimiter rateLimiter, CoachMemory memory, CoachTools tools,
                        AiProperties properties, ObjectProvider<ObservationRegistry> observations,
                        @Value("classpath:prompts/coach-system.st") Resource systemPrompt) {
        this(gateway, rateLimiter, memory, new Object[] {tools},
                properties.limits().maxInputChars(), observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                read(systemPrompt), Clock.systemUTC());
    }

    CoachService(LlmGateway gateway, CoachRateLimiter rateLimiter, CoachMemory memory, Object[] tools,
                 int maxInputChars, ObservationRegistry observations, String systemTemplate, Clock clock) {
        this.gateway = gateway;
        this.rateLimiter = rateLimiter;
        this.memory = memory;
        this.tools = tools.clone();
        this.chatClient = chatClient(gateway, observations);
        this.systemTemplate = systemTemplate;
        this.maxInputChars = maxInputChars;
        this.clock = clock;
    }

    /** A ChatClient whose tool loop runs on the given model and stops after {@value #MAX_TOOL_CALLS} tool calls. */
    static ChatClient chatClient(ChatModel model, ObservationRegistry observations) {
        ToolCallingManager toolCalling = ToolCallingManager.builder()
                .observationRegistry(observations)
                .maxTotalToolCalls(MAX_TOOL_CALLS)
                .onLimitExceeded(ToolCallLimitBehavior.THROW)
                .build();
        return ChatClient.builder(model, observations, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(toolCalling)).build();
    }

    /**
     * @param timeZone IANA zone from the browser, used for dates and best/worst hours; null means UTC
     */
    public CoachReply chat(UUID userId, UUID conversationId, String message, String timeZone) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("message must not be blank");
        }
        if (text.length() > maxInputChars) {
            throw ApiException.badRequest("message must be at most " + maxInputChars + " characters");
        }
        ZoneId zone = parseZone(timeZone);
        if (!gateway.isConfigured()) {
            throw ApiException.serviceUnavailable("Coach is offline right now: no AI provider is set up.");
        }
        rateLimiter.acquire(userId);

        List<Message> history = memory.get(userId, conversationId);
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(systemPrompt(zone)));
        messages.addAll(history);
        messages.add(new UserMessage(text));

        CoachTurn turn = new CoachTurn(userId, zone);
        String answer;
        List<CoachTurn.Citation> citations;
        try (AiUsageTally ignored = AiUsageTally.open("coach message")) {
            ChatResponse response = ask(messages, turn);
            if (hitToolLimit(response)) {
                log.info("Coach stopped after {} tool calls", MAX_TOOL_CALLS);
                answer = TOOL_LIMIT_ANSWER;
                citations = List.of();
            } else {
                String content = answerText(response);
                answer = content.isBlank() ? EMPTY_ANSWER : content.strip();
                citations = turn.citations();
            }
        }
        // Only the question and the final answer are remembered; tool rounds are not.
        memory.add(userId, conversationId, List.of(new UserMessage(text), new AssistantMessage(answer)));
        return new CoachReply(answer, citations, turn.toolsUsed());
    }

    private ChatResponse ask(List<Message> messages, CoachTurn turn) {
        try {
            return chatClient.prompt()
                    .messages(messages)
                    .tools(tools)
                    .toolContext(Map.of(CoachTurn.KEY, turn))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            ApiException api = find(e, ApiException.class);
            if (api != null) {
                throw api;
            }
            if (find(e, ToolCallLimitExceededException.class) != null) {
                return null; // treated as hitting the limit
            }
            log.warn("Coach message failed: {}", e.getClass().getSimpleName(), e);
            throw ApiException.serviceUnavailable("Coach is offline right now. Please try again later.");
        }
    }

    private static boolean hitToolLimit(ChatResponse response) {
        if (response == null) {
            return true;
        }
        Generation result = response.getResult();
        return result != null && result.getMetadata() != null
                && ToolCallLimitExceededException.FINISH_REASON.equals(result.getMetadata().getFinishReason());
    }

    private static String answerText(ChatResponse response) {
        Generation result = response.getResult();
        String text = result == null || result.getOutput() == null ? null : result.getOutput().getText();
        return text == null ? "" : text;
    }

    String systemPrompt(ZoneId zone) {
        String today = LocalDate.now(clock.withZone(zone)).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH));
        return StTemplateRenderer.builder().build().apply(systemTemplate, Map.of("today", today, "time_zone", zone.getId()));
    }

    @EventListener
    public void onAccountDeleted(AccountDeletedEvent event) {
        memory.forgetUser(event.userId());
        rateLimiter.forget(event.userId());
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone.strip());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("timeZone must be an IANA time zone such as Europe/London");
        }
    }

    private static <T extends Throwable> T find(Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the Coach system prompt", e);
        }
    }

    /** The reply: the answer, the sources actually retrieved, and the tools used (in order). */
    public record CoachReply(String answer, List<CoachTurn.Citation> citations, List<String> toolsUsed) {
    }
}
