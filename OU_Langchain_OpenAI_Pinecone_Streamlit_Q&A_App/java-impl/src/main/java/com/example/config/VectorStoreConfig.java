package com.example.config;

import com.example.util.DebugLogger;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.pinecone.apiKey}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.pinecone.environment}")
    private String environment;

    @Value("${spring.ai.vectorstore.pinecone.projectId}")
    private String projectId;

    @Value("${spring.ai.vectorstore.pinecone.indexName}")
    private String indexName;

    @Value("${spring.ai.vectorstore.pinecone.namespace:}")
    private String namespace;

    @Value("${spring.ai.vectorstore.pinecone.contentFieldName:text}")
    private String contentFieldName;

    private final DebugLogger debugLogger;

    public VectorStoreConfig(DebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    @Bean
    @Primary
    public CustomPineconeVectorStore vectorStore(EmbeddingModel embeddingModel) {
        debugLogger.log("[DEBUG_LOG] Creating CustomPineconeVectorStore bean");
        PineconeVectorStore.PineconeVectorStoreConfig config = PineconeVectorStore.PineconeVectorStoreConfig.builder()
                .withApiKey(apiKey)
                .withEnvironment(environment)
                .withProjectId(projectId)
                .withIndexName(indexName)
                .withNamespace(namespace)
                .build();

        CustomPineconeVectorStore store = new CustomPineconeVectorStore(config, embeddingModel, contentFieldName, debugLogger);
        store.checkAndCreateIndex();
        return store;
    }
}
