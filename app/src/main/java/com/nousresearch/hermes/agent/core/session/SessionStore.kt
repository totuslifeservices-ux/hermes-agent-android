package com.nousresearch.hermes.agent.core.session

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

// ── Room Entities ─────────────────────────────────────────────────────

/**
 * Room entity for the sessions table.
 * Each row represents one conversation session.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "model_config") val modelConfig: String? = null,
    @ColumnInfo(name = "message_count") val messageCount: Int = 0,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
)

/**
 * Room entity for the messages table.
 * Each row represents one message (user, assistant, or tool) in a session.
 * Foreign-key cascaded to sessions — deleting a session deletes its messages.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,
    val content: String? = null,
    @ColumnInfo(name = "tool_calls") val toolCalls: String? = null,
    @ColumnInfo(name = "tool_result") val toolResult: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

// ── DAO ───────────────────────────────────────────────────────────────

/**
 * Room DAO for sessions and messages tables.
 * All mutation operations are suspend functions for coroutine safety.
 * Queries use raw SQL for FTS5 full-text search to work around Room's
 * lack of native FTS5 annotation support.
 */
@Dao
interface SessionDao {
    // ── Sessions ───────────────────────────────────────────────────────

    @Query("SELECT * FROM sessions ORDER BY updated_at DESC")
    fun listSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Update
    suspend fun updateSession(session: SessionEntity)

    // ── Messages ───────────────────────────────────────────────────────

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun messageCount(sessionId: String): Int

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: String): MessageEntity?

    // ── FTS5 full-text search ──────────────────────────────────────────
    // Room doesn't natively support @Fts5 annotations (only @Fts4).
    // We use a raw query against the manually-created FTS5 virtual table.

    @RawQuery(observedEntities = [MessageEntity::class])
    fun searchMessagesFts(query: SupportSQLiteQuery): List<MessageEntity>

    // ── Archive / cleanup ──────────────────────────────────────────────

    @Query("DELETE FROM sessions WHERE updated_at < :before")
    suspend fun archiveSessionsBefore(before: Long)

    @Query(
        """
        UPDATE sessions SET
            message_count = (SELECT COUNT(*) FROM messages WHERE messages.session_id = sessions.id),
            token_count = :tokenCount
        WHERE id = :sessionId
        """,
    )
    suspend fun updateSessionCounts(sessionId: String, tokenCount: Int)
}

// ── Database ──────────────────────────────────────────────────────────

/**
 * Room database for the Hermes Agent session store.
 * Uses WAL journal mode for concurrent read/write performance.
 * Creates an FTS5 virtual table on first launch for full-text message search.
 */
@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        private const val DB_NAME = "hermes_agent.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addCallback(Fts5SetupCallback())
                .build()
                .also { db ->
                    // Enable WAL mode for concurrent reads during background writes
                    db.openHelper.writableDatabase.execSQL("PRAGMA journal_mode=WAL")
                }
        }
    }
}

// ── FTS5 Setup Callback ──────────────────────────────────────────────

/**
 * Room database callback that creates the FTS5 virtual table and sync triggers.
 *
 * FTS5 content-sync approach:
 *   - content=messages  → FTS5 reads the original table for unindexed content
 *   - content_rowid=rowid → rowid of messages table is used as rowid in FTS5
 *   - Triggers keep the FTS5 index in sync on INSERT/UPDATE/DELETE
 *   - tokenizer: porter (stemming) + unicode61 (unicode-aware tokenization)
 */
class Fts5SetupCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                content,
                content=messages,
                content_rowid=rowid,
                tokenize='porter unicode61'
            )
            """.trimIndent(),
        )

        // Trigger: after insert → insert into FTS5 index
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
            END
            """.trimIndent(),
        )

        // Trigger: after delete → remove from FTS5 index
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.rowid, old.content);
            END
            """.trimIndent(),
        )

        // Trigger: after update → remove old, insert new
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.rowid, old.content);
                INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
            END
            """.trimIndent(),
        )
    }
}

// ── SessionStore (Repository) ─────────────────────────────────────────

/**
 * High-level session store wrapping Room DAO operations.
 *
 * All public suspend functions dispatch to [Dispatchers.IO] so callers
 * don't need to manage thread pools. Observation flows ([listSessions],
 * [observeMessages]) return Room-reactive Flows that emit on DB changes.
 *
 * Features:
 * - CRUD for sessions and messages
 * - FTS5 full-text search across message content
 * - Automatic message count tracking on session metadata
 * - Archival of sessions older than a retention period
 */
class SessionStore(private val context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val dao: SessionDao by lazy { database.sessionDao() }

    // ── Session CRUD ───────────────────────────────────────────────────

    /**
     * Create a new session with a random UUID and optional metadata.
     * Returns the created [SessionEntity].
     */
    suspend fun createSession(
        title: String? = null,
        modelConfig: String? = null,
    ): SessionEntity = withContext(Dispatchers.IO) {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelConfig = modelConfig,
        )
        dao.upsertSession(session)
        session
    }

    /**
     * Delete a session and all its messages (CASCADE).
     */
    suspend fun deleteSession(sessionId: String): Unit = withContext(Dispatchers.IO) {
        dao.deleteSession(sessionId)
    }

    /**
     * Get a single session by ID, or null if not found.
     */
    suspend fun getSession(sessionId: String): SessionEntity? = withContext(Dispatchers.IO) {
        dao.getSession(sessionId)
    }

    /**
     * Observe all sessions ordered by most-recently-updated first.
     */
    fun listSessions(): Flow<List<SessionEntity>> = dao.listSessions()

    /**
     * Update session metadata.
     */
    suspend fun updateSession(session: SessionEntity): Unit = withContext(Dispatchers.IO) {
        dao.updateSession(session)
    }

    // ── Message CRUD ───────────────────────────────────────────────────

    /**
     * Add a single message to a session. Automatically updates the session's
     * updatedAt timestamp and messageCount.
     */
    suspend fun addMessage(
        sessionId: String,
        role: String, // "system" | "user" | "assistant" | "tool"
        content: String?,
        toolCalls: String? = null,
        toolResult: String? = null,
    ): MessageEntity = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            toolCalls = toolCalls,
            toolResult = toolResult,
        )
        dao.insertMessage(message)
        dao.getSession(sessionId)?.let { session ->
            dao.updateSession(
                session.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = dao.messageCount(sessionId),
                ),
            )
        }
        message
    }

    /**
     * Bulk-insert messages into a session. More efficient than calling
     * [addMessage] repeatedly for batch tool-call results.
     */
    suspend fun addMessages(messages: List<MessageEntity>): Unit = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        dao.insertMessages(messages)
        val sessionId = messages.first().sessionId
        dao.getSession(sessionId)?.let { session ->
            dao.updateSession(
                session.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = dao.messageCount(sessionId),
                ),
            )
        }
    }

    /**
     * Get all messages for a session, ordered by creation time ascending.
     */
    suspend fun getMessages(sessionId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        dao.getMessages(sessionId)
    }

    /**
     * Observe messages for a session reactively. Emits on any insert/update/delete.
     */
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(sessionId)

    /**
     * Get the most recent message in a session, or null if empty.
     */
    suspend fun getLastMessage(sessionId: String): MessageEntity? = withContext(Dispatchers.IO) {
        dao.getLastMessage(sessionId)
    }

    // ── Full-text search (FTS5) ────────────────────────────────────────

    /**
     * Search message content using FTS5 full-text search.
     *
     * The query is preprocessed to support prefix matching:
     *   - Each word gets a `*` suffix for prefix expansion
     *   - Words are joined with `AND`
     *
     * Example: `"hello world"` → `"hello* AND world*"`
     */
    suspend fun searchMessages(
        query: String,
        limit: Int = 20,
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        // Sanitise input and build FTS5 prefix-query syntax
        val sanitised = query.replace("'", "''")
        val ftsQuery = sanitised
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { "$it*" }

        if (ftsQuery.isBlank()) return@withContext emptyList()

        val sql =
            """
            SELECT messages.* FROM messages
            JOIN messages_fts ON messages.rowid = messages_fts.rowid
            WHERE messages_fts MATCH ?
            ORDER BY messages_fts.rank
            LIMIT $limit
            """.trimIndent()

        dao.searchMessagesFts(SimpleSQLiteQuery(sql, arrayOf(ftsQuery)))
    }

    // ── Archive / Maintenance ──────────────────────────────────────────

    /**
     * Delete sessions that haven't been updated in [retentionDays] days.
     * Messages are cascade-deleted with their parent session.
     */
    suspend fun archiveOldSessions(retentionDays: Int = 90): Unit =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
            dao.archiveSessionsBefore(cutoff)
        }

    /**
     * Update session token count and message count from current DB state.
     */
    suspend fun updateSessionCounts(
        sessionId: String,
        tokenCount: Int,
    ): Unit = withContext(Dispatchers.IO) {
        dao.updateSessionCounts(sessionId, tokenCount)
    }
}
