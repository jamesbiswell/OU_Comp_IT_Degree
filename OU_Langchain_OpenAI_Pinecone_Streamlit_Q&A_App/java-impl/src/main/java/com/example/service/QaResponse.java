package com.example.service;

import org.springframework.ai.document.Document;
import java.util.List;

public class QaResponse {
    private final String answer;
    private final List<Document> sourceDocuments;

    public QaResponse(String answer, List<Document> sourceDocuments) {
        this.answer = answer;
        this.sourceDocuments = sourceDocuments;
    }

    public String getAnswer() {
        return answer;
    }

    public List<Document> getSourceDocuments() {
        return sourceDocuments;
    }
}
