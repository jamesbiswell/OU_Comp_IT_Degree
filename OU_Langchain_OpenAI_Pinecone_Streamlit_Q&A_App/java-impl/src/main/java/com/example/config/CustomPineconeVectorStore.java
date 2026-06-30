package com.example.config;

import com.example.util.DebugLogger;
import com.google.protobuf.Value;
import io.pinecone.PineconeConnection;
import io.pinecone.PineconeControlPlaneClient;
import io.pinecone.proto.FetchResponse;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;
import org.openapitools.client.model.CreateIndexRequest;
import org.openapitools.client.model.CreateIndexRequestSpec;
import org.openapitools.client.model.IndexMetric;
import org.openapitools.client.model.IndexModel;
import org.openapitools.client.model.ServerlessSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PineconeVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A custom PineconeVectorStore that handles missing metadata gracefully.
 * LangChain and other clients might not store the same metadata fields that 
 * Spring AI 1.0.0-M1 expects, which causes crashes in the default implementation.
 */
public class CustomPineconeVectorStore extends PineconeVectorStore {

    private final String contentFieldName;
    private final PineconeConnection connection;
    private final EmbeddingModel embeddingModel;
    private final DebugLogger debugLogger;
    private final PineconeVectorStoreConfig config;
    private final String pineconeNamespace;

    public CustomPineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel,
                                     String contentFieldName, DebugLogger debugLogger) {
        this(config, embeddingModel, contentFieldName, debugLogger, null);
    }

    public CustomPineconeVectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel,
                                     String contentFieldName, DebugLogger debugLogger,
                                     PineconeConnection injectedConnection) {
        super(config, embeddingModel);
        this.config = config;
        this.embeddingModel = embeddingModel;
        this.contentFieldName = contentFieldName;
        this.debugLogger = debugLogger;
        
        if (injectedConnection != null) {
            this.connection = injectedConnection;
        } else {
            // Use reflection to get the connection from the parent class
            Field connectionField = ReflectionUtils.findField(PineconeVectorStore.class, "pineconeConnection");
            if (connectionField == null) {
                // Fallback for older versions if needed
                connectionField = ReflectionUtils.findField(PineconeVectorStore.class, "connection");
            }
            
            if (connectionField != null) {
                ReflectionUtils.makeAccessible(connectionField);
                this.connection = (PineconeConnection) ReflectionUtils.getField(connectionField, this);
            } else {
                this.connection = null;
            }
        }
        
        String ns = "";
        try {
            Field namespaceField = ReflectionUtils.findField(PineconeVectorStore.class, "pineconeNamespace");
            if (namespaceField == null) {
                namespaceField = ReflectionUtils.findField(PineconeVectorStore.class, "namespace");
            }
            if (namespaceField != null) {
                ReflectionUtils.makeAccessible(namespaceField);
                ns = (String) ReflectionUtils.getField(namespaceField, this);
            }
        } catch (Exception e) {
            debugLogger.error("[DEBUG_LOG] Failed to access namespace field: " + e.getMessage());
        }
        this.pineconeNamespace = ns;
    }

    public void checkAndCreateIndex() {
        try {
            // Use reflection to get the connection from the parent class
            Field clientConfigField = ReflectionUtils.findField(PineconeVectorStoreConfig.class, "clientConfig");
            Field connectionConfigField = ReflectionUtils.findField(PineconeVectorStoreConfig.class, "connectionConfig");
            
            if (clientConfigField == null || connectionConfigField == null) {
                debugLogger.error("[DEBUG_LOG] Could not find internal config fields in PineconeVectorStoreConfig");
                return;
            }
            
            ReflectionUtils.makeAccessible(clientConfigField);
            ReflectionUtils.makeAccessible(connectionConfigField);
            
            io.pinecone.PineconeClientConfig clientConfig = (io.pinecone.PineconeClientConfig)
                    ReflectionUtils.getField(clientConfigField, config);
            io.pinecone.PineconeConnectionConfig connectionConfig = (io.pinecone.PineconeConnectionConfig)
                    ReflectionUtils.getField(connectionConfigField, config);
            
            if (clientConfig == null || connectionConfig == null) {
                debugLogger.error("[DEBUG_LOG] Config values are null");
                return;
            }

            String indexName = connectionConfig.getIndexName();
            String apiKey = clientConfig.getApiKey();
            
            PineconeControlPlaneClient controlPlaneClient = new PineconeControlPlaneClient(apiKey);
            List<IndexModel> indexes = controlPlaneClient.listIndexes().getIndexes();
            
            boolean exists = false;
            if (indexes != null) {
                for (IndexModel index : indexes) {
                    if (indexName.equals(index.getName())) {
                        exists = true;
                        break;
                    }
                }
            }

            if (!exists) {
                debugLogger.log("[DEBUG_LOG] Creating index " + indexName + " ... ");
                
                ServerlessSpec serverlessSpec = new ServerlessSpec()
                        .cloud(ServerlessSpec.CloudEnum.AWS)
                        .region("us-east-1");
                
                CreateIndexRequestSpec spec = new CreateIndexRequestSpec()
                        .serverless(serverlessSpec);
                
                CreateIndexRequest createRequest = new CreateIndexRequest()
                        .name(indexName)
                        .dimension(1536)
                        .metric(IndexMetric.COSINE)
                        .spec(spec);
                
                controlPlaneClient.createIndex(createRequest);
                debugLogger.log("[DEBUG_LOG] Index " + indexName + " created successfully.");
            } else {
                debugLogger.log("[DEBUG_LOG] Index " + indexName + " already exists.");
            }
            
            // Describe index and pretty print details
            try {
                IndexModel indexInfo = controlPlaneClient.describeIndex(indexName);
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                String json = mapper.writeValueAsString(indexInfo);
                debugLogger.log("[DEBUG_LOG] Pinecone Index Details:\n" + json);
            } catch (Exception e) {
                debugLogger.error("[DEBUG_LOG] Failed to describe index: " + e.getMessage());
            }

            // Describe index stats if connection is available
            if (this.connection != null) {
                try {
                    io.pinecone.proto.DescribeIndexStatsResponse stats = this.connection.getBlockingStub()
                            .describeIndexStats(io.pinecone.proto.DescribeIndexStatsRequest.newBuilder().build());
                    
                    // We use Jackson to pretty print the Proto message using Printer or converting to Map
                    com.google.protobuf.util.JsonFormat.Printer printer = com.google.protobuf.util.JsonFormat.printer()
                            .preservingProtoFieldNames()
                            .omittingInsignificantWhitespace();
                    
                    String jsonStats = printer.print(stats);
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    Object jsonMap = mapper.readValue(jsonStats, Object.class);
                    String prettyJsonStats = mapper.writeValueAsString(jsonMap);
                    
                    debugLogger.log("[DEBUG_LOG] Pinecone Index Stats:\n" + prettyJsonStats);
                } catch (Exception e) {
                    debugLogger.error("[DEBUG_LOG] Failed to describe index stats: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            debugLogger.error("[DEBUG_LOG] Error checking/creating index: " + e.getMessage());
        }
    }

    @Override
    public void add(List<Document> documents) {
        if (this.connection == null || documents.isEmpty()) {
            super.add(documents);
            return;
        }

        int fetchBatchSize = 100;
        List<Document> chunksToAdd = new ArrayList<>();

        debugLogger.log("[DEBUG_LOG] Checking existence of " + documents.size() + " IDs in Pinecone in batches of "
                + fetchBatchSize + "...");

        for (int i = 0; i < documents.size(); i += fetchBatchSize) {
            int end = Math.min(i + fetchBatchSize, documents.size());
            List<Document> batchChunks = documents.subList(i, end);
            List<String> batchIds = batchChunks.stream().map(Document::getId).collect(Collectors.toList());

            try {
                FetchResponse fetchResponse = this.connection.getBlockingStub()
                        .fetch(io.pinecone.proto.FetchRequest.newBuilder()
                                .addAllIds(batchIds)
                                .setNamespace(pineconeNamespace)
                                .build());

                Map<String, io.pinecone.proto.Vector> existingVectors = fetchResponse.getVectorsMap();

                for (Document chunk : batchChunks) {
                    if (!existingVectors.containsKey(chunk.getId())) {
                        chunksToAdd.add(chunk);
                    }
                }
            } catch (Exception e) {
                debugLogger.error("[DEBUG_LOG] Error checking existing IDs in batch: " + e.getMessage());
            }
        }

        if (chunksToAdd.size() < documents.size()) {
            debugLogger.log("[DEBUG_LOG] Skipped " + (documents.size() - chunksToAdd.size()) + " existing chunks.");
        }

        if (!chunksToAdd.isEmpty()) {
            int embeddingBatchSize = 100;
            int totalBatches = (chunksToAdd.size() + embeddingBatchSize - 1) / embeddingBatchSize;
            debugLogger.log("[DEBUG_LOG] Adding " + chunksToAdd.size() + " new documents to index in "
                    + totalBatches + " batches...");

            for (int i = 0; i < chunksToAdd.size(); i += embeddingBatchSize) {
                int end = Math.min(i + embeddingBatchSize, chunksToAdd.size());
                List<Document> batchToAdd = chunksToAdd.subList(i, end);
                int batchNumber = (i / embeddingBatchSize) + 1;
                
                debugLogger.log("[DEBUG_LOG] Adding batch " + batchNumber + "/" + totalBatches
                        + " (" + batchToAdd.size() + " documents) ...");
                super.add(batchToAdd);
                debugLogger.log("[DEBUG_LOG] Batch " + batchNumber + " Ok");
            }
            debugLogger.log("[DEBUG_LOG] All batches added successfully!");
        } else {
            debugLogger.log("[DEBUG_LOG] All chunks already exist in Pinecone. No new inserts needed.");
        }
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return this.similaritySearch(SearchRequest.query(query));
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        debugLogger.log("[DEBUG_LOG] CustomPineconeVectorStore.similaritySearch called for query: " + request.getQuery());
        
        if (this.connection == null) {
            debugLogger.error("[DEBUG_LOG] Connection is null, falling back to super.similaritySearch");
            return super.similaritySearch(request);
        }

        List<Double> queryEmbedding = this.embeddingModel.embed(request.getQuery());
        debugLogger.log("[DEBUG_LOG] Query embedding dimension: " + queryEmbedding.size());
        List<Float> floatList = queryEmbedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());

        debugLogger.log("[DEBUG_LOG] Searching in namespace: '" + pineconeNamespace + "'");

        try {
            QueryResponse response = connection.getBlockingStub()
                    .query(io.pinecone.proto.QueryRequest.newBuilder()
                            .addAllVector(floatList)
                            .setTopK(request.getTopK())
                            .setNamespace(pineconeNamespace)
                            .setIncludeMetadata(true)
                            .build());

            if (response.getMatchesCount() == 0) {
                debugLogger.log("[DEBUG_LOG] No matches found in namespace '" + pineconeNamespace + "'.");
            } else {
                debugLogger.log("[DEBUG_LOG] Found " + response.getMatchesCount() + " matches.");
            }

            return response.getMatchesList().stream()
                    .map(this::mapScoredVectorToDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            debugLogger.error("[DEBUG_LOG] Error during direct Pinecone query: " + e.getMessage());
            return super.similaritySearch(request);
        }
    }

    private String getValueAsString(Value value) {
        if (value == null) return "";
        if (value.getKindCase() == Value.KindCase.STRING_VALUE) {
            return value.getStringValue();
        }
        return value.toString();
    }

    private Document mapScoredVectorToDocument(ScoredVector scoredVector) {
        debugLogger.log("[DEBUG_LOG] Mapping vector ID: " + scoredVector.getId()
                + " (Score: " + scoredVector.getScore() + ")");
        
        Map<String, Value> metadataMap = Collections.emptyMap();
        if (scoredVector.hasMetadata()) {
            try {
                com.google.protobuf.Struct struct = scoredVector.getMetadata();
                if (struct != null) {
                    metadataMap = struct.getFieldsMap();
                }
            } catch (Exception e) {
                debugLogger.error("[DEBUG_LOG] Failed to get metadata map: " + e.getMessage());
            }
        }
        
        String content = "";
        String source = getValueAsString(metadataMap.get("source"));
        if (metadataMap.containsKey(contentFieldName)) {
            content = getValueAsString(metadataMap.get(contentFieldName));
        } else if (metadataMap.containsKey("text")) {
            content = getValueAsString(metadataMap.get("text"));
        } else if (metadataMap.containsKey("content")) {
            content = getValueAsString(metadataMap.get("content"));
        }

        debugLogger.log("[DEBUG_LOG] Retrieved from: " + source);
        debugLogger.log("[DEBUG_LOG] Retrieved content length: " + content.length());
        debugLogger.log("[DEBUG_LOG] Retrieved content snippet: "
                + (content.length() > 200 ? content.substring(0, 200).replace("\n", " ")
                                            + "..." : content.replace("\n", " ")));

        Map<String, Object> metadata = new java.util.HashMap<>();
        for (Map.Entry<String, Value> entry : metadataMap.entrySet()) {
            try {
                Value value = entry.getValue();
                if (value == null) continue;
                switch (value.getKindCase()) {
                    case STRING_VALUE: metadata.put(entry.getKey(), value.getStringValue()); break;
                    case NUMBER_VALUE: metadata.put(entry.getKey(), value.getNumberValue()); break;
                    case BOOL_VALUE: metadata.put(entry.getKey(), value.getBoolValue()); break;
                    case LIST_VALUE: metadata.put(entry.getKey(), value.getListValue()); break;
                    case STRUCT_VALUE: metadata.put(entry.getKey(), value.getStructValue()); break;
                    case NULL_VALUE: metadata.put(entry.getKey(), null); break;
                    default: metadata.put(entry.getKey(), value.toString());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        metadata.put("distance", 1.0 - scoredVector.getScore());
        
        return new Document(scoredVector.getId(), content, metadata);
    }
}
