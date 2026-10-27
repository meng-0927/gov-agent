package com.example.govagent.conversation;

import com.example.govagent.model.AgentRunRecord;
import com.example.govagent.model.ConversationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理服务，统一处理短期上下文和持久化历史。
 * Demo 用内存实现；真实项目可分别替换为 Redis（热点会话）和 MySQL（完整历史/执行记录）。
 */
@Service
public class ConversationService {
    private final HotConversationStore hotStore;
    private final ConversationRepository repository;
    private final int recentMessageLimit;

    public ConversationService(HotConversationStore hotStore,
                               ConversationRepository repository,
                               @Value("${agent.conversation.recent-message-limit:8}") int recentMessageLimit) {
        this.hotStore = hotStore;
        this.repository = repository;
        this.recentMessageLimit = recentMessageLimit;
    }

    public List<ConversationMessage> loadRecent(String sessionId) {
        // 先读热点存储，避免每轮对话都访问持久化数据库。
        List<ConversationMessage> cached = hotStore.getRecent(sessionId);
        if (!cached.isEmpty()) {
            return cached;
        }

        // 缓存未命中时回源持久化层，只截取最近 N 条注入 Prompt，控制上下文长度。
        List<ConversationMessage> all = repository.findMessages(sessionId);
        List<ConversationMessage> recent = tail(all, recentMessageLimit);
        if (!recent.isEmpty()) {
            hotStore.putRecent(sessionId, recent);
        }
        return recent;
    }

    public void append(String sessionId, ConversationMessage message) {
        // 消息先落持久化层，再同步维护最近会话缓存。
        repository.appendMessage(sessionId, message);

        List<ConversationMessage> current = new ArrayList<>(hotStore.getRecent(sessionId));
        if (current.isEmpty()) {
            current.addAll(tail(repository.findMessages(sessionId), recentMessageLimit));
        } else {
            current.add(message);
            current = new ArrayList<>(tail(current, recentMessageLimit));
        }
        hotStore.putRecent(sessionId, current);
    }

    public void saveRun(AgentRunRecord record) {
        repository.saveRun(record);
    }

    private List<ConversationMessage> tail(List<ConversationMessage> input, int limit) {
        if (input.size() <= limit) {
            return List.copyOf(input);
        }
        return List.copyOf(input.subList(input.size() - limit, input.size()));
    }
}
