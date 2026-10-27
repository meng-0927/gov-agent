package com.example.govagent;

import com.example.govagent.model.ChatRequest;
import com.example.govagent.model.ChatResponse;
import com.example.govagent.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentFlowTest {
    @Autowired
    private AgentService agentService;

    @Test
    void shouldRunPolicyQaFlow() {
        ChatResponse response = agentService.chat(
                new ChatRequest("u1", "s1", "企业数字化改造项目申报需要什么条件？"));

        assertThat(response.answer()).isNotBlank();
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.trace()).isNotEmpty();
    }

    @Test
    void shouldRunDocumentGenerationFlow() {
        ChatResponse response = agentService.chat(
                new ChatRequest("u1", "s2", "根据数字化转型政策写一份项目申报通知"));

        assertThat(response.answer()).contains("通知");
        assertThat(response.citations()).isNotEmpty();
    }
}
