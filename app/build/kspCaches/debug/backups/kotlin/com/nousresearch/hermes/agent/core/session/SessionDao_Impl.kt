package com.nousresearch.hermes.agent.core.session

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.RoomSQLiteQuery
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteQuery
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SessionDao_Impl(
  __db: RoomDatabase,
) : SessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSessionEntity: EntityInsertAdapter<SessionEntity>

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>

  private val __updateAdapterOfSessionEntity: EntityDeleteOrUpdateAdapter<SessionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSessionEntity = object : EntityInsertAdapter<SessionEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `sessions` (`id`,`title`,`created_at`,`updated_at`,`model_config`,`message_count`,`token_count`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
        val _tmpTitle: String? = entity.title
        if (_tmpTitle == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpTitle)
        }
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
        val _tmpModelConfig: String? = entity.modelConfig
        if (_tmpModelConfig == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModelConfig)
        }
        statement.bindLong(6, entity.messageCount.toLong())
        statement.bindLong(7, entity.tokenCount.toLong())
      }
    }
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `messages` (`id`,`session_id`,`role`,`content`,`tool_calls`,`tool_result`,`created_at`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.role)
        val _tmpContent: String? = entity.content
        if (_tmpContent == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpContent)
        }
        val _tmpToolCalls: String? = entity.toolCalls
        if (_tmpToolCalls == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpToolCalls)
        }
        val _tmpToolResult: String? = entity.toolResult
        if (_tmpToolResult == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpToolResult)
        }
        statement.bindLong(7, entity.createdAt)
      }
    }
    this.__updateAdapterOfSessionEntity = object : EntityDeleteOrUpdateAdapter<SessionEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `sessions` SET `id` = ?,`title` = ?,`created_at` = ?,`updated_at` = ?,`model_config` = ?,`message_count` = ?,`token_count` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
        val _tmpTitle: String? = entity.title
        if (_tmpTitle == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpTitle)
        }
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
        val _tmpModelConfig: String? = entity.modelConfig
        if (_tmpModelConfig == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModelConfig)
        }
        statement.bindLong(6, entity.messageCount.toLong())
        statement.bindLong(7, entity.tokenCount.toLong())
        statement.bindText(8, entity.id)
      }
    }
  }

  public override suspend fun upsertSession(session: SessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfSessionEntity.insert(_connection, session)
  }

  public override suspend fun insertMessage(message: MessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfMessageEntity.insert(_connection, message)
  }

  public override suspend fun insertMessages(messages: List<MessageEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMessageEntity.insert(_connection, messages)
  }

  public override suspend fun updateSession(session: SessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfSessionEntity.handle(_connection, session)
  }

  public override fun listSessions(): Flow<List<SessionEntity>> {
    val _sql: String = "SELECT * FROM sessions ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfModelConfig: Int = getColumnIndexOrThrow(_stmt, "model_config")
        val _columnIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "message_count")
        val _columnIndexOfTokenCount: Int = getColumnIndexOrThrow(_stmt, "token_count")
        val _result: MutableList<SessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SessionEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String?
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _tmpTitle = null
          } else {
            _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpModelConfig: String?
          if (_stmt.isNull(_columnIndexOfModelConfig)) {
            _tmpModelConfig = null
          } else {
            _tmpModelConfig = _stmt.getText(_columnIndexOfModelConfig)
          }
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_columnIndexOfMessageCount).toInt()
          val _tmpTokenCount: Int
          _tmpTokenCount = _stmt.getLong(_columnIndexOfTokenCount).toInt()
          _item =
              SessionEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpModelConfig,_tmpMessageCount,_tmpTokenCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSession(sessionId: String): SessionEntity? {
    val _sql: String = "SELECT * FROM sessions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfModelConfig: Int = getColumnIndexOrThrow(_stmt, "model_config")
        val _columnIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "message_count")
        val _columnIndexOfTokenCount: Int = getColumnIndexOrThrow(_stmt, "token_count")
        val _result: SessionEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String?
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _tmpTitle = null
          } else {
            _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpModelConfig: String?
          if (_stmt.isNull(_columnIndexOfModelConfig)) {
            _tmpModelConfig = null
          } else {
            _tmpModelConfig = _stmt.getText(_columnIndexOfModelConfig)
          }
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_columnIndexOfMessageCount).toInt()
          val _tmpTokenCount: Int
          _tmpTokenCount = _stmt.getLong(_columnIndexOfTokenCount).toInt()
          _result =
              SessionEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpModelConfig,_tmpMessageCount,_tmpTokenCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMessages(sessionId: String): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "session_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfToolCalls: Int = getColumnIndexOrThrow(_stmt, "tool_calls")
        val _columnIndexOfToolResult: Int = getColumnIndexOrThrow(_stmt, "tool_result")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String?
          if (_stmt.isNull(_columnIndexOfContent)) {
            _tmpContent = null
          } else {
            _tmpContent = _stmt.getText(_columnIndexOfContent)
          }
          val _tmpToolCalls: String?
          if (_stmt.isNull(_columnIndexOfToolCalls)) {
            _tmpToolCalls = null
          } else {
            _tmpToolCalls = _stmt.getText(_columnIndexOfToolCalls)
          }
          val _tmpToolResult: String?
          if (_stmt.isNull(_columnIndexOfToolResult)) {
            _tmpToolResult = null
          } else {
            _tmpToolResult = _stmt.getText(_columnIndexOfToolResult)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item =
              MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpToolCalls,_tmpToolResult,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeMessages(sessionId: String): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "session_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfToolCalls: Int = getColumnIndexOrThrow(_stmt, "tool_calls")
        val _columnIndexOfToolResult: Int = getColumnIndexOrThrow(_stmt, "tool_result")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String?
          if (_stmt.isNull(_columnIndexOfContent)) {
            _tmpContent = null
          } else {
            _tmpContent = _stmt.getText(_columnIndexOfContent)
          }
          val _tmpToolCalls: String?
          if (_stmt.isNull(_columnIndexOfToolCalls)) {
            _tmpToolCalls = null
          } else {
            _tmpToolCalls = _stmt.getText(_columnIndexOfToolCalls)
          }
          val _tmpToolResult: String?
          if (_stmt.isNull(_columnIndexOfToolResult)) {
            _tmpToolResult = null
          } else {
            _tmpToolResult = _stmt.getText(_columnIndexOfToolResult)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item =
              MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpToolCalls,_tmpToolResult,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun messageCount(sessionId: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM messages WHERE session_id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLastMessage(sessionId: String): MessageEntity? {
    val _sql: String =
        "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "session_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfToolCalls: Int = getColumnIndexOrThrow(_stmt, "tool_calls")
        val _columnIndexOfToolResult: Int = getColumnIndexOrThrow(_stmt, "tool_result")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MessageEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String?
          if (_stmt.isNull(_columnIndexOfContent)) {
            _tmpContent = null
          } else {
            _tmpContent = _stmt.getText(_columnIndexOfContent)
          }
          val _tmpToolCalls: String?
          if (_stmt.isNull(_columnIndexOfToolCalls)) {
            _tmpToolCalls = null
          } else {
            _tmpToolCalls = _stmt.getText(_columnIndexOfToolCalls)
          }
          val _tmpToolResult: String?
          if (_stmt.isNull(_columnIndexOfToolResult)) {
            _tmpToolResult = null
          } else {
            _tmpToolResult = _stmt.getText(_columnIndexOfToolResult)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result =
              MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpToolCalls,_tmpToolResult,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteSession(sessionId: String) {
    val _sql: String = "DELETE FROM sessions WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessage(messageId: String) {
    val _sql: String = "DELETE FROM messages WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun archiveSessionsBefore(before: Long) {
    val _sql: String = "DELETE FROM sessions WHERE updated_at < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, before)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateSessionCounts(sessionId: String, tokenCount: Int) {
    val _sql: String = """
        |
        |        UPDATE sessions SET
        |            message_count = (SELECT COUNT(*) FROM messages WHERE messages.session_id = sessions.id),
        |            token_count = ?
        |        WHERE id = ?
        |        
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, tokenCount.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchMessagesFts(query: SupportSQLiteQuery): List<MessageEntity> {
    val _rawQuery: RoomRawQuery = RoomSQLiteQuery.copyFrom(query).toRoomRawQuery()
    val _sql: String = _rawQuery.sql
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _rawQuery.getBindingFunction().invoke(_stmt)
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          _item =
              __entityStatementConverter_comNousresearchHermesAgentCoreSessionMessageEntity(_stmt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  private
      fun __entityStatementConverter_comNousresearchHermesAgentCoreSessionMessageEntity(statement: SQLiteStatement):
      MessageEntity {
    val _entity: MessageEntity
    val _columnIndexOfId: Int = getColumnIndex(statement, "id")
    val _columnIndexOfSessionId: Int = getColumnIndex(statement, "session_id")
    val _columnIndexOfRole: Int = getColumnIndex(statement, "role")
    val _columnIndexOfContent: Int = getColumnIndex(statement, "content")
    val _columnIndexOfToolCalls: Int = getColumnIndex(statement, "tool_calls")
    val _columnIndexOfToolResult: Int = getColumnIndex(statement, "tool_result")
    val _columnIndexOfCreatedAt: Int = getColumnIndex(statement, "created_at")
    val _tmpId: String
    if (_columnIndexOfId == -1) {
      error("Missing value for a NON-NULL column 'id', found NULL value instead.")
    } else {
      _tmpId = statement.getText(_columnIndexOfId)
    }
    val _tmpSessionId: String
    if (_columnIndexOfSessionId == -1) {
      error("Missing value for a NON-NULL column 'session_id', found NULL value instead.")
    } else {
      _tmpSessionId = statement.getText(_columnIndexOfSessionId)
    }
    val _tmpRole: String
    if (_columnIndexOfRole == -1) {
      error("Missing value for a NON-NULL column 'role', found NULL value instead.")
    } else {
      _tmpRole = statement.getText(_columnIndexOfRole)
    }
    val _tmpContent: String?
    if (_columnIndexOfContent == -1) {
      _tmpContent = null
    } else {
      if (statement.isNull(_columnIndexOfContent)) {
        _tmpContent = null
      } else {
        _tmpContent = statement.getText(_columnIndexOfContent)
      }
    }
    val _tmpToolCalls: String?
    if (_columnIndexOfToolCalls == -1) {
      _tmpToolCalls = null
    } else {
      if (statement.isNull(_columnIndexOfToolCalls)) {
        _tmpToolCalls = null
      } else {
        _tmpToolCalls = statement.getText(_columnIndexOfToolCalls)
      }
    }
    val _tmpToolResult: String?
    if (_columnIndexOfToolResult == -1) {
      _tmpToolResult = null
    } else {
      if (statement.isNull(_columnIndexOfToolResult)) {
        _tmpToolResult = null
      } else {
        _tmpToolResult = statement.getText(_columnIndexOfToolResult)
      }
    }
    val _tmpCreatedAt: Long
    if (_columnIndexOfCreatedAt == -1) {
      _tmpCreatedAt = 0
    } else {
      _tmpCreatedAt = statement.getLong(_columnIndexOfCreatedAt)
    }
    _entity =
        MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpToolCalls,_tmpToolResult,_tmpCreatedAt)
    return _entity
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
