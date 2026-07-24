package com.nousresearch.hermes.agent.core.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for sessions and messages.
 */
@Dao
interface SessionDao {
    // ── Sessions ─────────────────────────────────────────────────────

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

    // ── Messages ─────────────────────────────────────────────────────

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

    // ── FTS5 Search (RawQuery to bypass compile-time SQL validation) ──

    @RawQuery(observedEntities = [MessageEntity::class])
    suspend fun searchMessagesFts(query: SupportSQLiteQuery): List<MessageEntity>

    // ── Archive / Cleanup ────────────────────────────────────────────

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
