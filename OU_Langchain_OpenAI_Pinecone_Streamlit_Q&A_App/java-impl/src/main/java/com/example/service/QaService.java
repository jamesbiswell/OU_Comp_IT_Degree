package com.example.service;

import com.example.config.CustomPineconeVectorStore;
import com.example.util.DebugLogger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QaService {

    private ChatClient chatClient;
    private final CustomPineconeVectorStore vectorStore;
    private final DebugLogger debugLogger;
    private final ChatClient.Builder builder;

    @Value("${spring.ai.vectorstore.pinecone.topK:10}")
    private int topK;

    public QaService(ChatClient.Builder builder, CustomPineconeVectorStore vectorStore, DebugLogger debugLogger) {
        this.builder = builder;
        this.vectorStore = vectorStore;
        this.debugLogger = debugLogger;
        this.chatClient = builder.build();
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public QaResponse ask(String question) {
        // Manually retrieving documents to have full control over the query sent to the vector store.
        // This prevents instructions from being embedded and ensures the best semantic match.
        debugLogger.log("[DEBUG_LOG] Performing manual similarity search for: " + question);
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.query(question).withTopK(topK));
        
        String context = docs.stream()
            .map(Document::getContent)
            .map(s -> s.replace("{", "(").replace("}", ")").replace("$", "USD"))
            .collect(Collectors.joining("\n\n"));

        String systemPrompt = "You are a helpful assistant for the Open University Q&A system. " +
                "Use the provided context to answer the user's question or fulfill their request. " +
                "Your response must be based ONLY on the provided context. " +
                "If the information required to answer or fulfill the request is not in the context, " +
                "politely say you don't know based on the documents.\n\n" +
                "CONTEXT:\n" + context;

        // Use a more robust way to set the system text to avoid ST parsing issues with special characters in context.
        // We avoid PromptTemplate which is triggered by string-based system() calls.
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        return new QaResponse(answer, docs);
    }
}
