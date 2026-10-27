package com.example.govagent.conversation;

import com.example.govagent.model.AgentRunRecord;
import com.example.govagent.model.ConversationMessage;

import java.util.List;

/** Production mapping: MySQL, used for durable conversation/run records. */
public interface ConversationRepository {
    void appendMessage(String sessionId, ConversationMessage message);

    List<ConversationMessage> findMessages(String sessionId);

    void saveRun(AgentRunRecord record);
}
