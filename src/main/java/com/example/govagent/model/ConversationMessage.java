package com.example.govagent.model;

import java.time.Instant;

public record ConversationMessage(
        ConversationRole role,
        String content,
        Instant createdAt
) {
    public static ConversationMessage user(String content) {
        return new ConversationMessage(ConversationRole.USER, content, Instant.now());
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(ConversationRole.ASSISTANT, content, Instant.now());
    }
}
