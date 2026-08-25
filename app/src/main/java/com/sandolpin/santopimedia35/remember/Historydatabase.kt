package com.sandolpin.santopimedia35.remember

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PlayHistoryEntity(
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String?,
    val appLabel: String,
    val packageName: String,
    val playedAtMs: Long,       // 再生開始時刻
    val durationMs: Long,       // 曲の長さ
    val playTimeMs: Long,       // 実際に再生していた時間（停止・スキップ関係なく再生時間）
    val listenedMs: Long,       // どこまで再生位置が進んだか
    val listenedAll: Boolean,   // 90%以上聴いたか
    val mediaId: String = ""    // 曲固有のメディアID（再生リクエスト用。空="不明"）
)

// 除外パッケージ
data class ExcludedApp(val packageName: String, val appLabel: String)

private class HistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, "santopi_history.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS play_history (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT NOT NULL,
                artist      TEXT NOT NULL,
                album       TEXT NOT NULL,
                albumArtUri TEXT,
                appLabel    TEXT NOT NULL,
                packageName TEXT NOT NULL,
                playedAtMs  INTEGER NOT NULL UNIQUE,
                durationMs  INTEGER NOT NULL,
                playTimeMs  INTEGER NOT NULL DEFAULT 0,
                listenedMs  INTEGER NOT NULL,
                listenedAll INTEGER NOT NULL,
                mediaId     TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS excluded_apps (
                packageName TEXT PRIMARY KEY,
                appLabel    TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("ALTER TABLE play_history ADD COLUMN playTimeMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS excluded_apps (
                    packageName TEXT PRIMARY KEY,
                    appLabel    TEXT NOT NULL
                )
            """.trimIndent())
        }
        if (old < 3) {
            // playedAtMs にUNIQUE制約を追加
            // SQLiteはALTER TABLEでのUNIQUE追加不可のため、テーブルを作り直す
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS play_history_new (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    title       TEXT NOT NULL,
                    artist      TEXT NOT NULL,
                    album       TEXT NOT NULL,
                    albumArtUri TEXT,
                    appLabel    TEXT NOT NULL,
                    packageName TEXT NOT NULL,
                    playedAtMs  INTEGER NOT NULL UNIQUE,
                    durationMs  INTEGER NOT NULL,
                    playTimeMs  INTEGER NOT NULL DEFAULT 0,
                    listenedMs  INTEGER NOT NULL,
                    listenedAll INTEGER NOT NULL
                )
            """.trimIndent())
            // 既存データを移行（重複はplayTimeMs最大のものだけ残す）
            db.execSQL("""
                INSERT OR REPLACE INTO play_history_new
                    (id, title, artist, album, albumArtUri, appLabel, packageName,
                     playedAtMs, durationMs, playTimeMs, listenedMs, listenedAll)
                SELECT id, title, artist, album, albumArtUri, appLabel, packageName,
                       playedAtMs, durationMs, MAX(playTimeMs), listenedMs, listenedAll
                FROM play_history
                GROUP BY playedAtMs
            """.trimIndent())
            db.execSQL("DROP TABLE play_history")
            db.execSQL("ALTER TABLE play_history_new RENAME TO play_history")
        }
        if (old < 4) {
            // 再生リクエスト（playFromMediaId）用にmediaIdカラムを追加
            db.execSQL("ALTER TABLE play_history ADD COLUMN mediaId TEXT NOT NULL DEFAULT ''")
        }
    }
}

class HistoryRepository(context: Context) {

    private val helper = HistoryDbHelper(context.applicationContext)

    // ===== 履歴 =====
    fun queryAll(): List<PlayHistoryEntity> = buildList {
        val c = helper.readableDatabase.rawQuery(
            "SELECT * FROM play_history ORDER BY playedAtMs DESC", null
        )
        while (c.moveToNext()) add(c.toEntity())
        c.close()
    }

    fun queryFrom(fromMs: Long): List<PlayHistoryEntity> = buildList {
        val c = helper.readableDatabase.rawQuery(
            "SELECT * FROM play_history WHERE playedAtMs >= ? ORDER BY playedAtMs DESC",
            arrayOf(fromMs.toString())
        )
        while (c.moveToNext()) add(c.toEntity())
        c.close()
    }

    fun insert(entity: PlayHistoryEntity) {
        val cv = ContentValues().apply {
            put("title",       entity.title)
            put("artist",      entity.artist)
            put("album",       entity.album)
            put("albumArtUri", entity.albumArtUri)
            put("appLabel",    entity.appLabel)
            put("packageName", entity.packageName)
            put("playedAtMs",  entity.playedAtMs)
            put("durationMs",  entity.durationMs)
            put("playTimeMs",  entity.playTimeMs)
            put("listenedMs",  entity.listenedMs)
            put("listenedAll", if (entity.listenedAll) 1 else 0)
            put("mediaId",     entity.mediaId)
        }
        // CONFLICT_REPLACE: 同じ playedAtMs（曲の開始時刻）のレコードは上書き
        // → flushCurrentTrack の仮保存と saveRecord の本保存が重複しない
        helper.writableDatabase.insertWithOnConflict(
            "play_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** 指定開始時刻のレコードの playTimeMs を返す（存在しなければ 0L）*/
    fun getPlayTimeMs(playedAtMs: Long): Long {
        val c = helper.readableDatabase.rawQuery(
            "SELECT playTimeMs FROM play_history WHERE playedAtMs = ?",
            arrayOf(playedAtMs.toString())
        )
        val result = if (c.moveToFirst()) c.getLong(0) else 0L
        c.close()
        return result
    }

    /** 再生時間の合計（ms）*/
    fun getTotalPlayTimeMs(fromMs: Long): Long {
        val c = helper.readableDatabase.rawQuery(
            "SELECT SUM(playTimeMs) FROM play_history WHERE playedAtMs >= ?",
            arrayOf(fromMs.toString())
        )
        val result = if (c.moveToFirst()) c.getLong(0) else 0L
        c.close()
        return result
    }

    /** 曲数 */
    fun getTrackCount(fromMs: Long): Int {
        val c = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM play_history WHERE playedAtMs >= ?",
            arrayOf(fromMs.toString())
        )
        val result = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return result
    }

    fun deleteAll() = helper.writableDatabase.delete("play_history", null, null)

    /** 指定時刻以降の履歴を削除 */
    fun deleteFrom(fromMs: Long) {
        helper.writableDatabase.delete(
            "play_history", "playedAtMs >= ?", arrayOf(fromMs.toString())
        )
    }

    // ===== 除外アプリ =====
    fun getExcludedApps(): List<ExcludedApp> = buildList {
        val c = helper.readableDatabase.rawQuery("SELECT * FROM excluded_apps", null)
        while (c.moveToNext()) {
            add(ExcludedApp(
                packageName = c.getString(c.getColumnIndexOrThrow("packageName")),
                appLabel    = c.getString(c.getColumnIndexOrThrow("appLabel"))
            ))
        }
        c.close()
    }

    fun addExcludedApp(pkg: String, label: String) {
        val cv = ContentValues().apply {
            put("packageName", pkg)
            put("appLabel", label)
        }
        helper.writableDatabase.insertWithOnConflict(
            "excluded_apps", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeExcludedApp(pkg: String) {
        helper.writableDatabase.delete("excluded_apps", "packageName = ?", arrayOf(pkg))
    }

    fun isExcluded(pkg: String): Boolean {
        val c = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM excluded_apps WHERE packageName = ?", arrayOf(pkg)
        )
        val result = c.moveToFirst() && c.getInt(0) > 0
        c.close()
        return result
    }

    private fun android.database.Cursor.toEntity() = PlayHistoryEntity(
        id          = getLong(getColumnIndexOrThrow("id")),
        title       = getString(getColumnIndexOrThrow("title")),
        artist      = getString(getColumnIndexOrThrow("artist")),
        album       = getString(getColumnIndexOrThrow("album")),
        albumArtUri = getString(getColumnIndexOrThrow("albumArtUri")),
        appLabel    = getString(getColumnIndexOrThrow("appLabel")),
        packageName = getString(getColumnIndexOrThrow("packageName")),
        playedAtMs  = getLong(getColumnIndexOrThrow("playedAtMs")),
        durationMs  = getLong(getColumnIndexOrThrow("durationMs")),
        playTimeMs  = getLong(getColumnIndexOrThrow("playTimeMs")),
        listenedMs  = getLong(getColumnIndexOrThrow("listenedMs")),
        listenedAll = getInt(getColumnIndexOrThrow("listenedAll")) == 1,
        mediaId     = getColumnIndex("mediaId").let { idx -> if (idx >= 0) getString(idx) ?: "" else "" }
    )
}