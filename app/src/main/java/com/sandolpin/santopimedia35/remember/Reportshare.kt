package com.sandolpin.santopimedia35.remember

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

// =============================================================================
// Coilで画像URIから同期的にBitmapを取得するヘルパー
// 通常の AsyncImage は「後から届いたら再描画」の非同期モデルだが、
// Canvas への一括描画では「事前に全部のBitmapが揃っている」必要があるため、
// ImageLoader.execute() で suspend 待機してBitmapを取得する。
// 取得失敗（URIなし・読み込みエラー）時は null を返し、呼び出し側でフォールバック描画する。
// =============================================================================
private suspend fun loadBitmapFromUri(context: Context, uri: String?): Bitmap? {
    if (uri.isNullOrEmpty()) return null
    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Canvasへの直接描画にはソフトウェアBitmapが必要
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: result.drawable.toBitmapCompat()
        } else null
    } catch (e: Exception) {
        android.util.Log.e("ReportShare", "アルバムアート読み込み失敗: $uri", e)
        null
    }
}

// Drawable → Bitmap 汎用変換（BitmapDrawable以外のケース用フォールバック）
private fun android.graphics.drawable.Drawable.toBitmapCompat(): Bitmap {
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

// =============================================================================
// シェア対象の期間種別
// =============================================================================
enum class ShareKind { TODAY, WEEK, MONTH }

// =============================================================================
// アルバムランキング集計
// =============================================================================
data class AlbumRankItem(
    val album: String,
    val artist: String,
    val albumArtUri: String?,
    val playTimeMs: Long
)

private fun computeAlbumRanking(history: List<PlayHistoryEntity>): List<AlbumRankItem> {
    data class Acc(var time: Long, var artist: String, var artUri: String?)
    val map = mutableMapOf<String, Acc>()
    for (e in history) {
        val key = e.album.ifEmpty { "不明アルバム" }
        val acc = map.getOrPut(key) { Acc(0L, e.artist.ifEmpty { "不明" }, null) }
        acc.time += e.playTimeMs
        if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
    }
    return map.entries.sortedByDescending { it.value.time }
        .map { AlbumRankItem(it.key, it.value.artist, it.value.artUri, it.value.time) }
}

// =============================================================================
// シェア用データ一式（画像生成に必要な情報をまとめたもの）
// =============================================================================
data class ShareReportData(
    val kind: ShareKind,
    val dateRangeLabel: String,     // "6/23-6/30" や "6月30日" など
    val totalPlayMs: Long,
    val totalCount: Int,
    val barEntries: List<BarEntry>,
    val artistRanking: List<ArtistRankItem>,  // 週/月用
    val albumRanking: List<AlbumRankItem>,    // きょう用
    val pieEntries: List<PieEntry>,
    val topTracks: List<TrackRankItem> = emptyList()  // きょう用: 再生回数トップ3（棒グラフの代わりに表示）
)

/** ShareKind に応じて期間を組み立て、画像生成に必要な全データを集計する */
suspend fun buildShareReportData(
    viewModel: HistoryViewModel,
    kind: ShareKind
): ShareReportData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val allHistory = viewModel.getAllHistory()

    val startMs: Long
    val endMs: Long
    val rangeLabel: String

    when (kind) {
        ShareKind.TODAY -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            startMs = cal.timeInMillis
            endMs = startMs + 24 * 60 * 60 * 1000L - 1L
            rangeLabel = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        }
        ShareKind.WEEK -> {
            val p = ReportPeriod.Week(0)
            startMs = p.startMs(); endMs = p.endMs()
            rangeLabel = weekRangeLabelForShare(0)
        }
        ShareKind.MONTH -> {
            val p = ReportPeriod.Month(0)
            startMs = p.startMs(); endMs = p.endMs()
            val cal = Calendar.getInstance()
            rangeLabel = "${cal.get(Calendar.MONTH) + 1}月"
        }
    }

    val filtered = allHistory.filter { it.playedAtMs in startMs..endMs }
    val totalPlayMs = filtered.sumOf { it.playTimeMs }
    val totalCount = filtered.size

    val barEntries = when (kind) {
        ShareKind.TODAY -> buildWeekBars(allHistory, 0)   // きょうを含む週の棒グラフを「直近で聴いた曲」枠に使用
        ShareKind.WEEK  -> buildWeekBars(allHistory, 0)
        ShareKind.MONTH -> buildMonthBars(allHistory, 0)
    }

    val ranking = computeReportRanking(filtered)
    val albumRanking = computeAlbumRanking(filtered)
    // computeReportRanking の tracks は既に再生回数の多い順にソート済み
    val topTracks = if (kind == ShareKind.TODAY) ranking.tracks.take(3) else emptyList()

    val appMap = mutableMapOf<String, Pair<String, Long>>()
    for (e in filtered) {
        val prev = appMap[e.packageName]?.second ?: 0L
        appMap[e.packageName] = Pair(e.appLabel.ifEmpty { e.packageName }, prev + e.playTimeMs)
    }
    val pieEntries = appMap.entries.sortedByDescending { it.value.second }
        .mapIndexed { i, entry ->
            PieEntry(label = entry.value.first, packageName = entry.key,
                valueMs = entry.value.second, color = PIE_COLORS[i % PIE_COLORS.size])
        }

    ShareReportData(
        kind = kind,
        dateRangeLabel = rangeLabel,
        totalPlayMs = totalPlayMs,
        totalCount = totalCount,
        barEntries = barEntries,
        artistRanking = ranking.artists,
        albumRanking = albumRanking,
        pieEntries = pieEntries,
        topTracks = topTracks
    )
}

// "6/23-6/30" のようなハイフン区切りラベル（weekRangeLabelは "〜" 区切りなのでシェア画像向けに別途用意）
private fun weekRangeLabelForShare(weekOffset: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val end = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    return "$start-$end"
}

// =============================================================================
// 画像生成＆シェアのエントリポイント
// ComposeView はウィンドウへのアタッチが必要でオフスクリーン描画に使えないため、
// android.graphics.Canvas で直接描画する方式を採用している。
// =============================================================================
@Composable
fun rememberShareImageLauncher(): suspend (ShareReportData) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    return { data ->
        val fileName = "report_${data.kind.name.lowercase()}_${System.currentTimeMillis()}.png"
        shareReportDataAsImage(context, data, fileName)
    }
}

suspend fun shareReportDataAsImage(context: Context, data: ShareReportData, fileName: String) {
    try {
        val density = context.resources.displayMetrics.density
        val bitmap  = drawReportBitmap(context, data, density)
        val file    = saveBitmapToCache(context, bitmap, fileName)
        val uri     = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "レポートをシェア").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        android.util.Log.e("ReportShare", "シェア画像の生成に失敗", e)
    }
}

// =============================================================================
// android.graphics.Canvas で直接レポートカードを描画して Bitmap を返す
// =============================================================================
private suspend fun drawReportBitmap(
    context: Context,
    data: ShareReportData,
    density: Float
): Bitmap {
    // ---- TODAY用: 曲カードのアルバムアートを先にすべて読み込んでおく ----
    // Canvas描画は同期処理のため、非同期のCoil読み込みは描画開始前に完了させておく必要がある。
    // インデックス対応のMapに保持し、後段の描画ループで参照する。
    val trackArtBitmaps: Map<Int, Bitmap?> = if (data.kind == ShareKind.TODAY) {
        data.topTracks.take(3).mapIndexed { i, track ->
            i to loadBitmapFromUri(context, track.albumArtUri)
        }.toMap()
    } else emptyMap()

    // dp → px 変換ヘルパー
    fun Float.dp() = (this * density).toInt()
    fun Int.dp()   = (this * density).toInt()

    // テキスト描画用 Paint ファクトリ
    fun textPaint(
        sizeSp: Float,
        color: Int = android.graphics.Color.BLACK,
        bold: Boolean = false,
        align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT
    ) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize   = sizeSp * density          // sp≈dp（シェア画像では簡略化）
        this.color = color
        isFakeBoldText = bold
        textAlign  = align
        typeface   = if (bold) android.graphics.Typeface.DEFAULT_BOLD
        else android.graphics.Typeface.DEFAULT
    }

    fun rectPaint(color: Int) =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.FILL
        }

    fun strokePaint(color: Int, strokePx: Float) =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = strokePx
        }

    val canvasWidthPx  = 390.dp()
    val paddingH       = 20.dp()
    val contentWidth   = canvasWidthPx - paddingH * 2
    val cardRadius     = 14.dp().toFloat()
    val cardPadding    = 16.dp()
    val sectionGap     = 14.dp()

    // ---- テキスト内容 ----
    val headerRight = when (data.kind) {
        ShareKind.TODAY -> data.dateRangeLabel
        ShareKind.WEEK  -> "1週間 (${data.dateRangeLabel})"
        ShareKind.MONTH -> "1ヶ月 (${data.dateRangeLabel})"
    }
    val timeLabel  = if (data.kind == ShareKind.TODAY) "きょう聴いた時間" else "今週聴いた時間"
    val countLabel = if (data.kind == ShareKind.TODAY) "きょう聴いた曲数" else "今週聴いた曲数"
    val graphTitle = if (data.kind == ShareKind.TODAY) "直近で聴いた曲"   else "聴いた時間"
    val rankingTitle = if (data.kind == ShareKind.TODAY) "アルバムランキング" else "よく聴いたアーティスト"

    // ---- 各セクション高さ計算（事前にcanvasHighを決定するため）----
    val headerH    = 40.dp()
    val gap1       = 16.dp()
    // サマリーカード: ラベル行2本 + 上下padding
    val summaryInH = 13.dp() + 8.dp() + 13.dp() + cardPadding * 2
    // グラフカード:
    //  - WEEK/MONTH: タイトル行 + 棒グラフ110dp + 上下padding（従来通り）
    //  - TODAY     : タイトル行 + 曲カード(HistoryCard風)を3枚 縦積み + 上下padding
    val trackCardH = 64.dp()          // 1曲ぶんのカード高さ（アート56dp + 余白）
    val trackCardGap = 8.dp()
    val topTrackCount = data.topTracks.size.coerceAtMost(3)
    val graphInH = if (data.kind == ShareKind.TODAY) {
        val cardsH = if (topTrackCount > 0)
            trackCardH * topTrackCount + trackCardGap * (topTrackCount - 1)
        else 60.dp() // データなし時の最低高さ
        15.dp() + 12.dp() + cardsH + cardPadding * 2
    } else {
        15.dp() + 12.dp() + 110.dp() + cardPadding * 2
    }
    // ランキングカード: タイトル + 行3本(各44dp) + 上下padding
    val rankingItemH = 44.dp()
    val rankCount = if (data.kind == ShareKind.TODAY) data.albumRanking.size.coerceAtMost(3)
    else data.artistRanking.size.coerceAtMost(3)
    val rankingInH = 15.dp() + 10.dp() + rankingItemH * rankCount.coerceAtLeast(1) + cardPadding * 2
    // 円グラフカード: タイトル + 円グラフ90dp + 上下padding
    val pieCount = data.pieEntries.size.coerceAtMost(4)
    val pieInH   = 15.dp() + 12.dp() + 90.dp().coerceAtLeast(pieCount * 22.dp()) + cardPadding * 2
    val footerH  = 12.dp() + 14.dp()

    val totalH = 20.dp() + headerH + gap1 +
            summaryInH + sectionGap +
            graphInH   + sectionGap +
            rankingInH + sectionGap +
            pieInH     + footerH + 20.dp()

    val bitmap = Bitmap.createBitmap(canvasWidthPx, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    var y = 20.dp().toFloat()

    // ---- ヘッダー ----
    canvas.drawText(
        "レポート",
        paddingH.toFloat(), y + 28.dp(),
        textPaint(24f, bold = true)
    )
    val rightP = textPaint(13f, android.graphics.Color.argb(180, 0, 0, 0),
        align = android.graphics.Paint.Align.RIGHT)
    canvas.drawText(
        headerRight,
        (canvasWidthPx - paddingH).toFloat(), y + 22.dp(), rightP
    )
    y += headerH + gap1

    // ---- カード描画ヘルパー（丸角の枠） ----
    fun drawCard(top: Float, height: Int): android.graphics.RectF {
        val rect = android.graphics.RectF(
            paddingH.toFloat(), top,
            (canvasWidthPx - paddingH).toFloat(), top + height
        )
        canvas.drawRoundRect(rect, cardRadius, cardRadius,
            rectPaint(android.graphics.Color.WHITE))
        canvas.drawRoundRect(rect, cardRadius, cardRadius,
            strokePaint(android.graphics.Color.argb(38, 0, 0, 0), 1.dp().toFloat()))
        return rect
    }

    fun drawKeyValue(label: String, value: String, cardLeft: Float, cardRight: Float, cy: Float) {
        canvas.drawText(label, cardLeft + cardPadding, cy,
            textPaint(13f, android.graphics.Color.argb(153, 0, 0, 0)))
        canvas.drawText(value, cardRight - cardPadding, cy,
            textPaint(17f, bold = true,
                align = android.graphics.Paint.Align.RIGHT))
    }

    // ---- サマリーカード ----
    drawCard(y, summaryInH)
    val cl = paddingH.toFloat(); val cr = (canvasWidthPx - paddingH).toFloat()
    drawKeyValue(timeLabel, formatListenedTime(data.totalPlayMs),
        cl, cr, y + cardPadding + 13.dp())
    drawKeyValue(countLabel, "${data.totalCount}曲",
        cl, cr, y + cardPadding + 13.dp() + 8.dp() + 13.dp())
    y += summaryInH + sectionGap

    // ---- グラフカード（TODAYは曲カード3枚、WEEK/MONTHは棒グラフ）----
    drawCard(y, graphInH)
    canvas.drawText(graphTitle, cl + cardPadding, y + cardPadding + 15.dp(),
        textPaint(15f, bold = true))
    canvas.drawText(data.dateRangeLabel,
        cr - cardPadding, y + cardPadding + 14.dp(),
        textPaint(12f, android.graphics.Color.argb(128, 0, 0, 0),
            align = android.graphics.Paint.Align.RIGHT))

    if (data.kind == ShareKind.TODAY) {
        // 曲カード（HistoryCard風・再生回数の多い順トップ3）を縦に並べる
        val listTop   = y + cardPadding + 15.dp() + 12.dp()
        val cardLeft  = cl + cardPadding
        val cardWidth = (cr - cardPadding) - cardLeft
        if (topTrackCount == 0) {
            canvas.drawText("データなし", cardLeft, listTop + 20.dp(),
                textPaint(13f, android.graphics.Color.argb(102, 0, 0, 0)))
        } else {
            data.topTracks.take(3).forEachIndexed { i, track ->
                val cardTop = listTop + (trackCardH + trackCardGap) * i
                drawTrackCard(
                    canvas    = canvas,
                    title     = track.title,
                    artist    = track.artist,
                    metaText  = "${track.playCount}回",
                    artBitmap = trackArtBitmaps[i],
                    left      = cardLeft,
                    top       = cardTop.toFloat(),
                    width     = cardWidth,
                    height    = trackCardH.toFloat(),
                    density   = density
                )
            }
        }
    } else {
        // 棒グラフ描画（従来通り: WEEK/MONTH）
        val barAreaTop  = y + cardPadding + 15.dp() + 12.dp()
        val barAreaH    = 80.dp().toFloat()
        val barAreaW    = contentWidth - cardPadding * 2
        val maxMs       = data.barEntries.maxOfOrNull { it.valueMs }?.coerceAtLeast(1L) ?: 1L
        val barCount    = data.barEntries.size
        val barW        = barAreaW.toFloat() / barCount * 0.5f
        val barGap      = barAreaW.toFloat() / barCount
        data.barEntries.forEachIndexed { i, entry ->
            val cx    = cl + cardPadding + barGap * i + barGap / 2f
            val frac  = (entry.valueMs.toFloat() / maxMs).coerceIn(0.02f, 1f)
            val bH    = barAreaH * frac
            val barColor = if (entry.isToday)
                android.graphics.Color.rgb(66, 133, 244)
            else
                android.graphics.Color.rgb(144, 164, 216)
            val rect = android.graphics.RectF(
                cx - barW / 2f, barAreaTop + barAreaH - bH,
                cx + barW / 2f, barAreaTop + barAreaH
            )
            canvas.drawRoundRect(rect, 3.dp().toFloat(), 3.dp().toFloat(), rectPaint(barColor))
            canvas.drawText(entry.label, cx,
                barAreaTop + barAreaH + 14.dp(),
                textPaint(9f, android.graphics.Color.argb(153, 0, 0, 0),
                    align = android.graphics.Paint.Align.CENTER))
        }
    }
    y += graphInH + sectionGap

    // ---- ランキングカード ----
    drawCard(y, rankingInH)
    canvas.drawText(rankingTitle, cl + cardPadding, y + cardPadding + 15.dp(),
        textPaint(15f, bold = true))
    var ry = y + cardPadding + 15.dp() + 10.dp()

    if (data.kind == ShareKind.TODAY) {
        data.albumRanking.take(3).forEachIndexed { i, item ->
            drawRankRow(canvas, i + 1,
                item.album.ifEmpty { "不明アルバム" }, item.artist,
                "${item.artist}・${formatListenedTime(item.playTimeMs)}",
                cl + cardPadding, cr - cardPadding, ry, density)
            ry += rankingItemH
        }
    } else {
        data.artistRanking.take(3).forEachIndexed { i, item ->
            drawRankRow(canvas, i + 1,
                item.artist, null,
                "${item.trackCount}曲、${formatListenedTime(item.playTimeMs)}",
                cl + cardPadding, cr - cardPadding, ry, density)
            ry += rankingItemH
        }
    }
    if (rankCount == 0) {
        canvas.drawText("データなし", cl + cardPadding, ry + 18.dp(),
            textPaint(13f, android.graphics.Color.argb(102, 0, 0, 0)))
    }
    y += rankingInH + sectionGap

    // ---- 円グラフカード ----
    drawCard(y, pieInH)
    canvas.drawText("アプリの使用率", cl + cardPadding, y + cardPadding + 15.dp(),
        textPaint(15f, bold = true))

    if (data.pieEntries.isEmpty()) {
        canvas.drawText("データなし", cl + cardPadding, y + cardPadding + 15.dp() + 30.dp(),
            textPaint(13f, android.graphics.Color.argb(102, 0, 0, 0)))
    } else {
        val pieTop      = y + cardPadding + 15.dp() + 12.dp()
        val pieRadius   = 35.dp().toFloat()
        val pieStroke   = 18.dp().toFloat()
        val pieCx       = cl + cardPadding + pieRadius + 5.dp()
        val pieCy       = pieTop + pieRadius
        val totalMs     = data.pieEntries.sumOf { it.valueMs }.coerceAtLeast(1L)
        val piePaint    = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = pieStroke
        }
        var startAngle  = -90f
        data.pieEntries.forEach { entry ->
            val sweep = 360f * (entry.valueMs.toFloat() / totalMs)
            piePaint.color = entry.color.toArgb()
            canvas.drawArc(
                android.graphics.RectF(pieCx - pieRadius, pieCy - pieRadius,
                    pieCx + pieRadius, pieCy + pieRadius),
                startAngle, sweep, false, piePaint
            )
            startAngle += sweep
        }
        // 凡例
        val legendX = pieCx + pieRadius + 16.dp()
        data.pieEntries.take(4).forEachIndexed { i, entry ->
            val legy = pieTop + i * 22.dp() + 10.dp()
            canvas.drawCircle(legendX + 4.dp(), legy.toFloat(),
                4.dp().toFloat(), rectPaint(entry.color.toArgb()))
            val pct = (entry.valueMs.toFloat() / totalMs * 100).toInt()
            val label = if (entry.label.length > 10) entry.label.take(9) + "…" else entry.label
            canvas.drawText("$label  $pct%",
                legendX + 12.dp(), legy + 4.dp(),
                textPaint(11f))
        }
    }
    y += pieInH

    // ---- フッター ----
    canvas.drawText(
        "Santopimedia",
        (canvasWidthPx / 2).toFloat(), y + 24.dp(),
        textPaint(11f, android.graphics.Color.argb(90, 0, 0, 0),
            align = android.graphics.Paint.Align.CENTER)
    )

    return bitmap
}

// =============================================================================
// HistoryCard風の1曲カードを描画するヘルパー
// HistoryScreen.kt の HistoryCard のデザイン（角丸背景・アルバムアートのぼかし背景・
// オーバーレイ・アート・曲名/アーティスト名）をCanvas描画で再現したもの。
// artBitmap が null の場合は音符アイコン風のプレースホルダーを描く。
// =============================================================================
private fun drawTrackCard(
    canvas: Canvas,
    title: String,
    artist: String,
    metaText: String,        // 右端に出す補足（再生回数など）
    artBitmap: Bitmap?,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    density: Float
) {
    fun Float.dp() = this * density
    fun Int.dp() = this * density

    val rect = android.graphics.RectF(left, top, left + width, top + height)
    val radius = 12.dp()

    // 1. カード背景（HistoryCardのcardBg相当: 薄いグレー）
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(14, 0, 0, 0) // Black 5.5%程度
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, radius, radius, bgPaint)

    // クリップしてアート・ぼかし背景がカード外にはみ出さないようにする
    canvas.save()
    val path = android.graphics.Path().apply {
        addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
    }
    canvas.clipPath(path)

    // 2. アルバムアートのぼかし背景（あれば）
    if (artBitmap != null) {
        // 簡易ぼかし: 縮小→拡大でぼかし風効果を出す（RenderEffectはAPI31+限定のため使わない）
        val small = Bitmap.createScaledBitmap(artBitmap, 16, 16, true)
        val blurredLike = Bitmap.createScaledBitmap(small, width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), true)
        val srcRect = android.graphics.Rect(0, 0, blurredLike.width, blurredLike.height)
        canvas.drawBitmap(blurredLike, srcRect, rect, null)
        // オーバーレイ（HistoryCardの白/黒グラデーション相当を単色半透明で簡略化）
        val overlayPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(215, 255, 255, 255) // ライトテーマ相当
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, radius, radius, overlayPaint)
    }
    canvas.restore()

    // 3. コンテンツ（アート・テキスト）
    val contentPadding = 10.dp()
    val artSize = height - contentPadding * 2
    val artLeft = left + contentPadding
    val artTop  = top + contentPadding
    val artRect = android.graphics.RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
    val artRadius = 8.dp()

    if (artBitmap != null) {
        canvas.save()
        val artPath = android.graphics.Path().apply {
            addRoundRect(artRect, artRadius, artRadius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(artPath)
        val srcRect = android.graphics.Rect(0, 0, artBitmap.width, artBitmap.height)
        canvas.drawBitmap(artBitmap, srcRect, artRect, null)
        canvas.restore()
    } else {
        val placeholderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(40, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(artRect, artRadius, artRadius, placeholderPaint)
        // 音符マーク代わりに "♪" を中央描画
        val notePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(110, 0, 0, 0)
            textSize = artSize * 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val fm = notePaint.fontMetrics
        canvas.drawText("♪", artRect.centerX(), artRect.centerY() - (fm.ascent + fm.descent) / 2f, notePaint)
    }

    // テキスト部分
    val textLeft = artRect.right + contentPadding
    val textRight = left + width - contentPadding
    val textAreaWidth = (textRight - textLeft).toInt().coerceAtLeast(1)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f.dp()
        color = android.graphics.Color.BLACK
        isFakeBoldText = true
    }
    val artistPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f.dp()
        color = android.graphics.Color.argb(166, 0, 0, 0)
    }
    val metaPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f.dp()
        color = android.graphics.Color.argb(140, 33, 100, 230)
        isFakeBoldText = true
    }

    // StaticLayoutで1行省略しつつ描画（Canvas.drawTextの手計算による文字潰れを防ぐ）
    drawSingleLineText(canvas, title.ifEmpty { "タイトル不明" }, titlePaint,
        textAreaWidth, textLeft, top + height * 0.28f)
    drawSingleLineText(canvas, artist, artistPaint,
        textAreaWidth, textLeft, top + height * 0.58f)
    drawSingleLineText(canvas, metaText, metaPaint,
        textAreaWidth, textLeft, top + height - contentPadding - (14f.dp()),
        alignOpposite = true)
}

// =============================================================================
// StaticLayoutを使って1行のテキストを指定座標に描画するヘルパー。
// Canvas.drawText + 自前のbaseline計算だと日本語フォントの縦方向メトリクスの
// ズレで文字が潰れて見えることがあるため、Androidの標準レイアウトエンジンである
// StaticLayoutに幅内での省略（ellipsize）と行の組み方を任せる。
// StaticLayoutは常に原点(0,0)基準でレイアウトを組むため、
// canvas.translate で目的の座標まで移動してから描画し、終わったら元に戻す。
// alignOpposite=true の場合は右寄せ（残り時間・回数などの右側表示用）になる。
// =============================================================================
private fun drawSingleLineText(
    canvas: Canvas,
    text: String,
    paint: android.text.TextPaint,
    maxWidth: Int,
    x: Float,
    y: Float,
    alignOpposite: Boolean = false
) {
    val ellipsizedText = android.text.TextUtils.ellipsize(
        text, paint, maxWidth.toFloat(), android.text.TextUtils.TruncateAt.END
    )
    val align = if (alignOpposite)
        android.text.Layout.Alignment.ALIGN_OPPOSITE
    else
        android.text.Layout.Alignment.ALIGN_NORMAL

    val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        android.text.StaticLayout.Builder
            .obtain(ellipsizedText, 0, ellipsizedText.length, paint, maxWidth)
            .setAlignment(align)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    } else {
        @Suppress("DEPRECATION")
        android.text.StaticLayout(
            ellipsizedText, paint, maxWidth, align, 1f, 0f, false
        )
    }

    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
}


private fun drawRankRow(
    canvas: Canvas,
    rank: Int,
    title: String,
    subtitle: String?,
    meta: String,
    left: Float,
    right: Float,
    top: Float,
    density: Float
) {
    fun Float.dp() = (this * density).toInt().toFloat()
    fun Int.dp()   = (this * density).toInt().toFloat()
    fun textPaint(sp: Float, color: Int = android.graphics.Color.BLACK, bold: Boolean = false,
                  align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT) =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp * density; this.color = color; isFakeBoldText = bold; textAlign = align
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
    fun rectPaint(c: Int) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        .apply { this.color = c; style = android.graphics.Paint.Style.FILL }

    val badgeSize = 26.dp()
    val badgeColor = when (rank) {
        1 -> android.graphics.Color.rgb(255, 215, 0)
        2 -> android.graphics.Color.rgb(192, 192, 192)
        else -> android.graphics.Color.rgb(205, 127, 50)
    }
    val badgeTextColor = when (rank) {
        1 -> android.graphics.Color.rgb(122, 88, 0)
        2 -> android.graphics.Color.rgb(68, 68, 68)
        else -> android.graphics.Color.rgb(92, 45, 0)
    }
    val cx = left + badgeSize / 2f
    val cy = top + 22.dp()
    canvas.drawCircle(cx, cy, badgeSize / 2f, rectPaint(badgeColor))
    canvas.drawText("$rank", cx, cy + 5.dp(),
        textPaint(12f, badgeTextColor, bold = true,
            align = android.graphics.Paint.Align.CENTER))

    val textLeft = left + badgeSize + 10.dp()
    val titleText = if (title.length > 18) title.take(17) + "…" else title
    canvas.drawText(titleText, textLeft, top + 16.dp(),
        textPaint(14f, bold = true))
    canvas.drawText(meta, textLeft, top + 32.dp(),
        textPaint(11f, android.graphics.Color.argb(140, 0, 0, 0)))
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): File {
    val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(cacheDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}