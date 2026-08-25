package com.sandolpin.santopimedia35

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.sandolpin.santopimedia35.database.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== アクションメニュー =====
@Composable
fun ActionMenuDialog(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowLyrics: () -> Unit = {},
    onShareScreenshot: () -> Unit = {},
    onShowProperty: () -> Unit = {},
    onShowFavorites: () -> Unit = {}
) {
    val mediaState by viewModel.mediaState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showSearchPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // お気に入りViewModel（現在再生中の曲のお気に入り状態取得用）
    val favoriteViewModel: com.sandolpin.santopimedia35.favorite.FavoriteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.sandolpin.santopimedia35.favorite.FavoriteViewModel.Factory(context)
        )
    val isFavorite = favoriteViewModel.isFavorite(
        mediaState.title, mediaState.artist, mediaState.packageName
    )
    // albumArtUri が無くBitmapのみのアプリ対応: キャッシュ済みファイルURIを取得
    // （ファイルI/Oを避けるためrememberでキャッシュ済みパスのみ参照する）
    val resolvedArtUri = remember(mediaState.title, mediaState.artist, mediaState.packageName) {
        mediaState.albumArtUri ?: mediaState.albumArtBitmap?.let { bmp ->
            val key = "${mediaState.title}|${mediaState.artist}|${mediaState.packageName}"
            AlbumArtCache.saveAndGetUri(context, bmp, key)
        }
    }

    // 背景色に応じたテキスト・アイコン色を計算（背景は85%不透明）
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val contentColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f)
        Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val subColor = contentColor.copy(alpha = 0.55f)
    val iconBgColor = contentColor.copy(alpha = 0.08f)
    val borderColor = contentColor.copy(alpha = 0.12f)

    if (showTimePicker) {
        TimePickerDialog(
            durationMs = mediaState.durationMs,
            onDismiss = { showTimePicker = false },
            onConfirm = { totalMs ->
                viewModel.seekTo(totalMs)
                showTimePicker = false
                onDismiss()
            }
        )
        return
    }

    if (showSearchPicker) {
        SearchPickerDialog(
            title = mediaState.title,
            artist = mediaState.artist,
            album = mediaState.album,
            onDismiss = { showSearchPicker = false }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ヘッダー
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "閉じる", tint = contentColor,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("アクションメニュー", fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = contentColor)
                }

                // ===== メニュー項目（設定の並び順・非表示に従って動的に描画） =====
                val hiddenIds = remember(settings.actionMenuHidden) {
                    settings.actionMenuHidden.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                val orderedIds = remember(settings.actionMenuOrder) {
                    settings.actionMenuOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                orderedIds.filter { it !in hiddenIds }.forEach { id ->
                    when (id) {
                        "share" -> ActionMenuItem(
                            icon = Icons.Rounded.Share,
                            title = "シェアしよう",
                            description = "画面をシェアして、友達などに\n音楽とアプリをシェアしましょう",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = {
                                onDismiss()
                                onShareScreenshot()
                            }
                        )
                        "search" -> ActionMenuItem(
                            icon = Icons.Rounded.Search,
                            title = "今流れている曲をしらべる",
                            description = "今流れているアーティスト、アルバムなどを検索できます",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { showSearchPicker = true }
                        )
                        "time_seek" -> ActionMenuItem(
                            icon = Icons.Rounded.History,
                            title = "指定の時間から再生",
                            description = "自分の好きなところから再生を始めることができます",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { showTimePicker = true }
                        )
                        "lyrics" -> ActionMenuItem(
                            icon = Icons.Rounded.Lyrics,
                            title = "歌詞を表示",
                            description = "今流れている曲の歌詞を表示します",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { onShowLyrics(); onDismiss() }
                        )
                        "favorites_list" -> ActionMenuItem(
                            icon = Icons.Rounded.Star,
                            title = "お気に入り",
                            description = "お気に入りに登録した曲を表示します",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { onShowFavorites(); onDismiss() }
                        )
                        "favorite_toggle" -> ActionMenuItem(
                            icon = if (isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                            title = if (isFavorite) "この曲をお気に入りから解除する" else "この曲をお気に入りにする",
                            description = if (isFavorite) "現在再生中の曲をお気に入りから削除します"
                            else "現在再生中の曲をお気に入りに追加します",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = {
                                favoriteViewModel.toggleFavorite(
                                    title       = mediaState.title,
                                    artist      = mediaState.artist,
                                    album       = mediaState.album,
                                    albumArtUri = resolvedArtUri,
                                    appLabel    = mediaState.appLabel,
                                    packageName = mediaState.packageName,
                                    mediaId     = mediaState.mediaId,
                                    durationMs  = mediaState.durationMs
                                )
                                onDismiss()
                            }
                        )
                        "property" -> ActionMenuItem(
                            icon = Icons.Rounded.Info,
                            title = "プロパティ",
                            description = "現在再生中のメタデータやキュー情報を表示します",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { onShowProperty(); onDismiss() }
                        )
                        "settings" -> ActionMenuItem(
                            icon = Icons.Rounded.Settings,
                            title = "設定",
                            description = "さらにカスタマイズできる項目を表示します",
                            contentColor = contentColor, subColor = subColor,
                            iconBgColor = iconBgColor, borderColor = borderColor,
                            onClick = { onNavigateToSettings(); onDismiss() }
                        )
                    }
                }
            }
        }
    }
}

// ===== アクションメニュー項目（塗りつぶしなし・枠線スタイル）=====
@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    contentColor: Color,
    subColor: Color,
    iconBgColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = contentColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = contentColor)
                Text(description, fontSize = 11.sp, color = subColor, lineHeight = 15.sp)
            }
        }
    }
}

// ===== 指定の時間から再生（曲の長さが上限）=====
@Composable
fun TimePickerDialog(
    durationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val totalSec = (durationMs / 1000).coerceAtLeast(0)
    val maxHours = (totalSec / 3600).toInt()
    val maxMinutes = if (maxHours == 0) (totalSec / 60).toInt() else 59
    val maxSeconds = if (maxHours == 0 && maxMinutes == 0) totalSec.toInt() else 59

    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }

    val bgColor = MaterialTheme.colorScheme.surface
    val contentColor = if (bgColor.luminance() > 0.5f) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "戻る", tint = contentColor,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("指定の時間から再生", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = contentColor)
                        Text(
                            "スライダーで時間を合わせてください（上限: ${formatTime(durationMs)}）",
                            fontSize = 11.sp, color = contentColor.copy(0.5f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrumRollPicker(value = hours, range = 0..maxHours.coerceAtLeast(0),
                        onValueChange = { hours = it }, contentColor = contentColor)
                    Text(":", fontSize = 32.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(horizontal = 4.dp), color = contentColor)
                    DrumRollPicker(value = minutes, range = 0..maxMinutes.coerceAtLeast(0),
                        onValueChange = { minutes = it }, contentColor = contentColor)
                    Text(":", fontSize = 32.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(horizontal = 4.dp), color = contentColor)
                    DrumRollPicker(value = seconds, range = 0..maxSeconds.coerceAtLeast(0),
                        onValueChange = { seconds = it }, contentColor = contentColor)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
                        onConfirm(totalMs.coerceAtMost(durationMs))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ここから再生", fontSize = 16.sp)
                }
            }
        }
    }
}

// ===== ドラムロールピッカー =====
@Composable
fun DrumRollPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    contentColor: Color = Color.Unspecified
) {
    val items = range.toList()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceAtLeast(0)
    )
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val index = listState.firstVisibleItemIndex
            onValueChange(items.getOrElse(index) { value })
        }
    }
    Box(modifier = Modifier.width(60.dp).height(120.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth().height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)
        ) {
            item { Spacer(Modifier.height(38.dp)) }
            items(items.size) { index ->
                val item = items[index]
                val isSelected = item == value
                Box(modifier = Modifier.height(40.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Text(
                        text = "%02d".format(item),
                        fontSize = if (isSelected) 32.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Light,
                        color = if (isSelected) (if (contentColor == Color.Unspecified)
                            MaterialTheme.colorScheme.onSurface else contentColor)
                        else (if (contentColor == Color.Unspecified)
                            MaterialTheme.colorScheme.onSurface else contentColor).copy(alpha = 0.3f)
                    )
                }
            }
            item { Spacer(Modifier.height(38.dp)) }
        }
    }
}

// ===== 検索方法選択ダイアログ =====
@Composable
fun SearchPickerDialog(
    title: String,
    artist: String,
    album: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.surface
    val contentColor = if (bgColor.luminance() > 0.5f) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val borderColor = contentColor.copy(alpha = 0.12f)
    val iconBgColor = contentColor.copy(alpha = 0.1f)

    data class SearchOption(val icon: ImageVector, val label: String, val query: String)

    val options = listOf(
        SearchOption(Icons.Rounded.Title, "タイトル", title),
        SearchOption(Icons.Rounded.Person, "アーティスト", artist),
        SearchOption(Icons.Rounded.Album, "アルバム", album),
        SearchOption(Icons.Rounded.MusicNote, "タイトルとアーティスト", "$title $artist"),
        SearchOption(Icons.Rounded.LibraryMusic, "アーティストとアルバム", "$artist $album"),
        SearchOption(Icons.Rounded.QrCode2, "コード（曲名・アーティスト・アルバム）", "$title $artist $album コード"),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ヘッダー
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "戻る", tint = contentColor,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("検索", fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = contentColor)
                    Spacer(Modifier.width(8.dp))
                    Text("検索方法を選んでください", fontSize = 12.sp,
                        color = contentColor.copy(0.5f))
                }

                // 検索オプション一覧
                options.forEach { opt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                val query = android.net.Uri.encode(opt.query)
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.google.com/search?q=$query")
                                )
                                context.startActivity(intent)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(iconBgColor, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(opt.icon, null, modifier = Modifier.size(20.dp),
                                    tint = contentColor)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(opt.label, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = contentColor)
                        }
                    }
                }
            }
        }
    }
}

// ===== シェア =====
/**
 * PixelCopy を使って HomeScreen のスクリーンショットをシェア
 *
 * 【なぜ PixelCopy か】
 *   Jetpack Compose は SurfaceView/TextureView ベースで GPU レンダリングするため、
 *   view.draw(canvas) ではコンポーザブルの内容が空白になる。
 *   PixelCopy.request() はハードウェアバッファを直接読み取るため正しくキャプチャできる。
 *
 * 【呼び出しタイミング】
 *   ActionMenuDialog の onDismiss() の後、300ms 待ってから呼ぶこと。
 *   ダイアログが消えて HomeScreen が前面に描画された状態でキャプチャするため。
 */
fun shareScreenshot(
    context: android.content.Context,
    title: String,
    artist: String
) {
    val shareText = "$title / $artist を再生中\n#さんとぴメディアコントロール"
    val activity = context as? android.app.Activity

    if (activity == null) {
        showErrorDialog(context, "Activity取得失敗", "context を Activity にキャストできませんでした。")
        shareTextOnly(context, shareText)
        return
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val window = activity.window
        val view   = window.decorView

        if (view.width == 0 || view.height == 0) {
            showErrorDialog(context, "ビュー未描画", "decorView のサイズが 0 です (${view.width}x${view.height})。")
            shareTextOnly(context, shareText)
            return
        }

        val bitmap = android.graphics.Bitmap.createBitmap(
            view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
        )

        android.view.PixelCopy.request(
            window, bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    CoroutineScope(Dispatchers.IO).launch {
                        saveBitmapAndShare(context, bitmap, shareText)
                    }
                } else {
                    val reason = when (result) {
                        android.view.PixelCopy.ERROR_UNKNOWN           -> "ERROR_UNKNOWN"
                        android.view.PixelCopy.ERROR_TIMEOUT           -> "ERROR_TIMEOUT"
                        android.view.PixelCopy.ERROR_SOURCE_NO_DATA    -> "ERROR_SOURCE_NO_DATA"
                        android.view.PixelCopy.ERROR_SOURCE_INVALID    -> "ERROR_SOURCE_INVALID"
                        android.view.PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
                        else -> "不明なエラーコード: $result"
                    }
                    android.util.Log.e("SantopiMedia", "PixelCopy failed: $reason")
                    showErrorDialog(context, "PixelCopy 失敗", "スクリーンショット取得に失敗しました。\n\nエラー: $reason")
                    shareTextOnly(context, shareText)
                }
            },
            android.os.Handler(android.os.Looper.getMainLooper())
        )
    } else {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val v = activity.window.decorView.rootView
                val bitmap = android.graphics.Bitmap.createBitmap(
                    v.width.coerceAtLeast(1),
                    v.height.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                withContext(Dispatchers.Main) { v.draw(canvas) }
                saveBitmapAndShare(context, bitmap, shareText)
            } catch (e: Exception) {
                android.util.Log.e("SantopiMedia", "fallback screenshot error", e)
                withContext(Dispatchers.Main) {
                    showErrorDialog(context, "スクリーンショット失敗 (API<26)", e.stackTraceToString())
                    shareTextOnly(context, shareText)
                }
            }
        }
    }
}

// ビットマップをキャッシュに保存してシェアIntent を発行
private suspend fun saveBitmapAndShare(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    shareText: String
) = withContext(Dispatchers.IO) {
    try {
        val cacheDir  = java.io.File(context.cacheDir, "share").also { it.mkdirs() }
        val cacheFile = java.io.File(cacheDir, "share_screenshot.jpg")
        java.io.FileOutputStream(cacheFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        val uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cacheFile
            )
        } catch (e: Exception) {
            android.util.Log.e("SantopiMedia", "FileProvider error", e)
            withContext(Dispatchers.Main) {
                showErrorDialog(
                    context,
                    "FileProvider エラー",
                    "authority: ${context.packageName}.fileprovider\n\n" +
                            "AndroidManifest.xml に FileProvider が登録されているか、\n" +
                            "res/xml/file_paths.xml に cache-path が設定されているか確認してください。\n\n" +
                            e.stackTraceToString()
                )
                shareTextOnly(context, shareText)
            }
            return@withContext
        }
        withContext(Dispatchers.Main) {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "シェア"))
        }
    } catch (e: Exception) {
        android.util.Log.e("SantopiMedia", "saveBitmapAndShare error", e)
        withContext(Dispatchers.Main) {
            showErrorDialog(context, "シェア処理エラー", e.stackTraceToString())
            shareTextOnly(context, shareText)
        }
    }
}

// エラーダイアログ表示（デバッグ用）
private fun showErrorDialog(
    context: android.content.Context,
    title: String,
    message: String
) {
    val activity = context as? android.app.Activity ?: return
    activity.runOnUiThread {
        android.app.AlertDialog.Builder(activity)
            .setTitle("⚠️ $title")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

// テキストのみシェア（フォールバック）
private fun shareTextOnly(context: android.content.Context, shareText: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "シェア"))
}

// 旧 shareCurrentTrack との互換性のために残す
fun shareCurrentTrack(
    context: android.content.Context,
    title: String,
    artist: String,
    albumArtUri: String?
) = shareScreenshot(context, title, artist)

suspend fun loadBitmapFromUri(context: android.content.Context, uri: String): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (uri.startsWith("http")) {
                val url = java.net.URL(uri)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                android.graphics.BitmapFactory.decodeStream(conn.inputStream)
            } else {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                    ?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) { null }
    }

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}