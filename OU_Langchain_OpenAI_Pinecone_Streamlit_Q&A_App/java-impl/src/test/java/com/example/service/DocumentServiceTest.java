package com.example.service;

import com.example.util.DebugLogger;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceTest {

    private final DebugLogger debugLogger = new DebugLogger();
    private final DocumentService documentService = new DocumentService(debugLogger);

    @Test
    void testLoadAndChunkTxt() throws IOException {
        File tempFile = File.createTempFile("test", ".txt");
        Files.writeString(tempFile.toPath(), "This is a test document with some content to be chunked. ".repeat(20));
        
        List<Document> result = documentService.loadAndChunk(tempFile);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getContent().contains("test document"));
        
        tempFile.delete();
    }

    @Test
    void testUnsupportedFormat() throws IOException {
        File tempFile = File.createTempFile("test", ".exe");
        
        List<Document> result = documentService.loadAndChunk(tempFile);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        tempFile.delete();
    }

    @Test
    void testGenerateId() {
        Document doc = new Document("initial-id",
                "test content",
                Map.of(
                "source", "test.pdf",
                "page", 1,
                "page_label", "2"
        ));
        
        String id1 = documentService.generateId(doc);
        assertNotNull(id1);
        assertEquals(64, id1.length()); // SHA-256 hex length
        
        // Deterministic check
        String id2 = documentService.generateId(doc);
        assertEquals(id1, id2);
        
        // Default check
        Document docDefaults = new Document("initial-id", "test content", Map.of(
                "source", "test.pdf"
        ));
        String idDefaults = documentService.generateId(docDefaults);
        assertEquals(64, idDefaults.length());
        
        // Verify it matches expected hash for "test.pdf|0|test content"
        // (Manual verification of the logic)
        
        // page_label check
        Document docPageLabel = new Document("id", "content", Map.of("source", "s", "page_label", "5"));
        String idPageLabel = documentService.generateId(docPageLabel);
        
        Document docNoPageLabel = new Document("id", "content", Map.of("source", "s"));
        String idNoPageLabel = documentService.generateId(docNoPageLabel);
        
        assertNotEquals(idPageLabel, idNoPageLabel);
        
        // Variation check
        Document doc2 = new Document("initial-id", "different content", Map.of(
                "source", "test.pdf",
                "page", 1,
                "page_label", "2"
        ));
        String id3 = documentService.generateId(doc2);
        assertNotEquals(id1, id3);
    }
}
