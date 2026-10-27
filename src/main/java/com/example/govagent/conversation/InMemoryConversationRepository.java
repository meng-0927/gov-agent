package com.example.govagent.conversation;

import com.example.govagent.model.AgentRunRecord;
import com.example.govagent.model.ConversationMessage;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConversationRepository 的内存版 Demo，实现上对应真实项目中的 MySQL 持久化层。
 */
@Repository
public class InMemoryConversationRepository implements ConversationRepository {
    private final Map<String, List<ConversationMessage>> messages = new ConcurrentHashMap<>();
    private final List<AgentRunRecord> runs = new CopyOnWriteArrayList<>();

    @Override
    public void appendMessage(String sessionId, ConversationMessage message) {
        messages.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public List<ConversationMessage> findMessages(String sessionId) {
        return new ArrayList<>(messages.getOrDefault(sessionId, List.of()));
    }

    @Override
    public void saveRun(AgentRunRecord record) {
        runs.add(record);
    }
}
