package com.ptsdalert.infrastructure.logging.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.domain.model.LogLevel
import com.ptsdalert.domain.ports.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SqliteLogRepository(context: Context) : LogRepository {

    private val db = LogDbHelper(context)
    // Emits Unit every time a row is inserted, so observers know to re-query.
    private val _insertSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    override suspend fun insert(entry: LogEntry) = withContext(Dispatchers.IO) {
        db.insert(entry)
        _insertSignal.tryEmit(Unit)
    }

    override fun observeRecent(limit: Int): Flow<List<LogEntry>> = flow {
        emit(db.queryRecent(limit))
        _insertSignal.collect { emit(db.queryRecent(limit)) }
    }.flowOn(Dispatchers.IO)
}

private class LogDbHelper(context: Context) :
    SQLiteOpenHelper(context, "app_logs.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_LEVEL     TEXT    NOT NULL,
                $COL_TAG       TEXT    NOT NULL,
                $COL_MESSAGE   TEXT    NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_ts ON $TABLE ($COL_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun insert(entry: LogEntry) {
        writableDatabase.insert(TABLE, null, ContentValues().apply {
            put(COL_TIMESTAMP, entry.timestamp)
            put(COL_LEVEL, entry.level.name)
            put(COL_TAG, entry.tag)
            put(COL_MESSAGE, entry.message)
        })
    }

    fun queryRecent(limit: Int): List<LogEntry> =
        readableDatabase.query(
            TABLE, null, null, null, null, null,
            "$COL_TIMESTAMP DESC", limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(LogEntry(
                        id        = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        level     = LogLevel.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_LEVEL))),
                        tag       = cursor.getString(cursor.getColumnIndexOrThrow(COL_TAG)),
                        message   = cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE))
                    ))
                }
            }
        }

    companion object {
        private const val DB_VERSION = 1
        private const val TABLE = "logs"
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_LEVEL = "level"
        private const val COL_TAG = "tag"
        private const val COL_MESSAGE = "message"
    }
}
