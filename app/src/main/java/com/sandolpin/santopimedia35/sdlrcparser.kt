package com.sandolpin.santopimedia35

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

// ===== sdlrc拡張LyricItem =====
data class SdlrcLine(
    val startMs: Long,
    val endMs: Long?,           // null = 指定なし（次の行まで）
    val text: String,
    val isRight: Boolean = false,   // /r 右寄せ
    val scrollMs: Long? = null,     // /s スクロール開始時間
    val isFast: Boolean = false,    // /f 素早くスクロール
    val groupId: Int = -1,          // 複数同時表示グループ（-1=単独）
    val karaoke: List<KaraokeChar> = emptyList(), // カラオケ文字タイミング
)

data class SdlrcMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

data class SdlrcResult(
    val metadata: SdlrcMetadata,
    val lines: List<SdlrcLine>,
    val source: String = "sdlrc",   // "sdlrc" / "lrc" / "ttml"
    val rawText: String = ""        // 元ファイルの中身（曲と紐づけてキャッシュ保存するため）
)

// ===== sdlrcパーサー =====
object SdlrcParser {

    /**
     * sdlrcテキストをパース
     */
    fun parse(text: String): SdlrcResult {
        val lines = text.lines()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        val result = mutableListOf<SdlrcLine>()
        var groupCounter = 0

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // メタデータ行
            when {
                line.startsWith("Title=", ignoreCase = true) ->
                    title = line.substringAfter("=").trim()
                line.startsWith("Artist=", ignoreCase = true) ->
                    artist = line.substringAfter("=").trim()
                line.startsWith("Album=", ignoreCase = true) ->
                    album = line.substringAfter("=").trim()
                line.startsWith("[") -> {
                    // タイムタグ行をパース
                    val parsed = parseSdlrcLine(line, groupCounter)
                    if (parsed != null) {
                        if (parsed.size > 1) groupCounter++ // 複数タグ=グループ
                        result.addAll(parsed)
                    }
                }
            }
        }

        // startMsでソート
        val sorted = result.sortedBy { it.startMs }
        return SdlrcResult(
            metadata = SdlrcMetadata(title, artist, album),
            lines    = sorted,
            source   = "sdlrc",
            rawText  = text
        )
    }

    // 1行分のタイムタグをパース → 複数行になる場合あり（複数同時表示）
    private fun parseSdlrcLine(raw: String, groupCounter: Int): List<SdlrcLine>? {
        // タグをすべて抽出
        val tagRegex = Regex("""\[([^\]]+)\]""")
        val tags = tagRegex.findAll(raw).map { it.groupValues[1].trim() }.toList()
        if (tags.isEmpty()) return null

        // テキスト部分（[...]以降）
        val rawAfterBracket = raw.substringAfterLast("]")
        // カラオケ構文 <mm:ss.cs>文字</mm:ss.cs> を検出
        val karaoke = parseKaraokeChars(rawAfterBracket)
        val text = if (karaoke.isNotEmpty()) karaoke.joinToString("") { it.char }
        else rawAfterBracket.trim()

        val startTimes = mutableListOf<Long>()
        var endMs: Long? = null
        var isRight  = false
        var scrollMs: Long? = null
        var isFast   = false

        for (tag in tags) {
            when {
                // 終了時間 /00:00.00
                tag.startsWith("/") && tag.contains(":") -> {
                    endMs = parseTimeTag(tag.removePrefix("/").trim())
                }
                // 開始時間（フラグ付き）
                tag.contains(":") || tag.contains(".") -> {
                    // フラグを分離
                    val parts = tag.split(Regex("\\s+"))
                    val timeStr = parts[0]
                    val flags = parts.drop(1)
                    val ms = parseTimeTag(timeStr)
                    if (ms != null) startTimes.add(ms)
                    flags.forEach { f ->
                        when (f.lowercase()) {
                            "/r"  -> isRight  = true
                            "/f"  -> isFast   = true
                            "/s"  -> scrollMs = ms  // /s は直前の時間
                        }
                    }
                }
            }
        }

        if (startTimes.isEmpty()) return null
        if (text.isEmpty() && endMs == null) return null

        val groupId = if (startTimes.size > 1) groupCounter else -1

        return startTimes.map { startMs ->
            SdlrcLine(
                startMs  = startMs,
                endMs    = endMs,
                text     = text,
                isRight  = isRight,
                scrollMs = scrollMs,
                isFast   = isFast,
                groupId  = groupId,
                karaoke  = karaoke,
            )
        }
    }

    /**
     * カラオケ構文パース: <00:01.00>あ</00:01.20>
     * rawText は [タイムタグ] より後ろのテキスト部分
     *
     * カラオケ構文とみなす条件:
     *   "<数字" パターンが存在する（タイムタグの開始）かつパース結果が1件以上
     * 条件を満たさない場合は emptyList() → 通常sdlrc行として処理される
     */
    fun parseKaraokeChars(rawText: String): List<KaraokeChar> {
        // "<数字" パターンがなければカラオケ構文ではない（通常テキストの < を誤検知しない）
        val hasKaraokeTag = rawText.contains("<") && rawText.contains(">") &&
                rawText.indexOfFirst { it == '<' }.let { idx ->
                    idx >= 0 && idx + 1 < rawText.length && rawText[idx + 1].isDigit()
                }
        if (!hasKaraokeTag) return emptyList()
        val result = mutableListOf<KaraokeChar>()
        var currentStartMs: Long? = null
        val currentChar = StringBuilder()
        var pos = 0
        while (pos < rawText.length) {
            if (rawText[pos] == '<') {
                val closeAngle = rawText.indexOf('>', pos)
                if (closeAngle < 0) { currentChar.append(rawText[pos]); pos++; continue }
                val tagContent = rawText.substring(pos + 1, closeAngle)
                val isClose = tagContent.startsWith("/")
                val timeStr = if (isClose) tagContent.substring(1) else tagContent
                val timeMs  = parseTimeTag(timeStr.trim())
                if (timeMs != null) {
                    if (!isClose) {
                        // 開始タグ: 前の文字があれば確定
                        if (currentStartMs != null && currentChar.isNotEmpty()) {
                            result.add(KaraokeChar(currentChar.toString(), currentStartMs, timeMs))
                            currentChar.clear()
                        }
                        currentStartMs = timeMs
                    } else {
                        // 終了タグ: 現在の文字を確定
                        if (currentStartMs != null) {
                            result.add(KaraokeChar(currentChar.toString(), currentStartMs, timeMs))
                            currentChar.clear()
                            currentStartMs = null
                        }
                    }
                } else {
                    // 時刻パース失敗 → そのまま文字として扱う
                    currentChar.append(rawText.substring(pos, closeAngle + 1))
                }
                pos = closeAngle + 1
            } else {
                currentChar.append(rawText[pos])
                pos++
            }
        }
        // 末尾の未確定文字
        if (currentStartMs != null && currentChar.isNotEmpty()) {
            result.add(KaraokeChar(currentChar.toString(), currentStartMs, -1L))
        }
        return result
    }

    // [mm:ss.cs] または [mm:ss:cs] 形式をmsに変換
    private fun parseTimeTag(s: String): Long? {
        return try {
            // mm:ss.cs
            val r1 = Regex("""(\d+):(\d+)[.:](\d+)""")
            r1.matchEntire(s.trim())?.let { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val cs  = m.groupValues[3].let {
                    if (it.length == 2) it.toLong() * 10 else it.toLong()
                }
                min * 60_000 + sec * 1_000 + cs
            }
        } catch (e: Exception) { null }
    }
}

// ===== TTMLパーサー =====
object TtmlParser {

    fun parse(xml: String): SdlrcResult? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            val lines = mutableListOf<SdlrcLine>()
            var title: String? = null
            var artist: String? = null
            var event = parser.next()

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase()) {
                        "title" -> {
                            if (title == null) title = parser.nextText().trim()
                        }
                        "p" -> {
                            val begin = parser.getAttributeValue(null, "begin")
                                ?: parser.getAttributeValue(null, "xml:begin") ?: ""
                            val end = parser.getAttributeValue(null, "end")
                                ?: parser.getAttributeValue(null, "xml:end")
                            val startMs = parseTtmlTime(begin)
                            val endMs   = end?.let { parseTtmlTime(it) }
                            val text    = parser.nextText().trim()
                            if (text.isNotEmpty() && startMs != null) {
                                lines.add(SdlrcLine(
                                    startMs = startMs,
                                    endMs   = endMs,
                                    text    = text
                                ))
                            }
                        }
                        "span" -> {
                            val begin = parser.getAttributeValue(null, "begin") ?: ""
                            val end   = parser.getAttributeValue(null, "end")
                            val startMs = parseTtmlTime(begin)
                            val endMs   = end?.let { parseTtmlTime(it) }
                            val text    = parser.nextText().trim()
                            if (text.isNotEmpty() && startMs != null) {
                                lines.add(SdlrcLine(
                                    startMs = startMs,
                                    endMs   = endMs,
                                    text    = text
                                ))
                            }
                        }
                    }
                }
                event = parser.next()
            }
            SdlrcResult(
                metadata = SdlrcMetadata(title = title, artist = artist),
                lines    = lines.sortedBy { it.startMs },
                source   = "ttml",
                rawText  = xml
            )
        } catch (e: Exception) {
            Log.e("SantopiMedia", "TTML parse error", e)
            null
        }
    }

    // TTML時間 "mm:ss.sss" / "HH:MM:SS.mmm" / "00:00:00.000" をmsに変換
    private fun parseTtmlTime(s: String): Long? {
        return try {
            val parts = s.trim().split(":")
            when (parts.size) {
                3 -> {
                    val h  = parts[0].toLong()
                    val m  = parts[1].toLong()
                    val ss = parts[2].toDouble()
                    ((h * 3600 + m * 60 + ss) * 1000).toLong()
                }
                2 -> {
                    val m  = parts[0].toLong()
                    val ss = parts[1].toDouble()
                    ((m * 60 + ss) * 1000).toLong()
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }
}

// ===== sdlrcをLyricsState(既存)に変換 =====
fun SdlrcResult.toLyricsState(): LyricsState {
    if (lines.isEmpty()) return LyricsState.NotFound

    val lyricItems = mutableListOf<LyricItem>()
    val gapThresholdMs = 7_000L   // 7秒以上の間隔で間奏ドットを表示

    // 間奏判定: 同時表示グループ内の行間は挿入しない
    // → 前の行の endMs を使い、現在行の startMs との差で判定
    var prevLine: SdlrcLine? = null

    for (sdLine in lines) {
        // 間奏検出
        if (prevLine != null) {
            // 前の行の endMs が現在行の startMs より大きい場合は重複表示なので間奏なし
            val prevEnd = prevLine.endMs ?: prevLine.startMs
            val gap = sdLine.startMs - prevEnd
            if (gap >= gapThresholdMs) {
                lyricItems.add(LyricItem.Interlude(
                    startMs = prevEnd,
                    endMs   = sdLine.startMs
                ))
            }
        } else if (sdLine.startMs >= gapThresholdMs) {
            lyricItems.add(LyricItem.Interlude(0L, sdLine.startMs))
        }

        // カラオケ行の末尾文字のendMsを補完（-1の場合、次の行のstartMsか行endMsで補完）
        val completedKaraoke = if (sdLine.karaoke.isNotEmpty()) {
            val lineEndMs = sdLine.endMs ?: sdLine.startMs
            sdLine.karaoke.mapIndexed { i, k ->
                if (k.endMs == -1L) {
                    // 末尾文字: 行のendMsで補完
                    k.copy(endMs = lineEndMs.coerceAtLeast(k.startMs + 200L))
                } else k
            }
        } else emptyList()

        lyricItems.add(LyricItem.Line(
            timeMs  = sdLine.startMs,
            text    = sdLine.text,
            isRight = sdLine.isRight,
            endMs   = sdLine.endMs ?: -1L,
            karaoke = completedKaraoke
        ))
        prevLine = sdLine
    }

    return LyricsState.Synced(lyricItems)
}

// ===== 内部ストレージから歌詞ファイルを読み込む =====
object LyricsFileLoader {

    /**
     * 歌詞ファイルを読み込む
     * folderUriStr が "content://" で始まる場合: SAFフォルダURIとして扱い配下を検索
     * それ以外の場合: 絶対パスとして直接アクセス（/storage/emulated/0/...）
     */
    suspend fun load(
        context: Context,
        folderUriStr: String?,
        title: String,
        artist: String
    ): SdlrcResult? = withContext(Dispatchers.IO) {
        if (folderUriStr.isNullOrBlank()) return@withContext null
        Log.d("SantopiMedia", "LyricsFileLoader: フォルダ=$folderUriStr title=$title artist=$artist")

        return@withContext if (folderUriStr.startsWith("content://")) {
            loadFromSaf(context, folderUriStr, title, artist)
        } else {
            loadFromPath(folderUriStr, title, artist)
        }
    }

    // SAF（Storage Access Framework）経由でフォルダを読む
    private fun loadFromSaf(
        context: Context,
        folderUriStr: String,
        title: String,
        artist: String
    ): SdlrcResult? {
        return try {
            val folderUri = Uri.parse(folderUriStr)
            val folder = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, folderUri)

            if (folder == null || !folder.exists()) {
                Log.e("SantopiMedia", "SAFフォルダが見つかりません: $folderUriStr")
                return null
            }

            Log.d("SantopiMedia", "SAFフォルダ確認OK: ${folder.name}, isDir=${folder.isDirectory}")

            val files = folder.listFiles()
            Log.d("SantopiMedia", "フォルダ内ファイル数: ${files.size}")
            files.forEach { Log.d("SantopiMedia", "  - ${it.name}") }

            val found = findFile(files.toList(), title, artist)
                ?: return null.also { Log.w("SantopiMedia", "一致ファイルなし") }

            Log.d("SantopiMedia", "ファイル発見: ${found.name}")
            val text = context.contentResolver
                .openInputStream(found.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: return null.also { Log.e("SantopiMedia", "ファイル読み取り失敗") }

            parseByExtension(text, found.name?.substringAfterLast(".")?.lowercase() ?: "")
        } catch (e: Exception) {
            Log.e("SantopiMedia", "SAFアクセスエラー", e)
            null
        }
    }

    // 絶対パス経由でフォルダを読む（/storage/emulated/0/...）
    private fun loadFromPath(folderPath: String, title: String, artist: String): SdlrcResult? {
        return try {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                Log.e("SantopiMedia", "パスが見つかりません: $folderPath")
                return null
            }
            val files = folder.listFiles() ?: return null
            Log.d("SantopiMedia", "パスフォルダ内ファイル数: ${files.size}")

            val found = findFileFromFiles(files.toList(), title, artist)
                ?: return null.also { Log.w("SantopiMedia", "一致ファイルなし") }

            Log.d("SantopiMedia", "ファイル発見: ${found.name}")
            val text = found.readText(Charsets.UTF_8)
            parseByExtension(text, found.extension.lowercase())
        } catch (e: Exception) {
            Log.e("SantopiMedia", "パスアクセスエラー", e)
            null
        }
    }

    // DocumentFileのリストからファイルを探す
    private fun findFile(
        files: List<androidx.documentfile.provider.DocumentFile>,
        title: String,
        artist: String
    ): androidx.documentfile.provider.DocumentFile? {
        val exts = listOf("sdlrc", "lrc", "ttml", "xml")
        val bases = listOf(title, "$artist - $title", "$title - $artist", artist + "_" + title)

        // 完全一致優先（拡張子順）
        for (ext in exts) {
            for (base in bases) {
                val name = "$base.$ext"
                files.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.let { return it }
            }
        }
        // タイトルを含む部分一致（拡張子あり）
        return files.firstOrNull { f ->
            val n = f.name?.lowercase() ?: return@firstOrNull false
            val e = n.substringAfterLast(".")
            n.contains(title.lowercase()) && e in exts
        }
    }

    // File のリストからファイルを探す
    private fun findFileFromFiles(files: List<File>, title: String, artist: String): File? {
        val exts = listOf("sdlrc", "lrc", "ttml", "xml")
        val bases = listOf(title, "$artist - $title", "$title - $artist", artist + "_" + title)
        for (ext in exts) {
            for (base in bases) {
                files.firstOrNull { it.name.equals("$base.$ext", ignoreCase = true) }
                    ?.let { return it }
            }
        }
        return files.firstOrNull { f ->
            f.nameWithoutExtension.lowercase().contains(title.lowercase()) &&
                    f.extension.lowercase() in exts
        }
    }

    private fun parseByExtension(text: String, ext: String): SdlrcResult? = when (ext) {
        "sdlrc"       -> SdlrcParser.parse(text)
        "lrc"         -> parseLrcToSdlrc(text)
        "ttml", "xml" -> TtmlParser.parse(text)
        else          -> null
    }

    // LRCをSdlrcResultに変換
    private fun parseLrcToSdlrc(lrc: String): SdlrcResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        val lines = mutableListOf<SdlrcLine>()

        for (raw in lrc.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("[ti:", ignoreCase = true) ->
                    title = line.removePrefix("[ti:").removeSuffix("]").trim()
                line.startsWith("[ar:", ignoreCase = true) ->
                    artist = line.removePrefix("[ar:").removeSuffix("]").trim()
                line.startsWith("[al:", ignoreCase = true) ->
                    album = line.removePrefix("[al:").removeSuffix("]").trim()
                line.startsWith("[") -> {
                    val r1 = Regex("""^\[(\d{2}):(\d{2})[.:](\d{2,3})\](.*)$""")
                    r1.matchEntire(line)?.let { m ->
                        val ms = m.groupValues[3].let {
                            if (it.length == 2) it.toLong() * 10 else it.toLong()
                        }
                        val t = m.groupValues[1].toLong() * 60_000 +
                                m.groupValues[2].toLong() * 1_000 + ms
                        val txt = m.groupValues[4].trim()
                        if (txt.isNotEmpty()) {
                            lines.add(SdlrcLine(startMs = t, endMs = null, text = txt))
                        }
                    }
                }
            }
        }
        return SdlrcResult(
            metadata = SdlrcMetadata(title, artist, album),
            lines    = lines.sortedBy { it.startMs },
            source   = "lrc",
            rawText  = lrc
        )
    }
}

// ===== fetchLyricsWithLocal: ローカル優先で歌詞取得 =====
suspend fun fetchLyricsWithLocal(
    context: Context,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    lyricsFolder: String?,
    autoRetry: Boolean = true,
    useNetwork: Boolean = true   // falseならローカルファイルのみ検索
): LyricsState = withContext(Dispatchers.IO) {
    LyricsCache.init(context)

    // 1. まずキャッシュを確認する（最優先）
    //    ローカルファイル由来の生データ・LRCLIB由来のデータ・スキップフラグ、
    //    いずれもここで一括して判定できる。
    //    フォルダ探索（特にSAF経由は1ファイルずつ問い合わせが発生し遅い）や
    //    ネットワーク通信より、DBの単純なキー検索は圧倒的に高速なため、
    //    「毎回フォルダを走査してからキャッシュを見る」という従来の無駄な順序をやめ、
    //    キャッシュがあれば他の処理を一切せず即座に返す。
    val cached = LyricsCache.load(title, artist)
    if (cached != null) {
        Log.d("SantopiMedia", "歌詞: キャッシュヒット（高速読み込み）")
        return@withContext cached
    }

    // 2. ローカルフォルダ内のファイルを検索
    val local = LyricsFileLoader.load(context, lyricsFolder, title, artist)
    if (local != null && local.lines.isNotEmpty()) {
        Log.d("SantopiMedia", "歌詞: ローカルファイル使用 (${local.source})")
        // 次回は高速なキャッシュ経由で読み込めるよう、生データを曲に紐づけて保存
        if (local.rawText.isNotBlank()) {
            LyricsCache.saveRaw(title, artist, local.rawText, local.source)
        }
        return@withContext local.toLyricsState()
    }

    // 3. ネットワーク無効なら NotFound を返す
    if (!useNetwork) {
        Log.d("SantopiMedia", "歌詞: ネットワーク検索無効のためNotFound")
        return@withContext LyricsState.NotFound
    }

    // 4. フォールバック: LRCLIB API
    Log.d("SantopiMedia", "歌詞: LRCLIBにフォールバック")
    return@withContext fetchLyrics(
        title      = title,
        artist     = artist,
        album      = album,
        durationMs = durationMs,
        autoRetry  = autoRetry,
        context    = context
    )
}