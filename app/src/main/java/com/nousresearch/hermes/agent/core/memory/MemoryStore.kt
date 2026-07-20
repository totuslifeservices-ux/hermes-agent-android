package com.nousresearch.hermes.agent.core.memory

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ── Types ─────────────────────────────────────────────────────────────

/**
 * A memory entry stored in the vector store.
 */
@Serializable
data class MemoryEntry(
    val key: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Result of a memory search query.
 */
data class MemorySearchResult(
    val entry: MemoryEntry,
    val score: Float,
)

// ── Simple Embedding Engine ──────────────────────────────────────────

/**
 * Produces a dense embedding vector for a text string using a simple
 * character-n-gram hashing approach.
 *
 * This is a **fallback embedding** used when no external embedding model
 * (like Ollama's nomic-embed-text) is available. It produces a fixed-size
 * vector of [DIMENSION] floats by hashing character n-grams (unigrams and
 * bigrams) and accumulating them into the vector.
 *
 * The result is L2-normalized for cosine similarity computation.
 *
 * This is NOT as accurate as a real embedding model but works reasonably
 * well for short text retrieval (queries under 200 words) and has zero
 * model dependencies.
 */
class SimpleEmbeddingEngine(private val dimension: Int = 256) {

    /**
     * Compute an embedding vector for [text].
     */
    fun embed(text: String): FloatArray {
        val vector = FloatArray(dimension)
        val normalized = text.lowercase().trim()

        if (normalized.isEmpty()) return vector

        // Character unigrams
        for (ch in normalized) {
            val hash = (ch.code * 31) % dimension
            vector[hash.coerceAtLeast(0)] += 1.0f
        }

        // Character bigrams
        for (i in 0 until normalized.length - 1) {
            val bigram = normalized.substring(i, i + 2)
            val hash = (bigram.hashCode().absoluteValue) % dimension
            vector[hash] += 2.0f
        }

        // Word unigrams (hashed into subspace)
        val words = normalized.split(Regex("\\s+"))
        for ((idx, word) in words.withIndex()) {
            val hash = (word.hashCode().absoluteValue * 31 + idx * 7) % dimension
            vector[hash] += 3.0f
        }

        // L2 normalize
        val norm = vector.fold(0.0f) { acc, v -> acc + v * v }
        if (norm > 0f) {
            val invNorm = 1.0f / kotlin.math.sqrt(norm)
            for (i in vector.indices) {
                vector[i] *= invNorm
            }
        }

        return vector
    }

    /**
     * Compute cosine similarity between two vectors.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }
}

// ── Ollama Embedding Client ───────────────────────────────────────────

/**
 * Embedding provider that calls Ollama's embedding API.
 * Falls back to [SimpleEmbeddingEngine] if Ollama is unreachable.
 */
class OllamaEmbeddingClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text",
    private val fallbackEngine: SimpleEmbeddingEngine = SimpleEmbeddingEngine(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get an embedding from Ollama, falling back to simple embedding.
     */
    suspend fun embed(text: String): FloatArray {
        return try {
            val url = java.net.URL("$baseUrl/api/embeddings")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            val body = json.encodeToString(
                mapOf("model" to model, "prompt" to text),
            )
            conn.outputStream.write(body.toByteArray())

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            @Suppress("UNCHECKED_CAST")
            val parsed = json.decodeFromString<Map<String, Any>>(response)
            val embedding = parsed["embedding"] as? List<Number>
            if (embedding != null) {
                FloatArray(embedding.size) { embedding[it].toFloat() }
            } else {
                fallbackEngine.embed(text)
            }
        } catch (_: Exception) {
            fallbackEngine.embed(text)
        }
    }
}

// ── MemoryStore ───────────────────────────────────────────────────────

/**
 * On-device vector store for agent memory.
 *
 * Inspired by ChromaDB but implemented entirely in Kotlin with no native
 * dependencies. Supports:
 * - Store text with metadata and computed embedding
 * - Nearest-neighbor search via cosine similarity
 * - Forget (delete) by key
 * - Persistence to JSON file
 *
 * ## Embedding Strategy
 *
 * By default, uses [SimpleEmbeddingEngine] (n-gram hashing) which works
 * without any external models. If [ollamaBaseUrl] is provided, the store
 * delegates to Ollama's embedding API with a fallback to the simple engine.
 *
 * ## Thread Safety
 *
 * All public suspend functions are dispatched to [Dispatchers.IO].
 * Internal data structures use [ConcurrentHashMap] for thread-safe access.
 */
class MemoryStore(
    private val context: Context,
    private val ollamaBaseUrl: String? = null,
    private val dimension: Int = 256,
    persistFileName: String = "agent_memory.json",
) {
    // ── Configuration ──────────────────────────────────────────────────

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val persistFile: File = File(context.filesDir, persistFileName)
    private val embeddingEngine: SimpleEmbeddingEngine = SimpleEmbeddingEngine(dimension)
    private val ollamaClient: OllamaEmbeddingClient? =
        ollamaBaseUrl?.let { OllamaEmbeddingClient(baseUrl = it) }

    // ── State ──────────────────────────────────────────────────────────

    // Key → (entry, embedding)
    private val store = ConcurrentHashMap<String, Pair<MemoryEntry, FloatArray>>()

    // ── Initialisation ─────────────────────────────────────────────────

    /**
     * Load persisted memory from disk. Call once at app startup.
     */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (!persistFile.exists()) return@withContext
        try {
            val text = persistFile.readText()
            @Suppress("UNCHECKED_CAST")
            val entries = json.decodeFromString<List<Map<String, Any>>>(text)
            for (entryMap in entries) {
                val key = entryMap["key"] as? String ?: continue
                val textContent = entryMap["text"] as? String ?: continue
                @Suppress("UNCHECKED_CAST")
                val metadata = (entryMap["metadata"] as? Map<String, String>) ?: emptyMap()
                val timestamp = (entryMap["timestamp"] as? Number)?.toLong()
                    ?: System.currentTimeMillis()
                val entry = MemoryEntry(key, textContent, metadata, timestamp)
                val embedding = embedText(textContent)
                store[key] = Pair(entry, embedding)
            }
        } catch (_: Exception) {
            // Corrupted file — start fresh
            store.clear()
        }
    }

    /**
     * Persist current memory to disk.
     */
    private suspend fun persist(): Unit = withContext(Dispatchers.IO) {
        try {
            val entries = store.values.map { it.first }
            val text = json.encodeToString(entries)
            persistFile.writeText(text)
        } catch (_: Exception) {
            // Best-effort persistence
        }
    }

    // ── Core Operations ────────────────────────────────────────────────

    /**
     * Store a text entry with optional metadata.
     *
     * @param key Unique identifier for this memory.
     * @param text The text content to remember.
     * @param metadata Optional key-value metadata.
     */
    suspend fun store(
        key: String,
        text: String,
        metadata: Map<String, String> = emptyMap(),
    ): Unit = withContext(Dispatchers.IO) {
        val entry = MemoryEntry(key, text, metadata)
        val embedding = embedText(text)
        store[key] = Pair(entry, embedding)
        persist()
    }

    /**
     * Search for the most similar entries to [query].
     *
     * @param query The search text.
     * @param limit Maximum number of results to return.
     * @param minScore Minimum similarity score (0.0–1.0). Results below this are excluded.
     * @return List of [MemorySearchResult] sorted by descending similarity.
     */
    suspend fun search(
        query: String,
        limit: Int = 5,
        minScore: Float = 0.1f,
    ): List<MemorySearchResult> = withContext(Dispatchers.IO) {
        if (store.isEmpty()) return@withContext emptyList()

        val queryEmbedding = embedText(query)

        val scored = store.values.map { (entry, embedding) ->
            val score = embeddingEngine.cosineSimilarity(queryEmbedding, embedding)
            MemorySearchResult(entry, score)
        }

        scored
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Forget (delete) a stored memory by key.
     *
     * @return true if the key existed and was removed.
     */
    suspend fun forget(key: String): Boolean = withContext(Dispatchers.IO) {
        val removed = store.remove(key) != null
        if (removed) persist()
        removed
    }

    /**
     * Get a single entry by key.
     */
    suspend fun get(key: String): MemoryEntry? = withContext(Dispatchers.IO) {
        store[key]?.first
    }

    /**
     * List all stored memory keys.
     */
    suspend fun listKeys(): List<String> = withContext(Dispatchers.IO) {
        store.keys.toList()
    }

    /**
     * Get the total number of stored memories.
     */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        store.size
    }

    /**
     * Clear all stored memories.
     */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        store.clear()
        persist()
    }

    // ── Embedding ──────────────────────────────────────────────────────

    /**
     * Compute an embedding, preferring Ollama if configured.
     */
    private suspend fun embedText(text: String): FloatArray {
        return if (ollamaClient != null) {
            ollamaClient.embed(text)
        } else {
            embeddingEngine.embed(text)
        }
    }
}
