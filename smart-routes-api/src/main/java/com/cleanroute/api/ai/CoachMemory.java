package com.cleanroute.api.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coach conversation memory: Spring AI's MessageWindowChatMemory (last 10 messages) kept in
 * memory only, so history is lost on restart (accepted for v1). Keys are
 * {@code userId + ":" + conversationId}, with the user id from the JWT, so a client-chosen
 * conversation id can never reach another user's history. The store is a bounded cache that
 * forgets idle conversations, so memory cannot grow without limit.
 */
@Component
public class CoachMemory {

    static final int MAX_MESSAGES = 10;
    private static final Duration IDLE_EXPIRY = Duration.ofHours(24);
    private static final long MAX_CONVERSATIONS = 10_000;

    private final Cache<String, List<Message>> store = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAX_CONVERSATIONS)
            .build();

    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new CacheRepository())
            .maxMessages(MAX_MESSAGES)
            .build();

    static String key(UUID userId, UUID conversationId) {
        return userId + ":" + conversationId;
    }

    public List<Message> get(UUID userId, UUID conversationId) {
        return memory.get(key(userId, conversationId));
    }

    public void add(UUID userId, UUID conversationId, List<Message> messages) {
        memory.add(key(userId, conversationId), messages);
    }

    /** Forgets every conversation of this user, for example when the account is deleted. */
    public void forgetUser(UUID userId) {
        String prefix = userId + ":";
        store.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    long conversations() {
        store.cleanUp();
        return store.estimatedSize();
    }

    private final class CacheRepository implements ChatMemoryRepository {

        @Override
        public List<String> findConversationIds() {
            return new ArrayList<>(store.asMap().keySet());
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            List<Message> messages = store.getIfPresent(conversationId);
            return messages == null ? List.of() : List.copyOf(messages);
        }

        @Override
        public void saveAll(String conversationId, List<Message> messages) {
            store.put(conversationId, List.copyOf(messages));
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            store.invalidate(conversationId);
        }
    }
}
