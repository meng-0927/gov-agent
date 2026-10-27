package com.example.govagent.conversation;

import com.example.govagent.model.ConversationMessage;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HotConversationStore 的内存版 Demo，实现上对应真实项目中的 Redis 热点会话缓存。
 */
@Repository
public class InMemoryHotConversationStore implements HotConversationStore {
    private final Map<String, List<ConversationMessage>> cache = new ConcurrentHashMap<>();

    @Override
    public List<ConversationMessage> getRecent(String sessionId) {
        return cache.getOrDefault(sessionId, List.of());
    }

    @Override
    public void putRecent(String sessionId, List<ConversationMessage> messages) {
        cache.put(sessionId, List.copyOf(messages));
    }
}
