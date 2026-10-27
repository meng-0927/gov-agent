package com.example.govagent.conversation;

import com.example.govagent.model.ConversationMessage;

import java.util.List;

/** Production mapping: Redis, used for recent active-session context. */
public interface HotConversationStore {
    List<ConversationMessage> getRecent(String sessionId);

    void putRecent(String sessionId, List<ConversationMessage> messages);
}
