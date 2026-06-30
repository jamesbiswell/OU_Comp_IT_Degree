package com.example.service;

import com.example.config.CustomPineconeVectorStore;
import com.example.util.DebugLogger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaServiceTest {

    @Test
    void testAsk() {
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        ChatClient mockChatClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        CustomPineconeVectorStore mockVectorStore = mock(CustomPineconeVectorStore.class);
        DebugLogger realDebugLogger = new DebugLogger(false);
        
        when(mockBuilder.build()).thenReturn(mockChatClient);
        
        when(mockChatClient.prompt()
                .system(any(String.class))
                .user(any(String.class))
                .call()
                .content()).thenReturn("Test Answer");

        QaService qaService = new QaService(mockBuilder, mockVectorStore, realDebugLogger);
        qaService.setChatClient(mockChatClient); // Use setter to bypass mock builder complexity
        
        QaResponse response = qaService.ask("Test Question");

        assertEquals("Test Answer", response.getAnswer());
    }
}
