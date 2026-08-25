package com.sandolpin.santopimedia35

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ===== データクラス =====

/** カラオケ1文字分のタイミング */
data class KaraokeChar(
    val char: String,      // 文字（1文字 or 結合文字）
    val startMs: Long,     // 表示開始ms
    val endMs: Long        // 表示終了ms（次の文字のstartMs）
)

sealed class LyricItem {
    data class Line(
        val timeMs: Long,
        val text: String,
        val isRight: Boolean = false,  // sdlrc /r フラグ: 右寄せ表示
        val endMs: Long = -1L,         // sdlrc /end 終了時刻(-1=未指定/lrc行)
        val karaoke: List<KaraokeChar> = emptyList() // カラオケ文字タイミング（空=通常行）
    ) : LyricItem()
    data class Interlude(val startMs: Long, val endMs: Long) : LyricItem()
}

/** カラオケ行かどうか */
val LyricItem.Line.isKaraoke: Boolean get() = karaoke.isNotEmpty()

data class LyricLine(val timeMs: Long, val text: String)

data class SearchResultItem(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val hasSynced: Boolean
)

sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Synced(val items: List<LyricItem>) : LyricsState()
    data class Plain(val lines: List<LyricLine>) : LyricsState()
    object NotFound : LyricsState()
    data class SearchResults(val results: List<SearchResultItem>) : LyricsState()
    data class Error(val message: String) : LyricsState()
}

// ===== 歌詞キャッシュDB =====

/**
 * lyrics_cache テーブル:
 *   key         = "${title}|${artist}"
 *   syncedLyrics = LRC文字列 or NULL
 *   plainLyrics  = プレーンテキスト or NULL
 *   savedAt      = 保存時刻(ms)
 */
private class LyricsCacheDbHelper(context: Context) :
    SQLiteOpenHelper(context, "santopi_lyrics_cache.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                key          TEXT PRIMARY KEY,
                syncedLyrics TEXT,
                plainLyrics  TEXT,
                rawContent   TEXT,
                rawFormat    TEXT,
                savedAt      INTEGER NOT NULL
            )
        """.trimIndent())
    }

    // rawContent/rawFormat 列を追加（バージョン1→2）。
    // 歌詞キャッシュは再取得可能なデータのため、破壊的マイグレーション（作り直し）で問題ない。
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS lyrics_cache")
        onCreate(db)
    }
}

object LyricsCache {

    private var helper: LyricsCacheDbHelper? = null

    fun init(context: Context) {
        if (helper == null) helper = LyricsCacheDbHelper(context.applicationContext)
    }

    /** キャッシュから読み込む（存在しなければnull）
     *  rawContent（ローカルファイル由来の生テキスト）があれば最優先で使う。
     *  sdlrc/ttml独自の情報（カラオケ・右寄せ・複数同時表示など）を
     *  syncedLyrics(LRC形式)に変換すると失われてしまうため、
     *  元の形式のまま保存しておいた生データを毎回パースし直すことで復元する。
     */
    fun load(title: String, artist: String): LyricsState? {
        val h = helper ?: return null
        val key = cacheKey(title, artist)
        val cursor = h.readableDatabase.query(
            "lyrics_cache", null,
            "key = ?", arrayOf(key),
            null, null, null
        )
        return try {
            if (!cursor.moveToFirst()) return null
            val synced = cursor.getString(cursor.getColumnIndexOrThrow("syncedLyrics"))
            val plain  = cursor.getString(cursor.getColumnIndexOrThrow("plainLyrics"))
            val rawContent = cursor.getStringOrNull("rawContent")
            val rawFormat  = cursor.getStringOrNull("rawFormat")
            android.util.Log.d("SantopiMedia", "LyricsCache: hit for $key")
            buildStateFromRaw(synced, plain, rawContent, rawFormat)
        } catch (e: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    // 列が存在しない/NULLの場合に安全にnullを返すヘルパー
    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    /** キャッシュに保存（LRCLIB等のJSONレスポンスをそのまま格納）
     *  rawContent/rawFormat（ファイル由来の生データ）は上書きしてクリアする。
     */
    fun save(title: String, artist: String, syncedLyrics: String?, plainLyrics: String?) {
        val h = helper ?: return
        val key = cacheKey(title, artist)
        val cv = ContentValues().apply {
            put("key", key)
            put("syncedLyrics", syncedLyrics)
            put("plainLyrics", plainLyrics)
            put("savedAt", System.currentTimeMillis())
        }
        h.writableDatabase.insertWithOnConflict(
            "lyrics_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
        android.util.Log.d("SantopiMedia", "LyricsCache: saved for $key")
    }

    /**
     * ローカルファイルから読み込んだ歌詞の「生データ」を曲(title+artist)に紐づけて保存する。
     * 次回同じ曲を開いたときは、フォルダを探しに行かずに済み、
     * この生データを再パースするだけで同じ歌詞（カラオケ・右寄せ等も含む）を復元できる。
     *
     * @param rawContent ファイルの中身そのもの（.sdlrc/.lrc/.ttml/.xml のテキスト）
     * @param format     "sdlrc" / "lrc" / "ttml" のいずれか（再パース時にパーサーを選ぶため）
     */
    fun saveRaw(title: String, artist: String, rawContent: String, format: String) {
        val h = helper ?: return
        val key = cacheKey(title, artist)
        val cv = ContentValues().apply {
            put("key", key)
            put("rawContent", rawContent)
            put("rawFormat", format)
            put("savedAt", System.currentTimeMillis())
        }
        h.writableDatabase.insertWithOnConflict(
            "lyrics_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
        android.util.Log.d("SantopiMedia", "LyricsCache: saved raw($format) for $key")
    }

    /** キャッシュを削除（特定曲）*/
    fun delete(title: String, artist: String) {
        val h = helper ?: return
        h.writableDatabase.delete("lyrics_cache", "key = ?", arrayOf(cacheKey(title, artist)))
    }

    /** キャッシュを全削除 */
    fun deleteAll() {
        val h = helper ?: return
        h.writableDatabase.delete("lyrics_cache", null, null)
    }

    /** この曲はAPI検索しないフラグを保存 */
    fun saveSkip(title: String, artist: String) {
        save(title, artist, syncedLyrics = null, plainLyrics = "__skip__")
    }

    /** スキップフラグが立っているか確認 */
    fun isSkipped(title: String, artist: String): Boolean {
        val h = helper ?: return false
        val key = cacheKey(title, artist)
        val cursor = h.readableDatabase.query(
            "lyrics_cache", arrayOf("plainLyrics"),
            "key = ?", arrayOf(key), null, null, null
        )
        return try {
            cursor.moveToFirst() && cursor.getString(0) == "__skip__"
        } finally { cursor.close() }
    }

    /** キャッシュの件数 */
    fun count(): Int {
        val h = helper ?: return 0
        val cursor = h.readableDatabase.rawQuery("SELECT COUNT(*) FROM lyrics_cache", null)
        return try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally { cursor.close() }
    }

    private fun cacheKey(title: String, artist: String) = "$title|$artist"

    private fun buildStateFromRaw(
        synced: String?,
        plain: String?,
        rawContent: String? = null,
        rawFormat: String? = null
    ): LyricsState? {
        // スキップフラグ・（旧バージョンの）ローカルファイルフラグはNotFound扱い
        if (plain == "__skip__" || plain == "__local_file__") return LyricsState.NotFound

        // ローカルファイル由来の生データがあれば最優先で元の形式のままパースし直す
        // （sdlrc独自のカラオケ・右寄せ・複数同時表示の情報を保持するため）
        if (!rawContent.isNullOrBlank()) {
            when (rawFormat) {
                "sdlrc" -> {
                    val result = SdlrcParser.parse(rawContent)
                    if (result.lines.isNotEmpty()) return result.toLyricsState()
                }
                "ttml", "xml" -> {
                    val result = TtmlParser.parse(rawContent)
                    if (result != null && result.lines.isNotEmpty()) return result.toLyricsState()
                }
                "lrc" -> {
                    val lines = parseLrc(rawContent)
                    if (lines.isNotEmpty()) return LyricsState.Synced(buildLyricItems(lines))
                }
            }
        }

        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return LyricsState.Synced(buildLyricItems(lines))
        }
        if (!plain.isNullOrBlank()) {
            val lines = plain.split("\n").map { LyricLine(-1L, it) }
            return LyricsState.Plain(lines)
        }
        return null
    }
}

// ===== API関数 =====

/**
 * 歌詞取得（キャッシュ優先）
 * 1. キャッシュを確認 → あれば即返す
 * 2. /api/get で取得 → 成功したらキャッシュ保存
 * 3. 失敗したら searchLyrics で再試行
 * 4. autoRetry=true かつ見つからない場合、タイトルのみで searchLyricsWithResults
 */
suspend fun fetchLyrics(
    title: String, artist: String, album: String,
    durationMs: Long, autoRetry: Boolean = true,
    context: Context? = null
): LyricsState = withContext(Dispatchers.IO) {
    // コンテキストがある場合キャッシュを初期化
    context?.let { LyricsCache.init(it) }

    // 1. キャッシュ確認
    val cached = LyricsCache.load(title, artist)
    if (cached != null) return@withContext cached

    try {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val durSec = (durationMs / 1000).coerceAtLeast(0)
        val url = "https://lrclib.net/api/get" +
                "?track_name=${enc(title)}&artist_name=${enc(artist)}" +
                "&album_name=${enc(album)}&duration=$durSec"
        android.util.Log.d("SantopiMedia", "fetchLyrics: $url")
        val resp = httpGet(url)

        if (resp == null) {
            val searchResult = searchLyrics(title, artist, context)
            if (searchResult is LyricsState.NotFound && autoRetry) {
                searchLyricsWithResults(title, "")
            } else searchResult
        } else {
            val parsed = parseLyricsResponseAndCache(resp, title, artist)
            if (parsed is LyricsState.NotFound && autoRetry) {
                searchLyricsWithResults(title, "")
            } else parsed
        }
    } catch (e: Exception) {
        android.util.Log.e("SantopiMedia", "fetchLyrics error", e)
        LyricsState.Error(e.message ?: "不明なエラー")
    }
}

suspend fun fetchLyricsById(id: Int, title: String = "", artist: String = ""): LyricsState =
    withContext(Dispatchers.IO) {
        try {
            val resp = httpGet("https://lrclib.net/api/get/$id")
                ?: return@withContext LyricsState.NotFound
            // IDで取得した場合もキャッシュ保存
            if (title.isNotEmpty()) {
                parseLyricsResponseAndCache(resp, title, artist)
            } else {
                parseLyricsResponse(resp)
            }
        } catch (e: Exception) { LyricsState.NotFound }
    }

suspend fun searchLyrics(
    title: String, artist: String,
    context: Context? = null
): LyricsState = withContext(Dispatchers.IO) {
    context?.let { LyricsCache.init(it) }
    // キャッシュ確認
    val cached = LyricsCache.load(title, artist)
    if (cached != null) return@withContext cached

    try {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val resp = httpGet(
            "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        ) ?: return@withContext LyricsState.NotFound
        val arr = JSONArray(resp)
        if (arr.length() == 0) return@withContext LyricsState.NotFound
        parseLyricsResponseAndCache(arr.getJSONObject(0).toString(), title, artist)
    } catch (e: Exception) { LyricsState.NotFound }
}

suspend fun searchLyricsWithResults(title: String, artist: String): LyricsState =
    withContext(Dispatchers.IO) {
        try {
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            val query = if (artist.isNotEmpty())
                "track_name=${enc(title)}&artist_name=${enc(artist)}"
            else "track_name=${enc(title)}"
            val resp = httpGet("https://lrclib.net/api/search?$query")
                ?: return@withContext LyricsState.NotFound
            val arr = JSONArray(resp)
            if (arr.length() == 0) return@withContext LyricsState.NotFound
            val results = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SearchResultItem(
                    id        = obj.optInt("id"),
                    title     = obj.optString("trackName", ""),
                    artist    = obj.optString("artistName", ""),
                    album     = obj.optString("albumName", ""),
                    duration  = obj.optInt("duration", 0),
                    hasSynced = !obj.isNull("syncedLyrics") &&
                            obj.optString("syncedLyrics").isNotEmpty()
                )
            }
            LyricsState.SearchResults(results)
        } catch (e: Exception) { LyricsState.NotFound }
    }

// ===== パース =====

/** パースしてキャッシュにも保存 */
fun parseLyricsResponseAndCache(json: String, title: String, artist: String): LyricsState {
    val obj = JSONObject(json)
    val synced = if (obj.has("syncedLyrics") && !obj.isNull("syncedLyrics"))
        obj.getString("syncedLyrics") else null
    val plain = if (obj.has("plainLyrics") && !obj.isNull("plainLyrics"))
        obj.getString("plainLyrics") else null

    // キャッシュ保存
    if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
        LyricsCache.save(title, artist, synced, plain)
    }

    return parseLyricsResponseFromRaw(synced, plain)
}

fun parseLyricsResponse(json: String): LyricsState {
    val obj = JSONObject(json)
    val synced = if (obj.has("syncedLyrics") && !obj.isNull("syncedLyrics"))
        obj.getString("syncedLyrics") else null
    val plain = if (obj.has("plainLyrics") && !obj.isNull("plainLyrics"))
        obj.getString("plainLyrics") else null
    return parseLyricsResponseFromRaw(synced, plain)
}

private fun parseLyricsResponseFromRaw(synced: String?, plain: String?): LyricsState {
    if (!synced.isNullOrBlank()) {
        val lines = parseLrc(synced)
        if (lines.isNotEmpty()) return LyricsState.Synced(buildLyricItems(lines))
    }
    if (!plain.isNullOrBlank()) {
        val lines = plain.split("\n").map { LyricLine(-1L, it) }
        return LyricsState.Plain(lines)
    }
    return LyricsState.NotFound
}

// lrc形式用: Interludeは生成しない（間奏ドットはsdlrc専用）
fun buildLyricItems(lines: List<LyricLine>): List<LyricItem> {
    return lines.map { LyricItem.Line(it.timeMs, it.text) }
}

fun parseLrc(lrc: String): List<LyricLine> {
    val r1 = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")
    val r2 = Regex("""^\[(\d{2}):(\d{2})\](.*)$""")
    val result = mutableListOf<LyricLine>()
    for (raw in lrc.split("\n")) {
        val line = raw.trim()
        if (line.matches(Regex("""^\[[a-zA-Z]+:.*\]$"""))) continue
        r1.matchEntire(line)?.let { m ->
            val ms = m.groupValues[3].let {
                if (it.length == 2) it.toLong() * 10 else it.toLong()
            }
            val t = m.groupValues[1].toLong() * 60_000 +
                    m.groupValues[2].toLong() * 1_000 + ms
            val txt = m.groupValues[4].trim()
            if (txt.isNotEmpty()) result.add(LyricLine(t, txt))
            return@let
        }
        r2.matchEntire(line)?.let { m ->
            val t = m.groupValues[1].toLong() * 60_000 +
                    m.groupValues[2].toLong() * 1_000
            val txt = m.groupValues[3].trim()
            if (txt.isNotEmpty()) result.add(LyricLine(t, txt))
        }
    }
    return result.sortedBy { it.timeMs }
}

fun httpGet(urlStr: String): String? {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", "さんとぴメディアコントロール/1.0 (Android)")
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    return try {
        conn.connect()
        if (conn.responseCode == 404) return null
        if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader().readText()
    } finally {
        conn.disconnect()
    }
}