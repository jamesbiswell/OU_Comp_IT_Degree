package com.example.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    public List<Document> loadAndChunk(File file) {
        List<Document> documents;
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                    new FileSystemResource(file),
                    PdfDocumentReaderConfig.builder()
                            .withPageTopMargin(0)
                            .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                    .withNumberOfTopTextLinesToDelete(0)
                                    .build())
                            .withPagesPerDocument(1)
                            .build());
            documents = pdfReader.get();
        } else if (fileName.endsWith(".docx") || fileName.endsWith(".txt")) {
            TikaDocumentReader tikaReader = new TikaDocumentReader(new FileSystemResource(file));
            documents = tikaReader.get();
        } else {
            return new ArrayList<>();
        }

        // Chunking
        TokenTextSplitter splitter = new TokenTextSplitter(256, 0, 0, 1000, true);
        return splitter.apply(documents);
    }
    
    public String generateId(Document chunk) {

        // - give every chunk a deterministic Pinecone ID based on
        // - its document source, page, page number and chunk text
        
        String source = String.valueOf(chunk.getMetadata().getOrDefault("source", "unknown"));
        String page = String.valueOf(chunk.getMetadata().getOrDefault("page_number", 
                      chunk.getMetadata().getOrDefault("page", "0")));
        String content = chunk.getContent();
        
        String idText = String.format("%s|%s|%s", source, page, content);
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(idText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
