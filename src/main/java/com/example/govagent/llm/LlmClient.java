package com.example.govagent.llm;

public interface LlmClient {
    /** Returns JSON only. */
    String createPlan(String userMessage, String plannerPrompt);

    /** Used by generation-oriented tools. */
    String generateText(String prompt);
}
