# OU Comp IT Degree RAG Project - Spring Boot Implementation

This is a Spring Boot application using Spring AI to implement a Retrieval-Augmented Generation (RAG) system.

Ported from the original Python/LangChain implementation.

## Features

- **Document Loading**: Supports PDF, DOCX, and TXT files.
- **Text Chunking**: Uses `TokenTextSplitter` for efficient document segmentation.
- **Vector Storage**: Integrates with Pinecone for storing and retrieving embeddings.
- **OpenAI Integration**: Uses OpenAI for embeddings and chat completions (LLM).
- **CLI Interface**: Interactive command-line interface for asking questions.

## Prerequisites

- Java 17 or higher
- Maven
- OpenAI API Key
- Pinecone API Key

## Configuration

The application requires environment variables for API keys.

Either set them in the environment or provide them in a `.env` file at the root.

The application will pick up values from `application.properties` which references these variables.

## Required Environment Variables

- `OPENAI_API_KEY`: Your OpenAI API key
- `PINECONE_API_KEY`: Your Pinecone API key
- `PINECONE_PROJECT_ID`: Your Pinecone Project ID
- `PINECONE_ENVIRONMENT`: Your Pinecone Environment
- `PINECONE_NAMESPACE`: Your Pinecone Namespace
- `PINECONE_INDEX_NAME`: Your Pinecone Index Name

## LangChain Compatibility

The application uses a `CustomPineconeVectorStore` (with `VectorStoreConfig`) that allows it to work with an existing Pinecone index created using LangChain, for the purpose of asking questions.

- It handles missing metadata fields gracefully.
- It attempts to find document content in either the configured `contentFieldName` (default: `text`), or fallback fields like `text` or `content`.

To use existing Pinecone indexes created by LangChain:

1. Ensure the five `PINECONE_` variables match your Pinecone console.
2. The application will attempt to use vectors in the specified index.

## Usage

1. **Build the project**:
   ```bash
   mvn clean install
   ```

2. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```
   
3. **Pinecone Index**:
   The application will log detailed information about the Pinecone index and its stats during startup.

4. **Interact**:
   The application will automatically:
   - Scan the folders specified in `application.properties` (e.g. `M255`, `M257`, etc.) in the parent directory.
   - Load documents, chunk them, and upload them to Pinecone (if they don't exist).
   - After processing, you will be prompted to enter questions.

5. __\***WARNING NOTE\***__:
   - For an existing Pinecone index created using LangChain, the application would re-upload documents that already exist in the index.
   - This is because Python uses the character-based splitter `RecursiveCharacterTextSplitter`.
   - Whereas Java uses the token-based splitter, `TokenTextSplitter`.
   - The two methods are cutting the cake into different slices.
   - None of the generated SHA-256 hashes would be the same – this hash is used to determine if the document chunk already exists.
   - So there would be duplicates in the existing index.
   - `app.folderNames=` in `application.properties` can be left blank.
   - This issue does not affect being able to ask questions using an existing index.

## Project Structure

- `CustomPineconeVectorStore`: A custom `VectorStore` that uses `PineconeClient` for vector interactions.
- `VectorStoreConfig`: A configuration class for `CustomPineconeVectorStore`.
- `AppRunner`: A `CommandLineRunner` that executes the main logic.
- `DocumentService`: Handles loading and chunking of various document formats.
- `QaService`: Orchestrates the RAG flow using `ChatClient` and `QuestionAnswerAdvisor`.
- `VectorStoreService`: Manages integration with Pinecone.
- `DebugLogger`: A utility class for logging debug information.

## Testing

Run unit tests using:
```bash
mvn test
```