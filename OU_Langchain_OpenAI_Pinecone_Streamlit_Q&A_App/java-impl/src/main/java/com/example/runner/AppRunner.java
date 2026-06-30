package com.example.runner;

import com.example.service.DocumentService;
import com.example.service.QaService;
import com.example.service.QaResponse;
import com.example.service.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Component
public class AppRunner implements CommandLineRunner {

    private final DocumentService documentService;
    private final VectorStoreService vectorStoreService;
    private final QaService qaService;

    @Value("${app.folderNames}")
    private String[] folderNames;

    @Value("${app.referenceLimit:10}")
    private int referenceLimit;

    public AppRunner(DocumentService documentService, VectorStoreService vectorStoreService, QaService qaService) {
        this.documentService = documentService;
        this.vectorStoreService = vectorStoreService;
        this.qaService = qaService;
    }

    @Override
    public void run(String... args) {
        Path rootPath = Paths.get(".");
        
        // Use the first folder name from config for existence check
        String firstFolder = (folderNames != null && folderNames.length > 0) ? folderNames[0] : "";
        
        if (!firstFolder.isEmpty() && !Files.exists(rootPath.resolve(firstFolder))) {
            final Path path = Paths.get("..");
            if (Files.exists(path.resolve(firstFolder))) {
                rootPath = path;
            } else {
                final Path rootPathTest = Paths.get("../..");
                if (Files.exists(rootPathTest.resolve(firstFolder))) {
                    rootPath = rootPathTest;
                }
            }
        }

        List<Document> allChunks = new ArrayList<>();
        
        if (folderNames != null) {
            for (String folderName : folderNames) {
                Path folderPath = rootPath.resolve(folderName);
                File folder = folderPath.toFile();
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles((dir, name) ->
                            name.toLowerCase().endsWith(".pdf") ||
                            name.toLowerCase().endsWith(".docx") ||
                            name.toLowerCase().endsWith(".txt"));
                    
                    if (files != null) {
                        for (File file : files) {
                            System.out.println("Loading and chunking: " + file.getAbsolutePath());
                            List<Document> chunks = documentService.loadAndChunk(file);
                            List<Document> processedChunks = new ArrayList<>();
                            for (Document chunk : chunks) {
                                // Spring AI PineconeVectorStore expects the content to be available in metadata "text" field
                                chunk.getMetadata().put("text", chunk.getContent());
                                
                                // Use normalised path as source
                                String sourcePath = file.getAbsolutePath().replace("\\", "/");
                                chunk.getMetadata().putIfAbsent("source", sourcePath);
                                
                                // Ensure page information is present
                                chunk.getMetadata().putIfAbsent("page", 0);
                                chunk.getMetadata().putIfAbsent("page_number", 0);
                                
                                // Safety defaults for other fields
                                chunk.getMetadata().putIfAbsent("distance", 0.0);
                                chunk.getMetadata().putIfAbsent("content", chunk.getContent());
                                chunk.getMetadata().putIfAbsent("metadata", "{}");
    
                                // Create deterministic ID using the enriched metadata
                                String deterministicId = documentService.generateId(chunk);
                                
                                // Create a new Document with the deterministic ID
                                Document idDocument = new Document(deterministicId, chunk.getContent(), chunk.getMetadata());
                                processedChunks.add(idDocument);
                            }
                            allChunks.addAll(processedChunks);
                        }
                    }
                }
            }
        }
        
        if (!allChunks.isEmpty()) {
            System.out.println("Adding " + allChunks.size() + " chunks to vector store...");
            vectorStoreService.addDocuments(allChunks);
        } else {
            System.out.println("No documents found in specified folders.");
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your questions (or write 'quit' to quit).");
        int i = 1;
        while (true) {
            System.out.print("Question #" + i + ": ");
            String question = scanner.nextLine();
            if (question == null || question.equalsIgnoreCase("quit")) {
                break;
            }
            
            try {
                QaResponse response = qaService.ask(question);
                System.out.println("\n--------------------------------------------------");
                System.out.println("Query: " + question);
                System.out.println();
                System.out.println("Answer: " + response.getAnswer());
                
                System.out.println("\nReference(s):");
                List<Document> sourceDocs = response.getSourceDocuments();
                int limit = Math.min(sourceDocs.size(), referenceLimit);
                for (int j = 0; j < limit; j++) {
                    Document doc = sourceDocs.get(j);
                    String source = (String) doc.getMetadata().getOrDefault("source", "Unknown document");
                    Object pageLabel = doc.getMetadata().get("page_label");
                    Object page = doc.getMetadata().get("page");
                    Double distance = (Double) doc.getMetadata().getOrDefault("distance", 0.0);
                    double score = 1.0 - distance;

                    System.out.println("\nDocument source: " + source);
                    System.out.println("Page label: " + pageLabel);
                    System.out.println("Page index: " + page);
                    System.out.printf("Relevance score: %.4f\n", score);
                }
                
                System.out.println("--------------------------------------------------\n");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Failed to get answer: " + e.getMessage());
                
                // Detailed check for the Protobuf Struct null crash
                boolean isProtobufCrash = false;
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof IllegalArgumentException) {
                        for (StackTraceElement element : cause.getStackTrace()) {
                            if (element.getClassName().contains("com.google.protobuf.Struct")) {
                                isProtobufCrash = true;
                                break;
                            }
                        }
                    }
                    cause = cause.getCause();
                }

                if (isProtobufCrash) {
                    System.err.println("CRITICAL: This error is caused by missing metadata fields in the existing Pinecone index records.");
                    System.err.println("The application has been updated to use a CustomPineconeVectorStore to handle this, but if you still see this, it means the custom mapping failed.");
                    System.err.println("\nRECOMMENDATION:");
                    System.err.println("1. Ensure your Pinecone index has the 'text' or 'content' field populated (LangChain usually uses 'text').");
                    System.err.println("2. If the problem persists, you may need to re-index your documents.");
                    e.printStackTrace();
                } else {
                    e.printStackTrace();
                }
            }
            i++;
        }
    }
}
