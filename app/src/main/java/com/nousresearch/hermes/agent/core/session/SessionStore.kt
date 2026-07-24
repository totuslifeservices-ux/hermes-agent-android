package com.nousresearch.hermes.agent.core.session

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Room database for session storage with FTS5 full-text search.
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
        }
    }
}

/**
 * Creates the FTS5 virtual table and sync triggers on first database creation.
 */
class Fts5SetupCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL("PRAGMA journal_mode=WAL")

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

        // Trigger: after insert
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                INSERT INTO messages_fts(rowid, content) VALUES (new.rowid, new.content);
            END
            """.trimIndent(),
        )

        // Trigger: after delete
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.rowid, old.content);
            END
            """.trimIndent(),
        )

        // Trigger: after update
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

/**
 * High-level session store wrapping Room DAO operations.
 * All suspend functions dispatch to Dispatchers.IO.
 */
class SessionStore(private val context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val dao: SessionDao by lazy { database.sessionDao() }

    // ── Session CRUD ──────────────────────────────────────────────────

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

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteSession(sessionId)
    }

    suspend fun getSession(sessionId: String): SessionEntity? = withContext(Dispatchers.IO) {
        dao.getSession(sessionId)
    }

    fun listSessions(): Flow<List<SessionEntity>> = dao.listSessions()

    suspend fun updateSession(session: SessionEntity) = withContext(Dispatchers.IO) {
        dao.updateSession(session)
    }

    // ── Message CRUD ─────────────────────────────────────────────────

    suspend fun addMessage(
        sessionId: String,
        role: String,
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

    suspend fun addMessages(messages: List<MessageEntity>) = withContext(Dispatchers.IO) {
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

    suspend fun getMessages(sessionId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        dao.getMessages(sessionId)
    }

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(sessionId)

    suspend fun getLastMessage(sessionId: String): MessageEntity? = withContext(Dispatchers.IO) {
        dao.getLastMessage(sessionId)
    }

    // ── Full-text search (FTS5) ───────────────────────────────────────

    suspend fun searchMessages(
        query: String,
        limit: Int = 20,
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        val sanitised = query.replace("'", "''")
        val ftsQuery = sanitised
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { "$it*" }
        if (ftsQuery.isBlank()) return@withContext emptyList()

        val sql = """
            SELECT messages.* FROM messages
            JOIN messages_fts ON messages.rowid = messages_fts.rowid
            WHERE messages_fts MATCH ?
            ORDER BY messages_fts.rank
            LIMIT $limit
        """.trimIndent()

        dao.searchMessagesFts(SimpleSQLiteQuery(sql, arrayOf(ftsQuery)))
    }

    // ── Archive / Maintenance ────────────────────────────────────────

    suspend fun archiveOldSessions(retentionDays: Int = 90) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
        dao.archiveSessionsBefore(cutoff)
    }

    suspend fun updateSessionCounts(sessionId: String, tokenCount: Int) = withContext(Dispatchers.IO) {
        dao.updateSessionCounts(sessionId, tokenCount)
    }
}
