package com.sandolpin.santopimedia35

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ===== 歌詞ファイルピッカー（共通化）=====
// .sdlrc/.lrc/.ttml/.xml を選択 → パース → 曲(title+artist)に生データを紐づけて保存 → 結果を返す。
// NotFoundView の「ファイルから指定」ボタンと、LyricsScreen ヘッダーの三点メニュー
// 「ファイルを読み込む」の両方から使う共通ロジック。
// rememberLauncherForActivityResult はコンポーズ時に登録する必要があるため、
// 呼び出し側のコンポーザブル関数の中で1回だけ呼び出し、戻り値の関数を
// ボタンのonClick等から呼んでもらう形にする。
@Composable
fun rememberLyricsFilePicker(
    mediaState: com.sandolpin.santopimedia35.database.MediaState?,
    onLyricsLoaded: (LyricsState) -> Unit
): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // ファイルの生テキストと形式を、パース結果と一緒に持ち出す
                // （キャッシュ保存のため。曲と紐づけて保存し、次回はここを通らず即座に復元する）
                var rawText: String? = null
                var rawFormat: String? = null
                val result = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.readText()
                            ?: return@withContext null
                        val ext = uri.path?.substringAfterLast(".")?.lowercase()
                            ?: uri.toString().substringAfterLast(".").lowercase()
                        android.util.Log.d("SantopiMedia", "ファイル選択: ext=$ext")
                        rawText = text
                        when {
                            ext.contains("sdlrc") -> {
                                rawFormat = "sdlrc"
                                SdlrcParser.parse(text).toLyricsState()
                            }
                            ext.contains("lrc")   -> {
                                rawFormat = "lrc"
                                val lines = parseLrc(text)
                                if (lines.isNotEmpty())
                                    LyricsState.Synced(buildLyricItems(lines))
                                else LyricsState.NotFound
                            }
                            ext.contains("ttml") || ext.contains("xml") -> {
                                rawFormat = "ttml"
                                TtmlParser.parse(text)?.toLyricsState() ?: LyricsState.NotFound
                            }
                            else -> LyricsState.NotFound
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SantopiMedia", "ファイル読み込みエラー", e)
                        null
                    }
                }
                result?.let { loaded ->
                    // ファイルから読み込んだ歌詞を「生データ」として曲(title+artist)に紐づけて保存する。
                    // 次回同じ曲を開いたときは、ファイルを選び直さなくても
                    // このキャッシュを再パースするだけで同じ歌詞（カラオケ・右寄せ等も含む）が復元される。
                    val currentRawText = rawText
                    val currentRawFormat = rawFormat
                    if (loaded is LyricsState.Synced && mediaState != null &&
                        !currentRawText.isNullOrBlank() && currentRawFormat != null
                    ) {
                        LyricsCache.saveRaw(
                            mediaState.title,
                            mediaState.artist,
                            currentRawText,
                            currentRawFormat
                        )
                    }
                    onLyricsLoaded(loaded)
                }
            }
        }
    }

    return { launcher.launch(arrayOf("*/*")) }
}

// ===== 歌詞が見つからなかったとき =====
@Composable
fun NotFoundView(
    isBlurBg: Boolean = false,
    mediaState: com.sandolpin.santopimedia35.database.MediaState? = null,
    onLyricsLoaded: ((LyricsState) -> Unit)? = null  // ファイル選択で歌詞を直接渡す
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    var showSearchDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // ファイル選択（共通ロジック）
    val launchFilePicker = rememberLyricsFilePicker(
        mediaState = mediaState,
        onLyricsLoaded = { loaded -> onLyricsLoaded?.invoke(loaded) }
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Rounded.SearchOff, null,
                modifier = Modifier.size(48.dp), tint = textColor.copy(0.4f))
            Spacer(Modifier.height(12.dp))
            Text("歌詞が見つかりません",
                color = textColor.copy(0.6f), fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))

            // 検索条件変更ボタン
            OutlinedButton(
                onClick = { showSearchDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("検索条件を変更")
            }

            // ファイルから直接指定ボタン
            if (onLyricsLoaded != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { launchFilePicker() },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ファイルから指定 (.sdlrc/.lrc/.ttml)")
                }
            }
        }
    }

    if (showSearchDialog && mediaState != null) {
        LyricsSearchDialog(
            mediaState = mediaState,
            isBlurBg = isBlurBg,
            context = context,
            onDismiss = { showSearchDialog = false },
            onLyricsLoaded = if (onLyricsLoaded != null) { result ->
                showSearchDialog = false
                onLyricsLoaded(result)
            } else null
        )
    }
}

// ===== 検索条件変更ダイアログ =====
@Composable
fun LyricsSearchDialog(
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    isBlurBg: Boolean,
    context: android.content.Context,
    onDismiss: () -> Unit,
    onLyricsLoaded: ((LyricsState) -> Unit)? = null  // API再検索結果を親に返す
) {
    var showCustomSearch by remember { mutableStateOf(false) }
    var customTitle  by remember { mutableStateOf(mediaState.title) }
    var customArtist by remember { mutableStateOf(mediaState.artist) }
    var isSearching  by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    if (showCustomSearch) "指定して検索" else "検索条件を変更",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
                Spacer(Modifier.height(12.dp))

                if (!showCustomSearch) {
                    // タイトルだけで再検索（LRCLIB API）
                    SearchOptionButton(Icons.Rounded.Title, "タイトルだけで再検索") {
                        if (onLyricsLoaded != null) {
                            isSearching = true
                            scope.launch {
                                val result = searchLyricsWithResults(mediaState.title, "")
                                isSearching = false
                                onLyricsLoaded(result)
                                onDismiss()
                            }
                        } else {
                            openLyricsSearch(context, mediaState.title, "")
                            onDismiss()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SearchOptionButton(Icons.Rounded.Tune, "条件を指定して検索") {
                        showCustomSearch = true
                    }
                } else {
                    Text("タイトル", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("現在のタイトル: ${mediaState.title}") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("アーティスト", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    OutlinedTextField(
                        value = customArtist,
                        onValueChange = { customArtist = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("現在のアーティスト: ${mediaState.artist}") }
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (onLyricsLoaded != null) {
                                // LRCLIB APIを直接叩いて結果を返す
                                isSearching = true
                                scope.launch {
                                    val result = searchLyricsWithResults(customTitle, customArtist)
                                    isSearching = false
                                    onLyricsLoaded(result)
                                    onDismiss()
                                }
                            } else {
                                openLyricsSearch(context, customTitle, customArtist)
                                onDismiss()
                            }
                        },
                        enabled = !isSearching,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSearching) "検索中..." else "この条件でAPI検索")
                    }
                }

                if (isSearching) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SearchOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ===== 検索結果リスト画面 =====
@Composable
fun SearchResultsView(
    results: List<SearchResultItem>,
    isBlurBg: Boolean = false,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ArrowBack, "戻る", tint = textColor)
            }
            Spacer(Modifier.width(8.dp))
            Text("検索結果", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = textColor)
            Spacer(Modifier.weight(1f))
            Text("${results.size}件", fontSize = 12.sp,
                color = textColor.copy(0.5f))
        }
        HorizontalDivider(color = textColor.copy(0.15f))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { item ->
                SearchResultCard(
                    item = item,
                    textColor = textColor,
                    onClick = { onSelect(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, textColor.copy(0.15f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title.ifEmpty { "不明" },
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist.ifEmpty { "不明" },
                    fontSize = 12.sp, color = textColor.copy(0.6f), maxLines = 1)
                if (item.album.isNotEmpty()) {
                    Text(item.album, fontSize = 11.sp,
                        color = textColor.copy(0.4f), maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val min = item.duration / 60
                val sec = item.duration % 60
                Text("%d:%02d".format(min, sec),
                    fontSize = 11.sp, color = textColor.copy(0.45f))
                if (item.hasSynced) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.15f)
                    ) {
                        Text("同期", fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

fun openLyricsSearch(context: android.content.Context, title: String, artist: String) {
    // LRCLIB の検索ページを開く
    val query = java.net.URLEncoder.encode("$title $artist".trim(), "UTF-8")
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://lrclib.net/search?query=$query")
    )
    context.startActivity(intent)
}