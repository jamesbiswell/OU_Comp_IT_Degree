package com.example.service;

import com.example.config.CustomPineconeVectorStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PineconeVectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

class VectorStoreServiceTest {

    @Test
    void testAddDocuments() {
        CustomPineconeVectorStore mockStore = Mockito.mock(CustomPineconeVectorStore.class);
        VectorStoreService service = new VectorStoreService(mockStore);
        List<Document> docs = List.of(new Document("test"));

        service.addDocuments(docs);

        verify(mockStore).add(docs);
        assertEquals(mockStore, service.getVectorStore());
    }
}
