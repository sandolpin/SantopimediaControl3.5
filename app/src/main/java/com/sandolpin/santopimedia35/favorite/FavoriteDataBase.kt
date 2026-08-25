package com.sandolpin.santopimedia35.favorite

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * お気に入り登録された曲。
 * key（title|artist|packageName）で一意に管理し、同じ曲の重複登録を防ぐ。
 */
data class FavoriteTrackEntity(
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String?,
    val appLabel: String,
    val packageName: String,
    val mediaId: String,        // 再生リクエスト用（空="不明"）
    val durationMs: Long,
    val addedAtMs: Long          // お気に入りに追加した時刻（並び順用）
)

private class FavoriteDbHelper(context: Context) :
    SQLiteOpenHelper(context, "santopi_favorite.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorite_tracks (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                trackKey    TEXT NOT NULL UNIQUE,
                title       TEXT NOT NULL,
                artist      TEXT NOT NULL,
                album       TEXT NOT NULL,
                albumArtUri TEXT,
                appLabel    TEXT NOT NULL,
                packageName TEXT NOT NULL,
                mediaId     TEXT NOT NULL DEFAULT '',
                durationMs  INTEGER NOT NULL DEFAULT 0,
                addedAtMs   INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // 初版のため未使用（将来のスキーマ変更用）
    }
}

class FavoriteRepository(context: Context) {

    private val helper = FavoriteDbHelper(context.applicationContext)

    private fun trackKey(title: String, artist: String, packageName: String) =
        "$title|$artist|$packageName"

    fun queryAll(): List<FavoriteTrackEntity> = buildList {
        val c = helper.readableDatabase.rawQuery(
            "SELECT * FROM favorite_tracks ORDER BY addedAtMs DESC", null
        )
        while (c.moveToNext()) add(c.toEntity())
        c.close()
    }

    fun isFavorite(title: String, artist: String, packageName: String): Boolean {
        val key = trackKey(title, artist, packageName)
        val c = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM favorite_tracks WHERE trackKey = ?", arrayOf(key)
        )
        val result = c.moveToFirst() && c.getInt(0) > 0
        c.close()
        return result
    }

    fun add(
        title: String,
        artist: String,
        album: String,
        albumArtUri: String?,
        appLabel: String,
        packageName: String,
        mediaId: String,
        durationMs: Long
    ) {
        val cv = ContentValues().apply {
            put("trackKey",    trackKey(title, artist, packageName))
            put("title",       title)
            put("artist",      artist)
            put("album",       album)
            put("albumArtUri", albumArtUri)
            put("appLabel",    appLabel)
            put("packageName", packageName)
            put("mediaId",     mediaId)
            put("durationMs",  durationMs)
            put("addedAtMs",   System.currentTimeMillis())
        }
        // 既に登録済みなら無視（IGNORE）→ addedAtMsが上書きされず元の追加日時を保持
        helper.writableDatabase.insertWithOnConflict(
            "favorite_tracks", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun remove(title: String, artist: String, packageName: String) {
        val key = trackKey(title, artist, packageName)
        helper.writableDatabase.delete("favorite_tracks", "trackKey = ?", arrayOf(key))
    }

    private fun android.database.Cursor.toEntity() = FavoriteTrackEntity(
        id          = getLong(getColumnIndexOrThrow("id")),
        title       = getString(getColumnIndexOrThrow("title")),
        artist      = getString(getColumnIndexOrThrow("artist")),
        album       = getString(getColumnIndexOrThrow("album")),
        albumArtUri = getString(getColumnIndexOrThrow("albumArtUri")),
        appLabel    = getString(getColumnIndexOrThrow("appLabel")),
        packageName = getString(getColumnIndexOrThrow("packageName")),
        mediaId     = getString(getColumnIndexOrThrow("mediaId")) ?: "",
        durationMs  = getLong(getColumnIndexOrThrow("durationMs")),
        addedAtMs   = getLong(getColumnIndexOrThrow("addedAtMs"))
    )
}