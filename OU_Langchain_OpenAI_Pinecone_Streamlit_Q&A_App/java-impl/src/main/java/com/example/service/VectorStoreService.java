package com.example.service;

import com.example.config.CustomPineconeVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PineconeVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

    private final CustomPineconeVectorStore vectorStore;

    public VectorStoreService(CustomPineconeVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }
    
    public PineconeVectorStore getVectorStore() {
        return vectorStore;
    }
}
