package com.sandolpin.santopimedia35

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.QueueItem

/**
 * QueueScreen
 * - ヘッダー行（次に再生 + ボタン群）
 * - キューリスト
 * ※ミニプレーヤーは PlayerScreenWithSwipe 側で描画するため、ここでは持たない
 */
@Composable
fun QueueScreen(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onExpandHome: () -> Unit = {},   // ミニプレーヤータップ → HomeScreenに戻る
    onShowFavorites: () -> Unit = {} // お気に入り画面を表示
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val queueItems by viewModel.queueItems.collectAsState()
    val isShuffled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val accentColor = if (settings.extractColorFromArt) mediaState.dominantColor
    else MaterialTheme.colorScheme.primary

    val isBlur = settings.backgroundStyle == "blur"
    // 実際にぼかし背景（暗いオーバーレイ）が描画されるかどうか。
    // isBlur=true でもアルバムアートが無ければ描画されないため、
    // その場合は通常背景＋通常文字色にフォールバックする必要がある。
    val hasArtForBlur = mediaState.albumArtUri != null || mediaState.albumArtBitmap != null
    val hasBlurBackground = isBlur && hasArtForBlur
    // 色混ぜ背景も、ぼかし背景と同様に曲ごとに色が変わり暗めになりやすいため、
    // ヘッダー・キュー行の文字色を白基準にする対象として扱う
    val hasColorfulBackground = hasBlurBackground || settings.backgroundStyle == "color_mix"

    Box(modifier = Modifier.fillMaxSize()) {

        // ===== コンテンツ本体（背景＋ヘッダー＋リスト）=====
        Box(modifier = Modifier.fillMaxSize()) {

            // ===== 背景（HomeScreenと同じ）=====
            val bgArtBitmap = mediaState.albumArtBitmap
            if (isBlur && mediaState.albumArtUri != null) {
                AsyncImage(
                    model = mediaState.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            } else if (isBlur && bgArtBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bgArtBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            } else if (isBlur) {
                // isBlur設定だがアートが無い場合: 通常の背景色で塗りつぶす
                // （これが無いとシステムの透明背景のままになり、白文字と重なって見えなくなる）
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            val bgMod = when (settings.backgroundStyle) {
                "gradient" -> Modifier.background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                    )
                )
                "color_mix" -> Modifier.background(
                    // HomeScreenA.ktの「色混ぜ」と同じ考え方（静的グラデーション、アニメーションなし）
                    Brush.linearGradient(
                        listOf(accentColor, mediaState.blendedArtColor.copy(alpha = 1f))
                    )
                )
                "blur" -> Modifier
                else -> Modifier.background(MaterialTheme.colorScheme.background)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (settings.backgroundStyle != "blur") bgMod else Modifier)
                    // ★ MainActivity.ktのScaffoldがステータスバー分の余白を自動確保しなくなったため、
                    //   ここで自分でステータスバー分の高さを読んで確保する。
                    //   （背景は上のBoxで既にfillMaxSize()のままなのでステータスバーの裏まで
                    //   届いている。ヘッダーの「次に再生」等の文字だけが被らないようにする）
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                // ===== ヘッダー =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "次に再生",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = if (hasColorfulBackground) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                    // アプリを開くボタン
                    OutlinedButton(
                        onClick = { viewModel.openCurrentApp() },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("アプリを開く", fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    // お気に入り
                    IconButton(
                        onClick = onShowFavorites,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "お気に入り",
                            modifier = Modifier.size(22.dp),
                            tint = (if (hasColorfulBackground) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.6f)
                        )
                    }
                    // シャッフル
                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "シャッフル",
                            modifier = Modifier.size(22.dp),
                            tint = if (isShuffled) accentColor
                            else (if (hasColorfulBackground) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f)
                        )
                    }
                    // リピート
                    IconButton(
                        onClick = { viewModel.cycleRepeatMode() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                QueueRepeatMode.ONE -> Icons.Rounded.RepeatOne
                                else               -> Icons.Rounded.Repeat
                            },
                            contentDescription = "リピート",
                            modifier = Modifier.size(22.dp),
                            tint = if (repeatMode != QueueRepeatMode.NONE) accentColor
                            else (if (hasColorfulBackground) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f)
                        )
                    }
                }

                // ===== キューリスト =====
                val context = LocalContext.current
                // mediaState.albumArtUri が無く albumArtBitmap のみのアプリ対応:
                // Bitmap をキャッシュファイル化してURIとして使う（曲が変わったときだけ再計算）
                val fallbackArtUri = remember(mediaState.title, mediaState.artist, mediaState.packageName) {
                    if (mediaState.albumArtUri != null) {
                        mediaState.albumArtUri
                    } else {
                        mediaState.albumArtBitmap?.let { bmp ->
                            val key = "${mediaState.title}|${mediaState.artist}|${mediaState.packageName}"
                            com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(context, bmp, key)
                        }
                    }
                }
                val displayItems = if (queueItems.isNotEmpty()) {
                    queueItems
                } else if (mediaState.title.isNotEmpty()) {
                    listOf(
                        QueueItem(
                            id = 0L,
                            title = mediaState.title,
                            artist = mediaState.artist,
                            albumArtUri = fallbackArtUri
                        )
                    )
                } else {
                    emptyList()
                }

                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "キュー情報がありません",
                            color = (if (hasColorfulBackground) Color.White else MaterialTheme.colorScheme.onSurface)
                                .copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp, end = 20.dp,
                            top = 0.dp, bottom = 220.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(displayItems) { index, item ->
                            QueueItemRow(
                                index = index + 1,
                                item = item,
                                isBlurBg = hasColorfulBackground,
                                onClick = { viewModel.skipToQueueItem(item.id) }
                            )
                        }
                    }
                }
            }
        } // コンテンツ本体Box 終わり
    }
}

// ===== BottomMiniPlayer（フローティング・フロストガラス風）=====
@Composable
fun BottomMiniPlayer(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaState  by viewModel.mediaState.collectAsState()
    val accentColor = if (settings.extractColorFromArt) mediaState.dominantColor
    else MaterialTheme.colorScheme.primary

    // ダークモード判定
    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> MaterialTheme.colorScheme.background.luminance() < 0.5f
    }
    val textColor = if (isDark) Color.White else Color.Black
    val subColor  = textColor.copy(alpha = 0.60f)

    // フロストガラス風の背景色
    // ダーク: 黒ベース + 白をほんのり混ぜてガラス感を出す
    // ライト: 白ベース + 少し暗めにして視認性を確保
    val glassColor = if (isDark)
        Color(0xFF1A1A1A).copy(alpha = 0.80f)
    else
        Color(0xFFF5F5F5).copy(alpha = 0.85f)

    val borderColor = if (isDark)
        Color.White.copy(alpha = 0.15f)
    else
        Color.Black.copy(alpha = 0.08f)

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 24.dp,
                    shape        = shape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor    = Color.Black.copy(alpha = 0.55f)
                )
                .clip(shape)
                // 背景: 単色ガラス風（半透明）
                .background(glassColor)
                // 縁取り
                .border(width = 0.8.dp, color = borderColor, shape = shape)
                .clickable { onTap() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ドラッグハンドル
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = 0.25f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val miniArtBitmap = mediaState.albumArtBitmap
                    if (mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else if (miniArtBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = miniArtBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(textColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null,
                                tint = subColor, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mediaState.title.ifEmpty { "タイトル不明" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = textColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            mediaState.artist.ifEmpty { "アーティスト不明" },
                            fontSize = 12.sp, color = subColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(mediaState.currentPositionStr, fontSize = 10.sp, color = subColor)
                            Text("-${mediaState.remainingTimeStr}", fontSize = 10.sp, color = subColor)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { mediaState.progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = accentColor,
                    trackColor = textColor.copy(alpha = 0.15f)
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = textColor, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.seekRelative(-10000L) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Replay10, null, tint = textColor, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null, tint = textColor, modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.seekRelative(10000L) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Forward10, null, tint = textColor, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipNext, null, tint = textColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItemRow(
    index: Int,
    item: QueueItem,
    isBlurBg: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isBlurBg) Color.White.copy(0.18f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%02d".format(index),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(400),
                    color = textColor.copy(alpha = 0.4f),
                    modifier = Modifier.width(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifEmpty { "曲名不明" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight(700),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist.ifEmpty { "アーティスト不明" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight(600),
                        color = textColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }   // inner Box
    }       // outer Box
}           // QueueItemRow

enum class QueueRepeatMode { NONE, ALL, ONE }