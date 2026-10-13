package com.sandolpin.santopimedia35.lyricsscreen

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.MediaState

// 歌詞の状態別ビュー: 読み込み中・エラー・プレーン歌詞

// ===== ローディング =====
@Composable
fun LoadingView(
    onCancel: (() -> Unit)? = null,
    onSkipApi: (() -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("歌詞を取得中...", color = textColor.copy(0.6f))
            if (onCancel != null || onSkipApi != null) {
                Spacer(Modifier.height(20.dp))
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close,
                            null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("検索を中止")
                    }
                }
                if (onSkipApi != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSkipApi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Block,
                            null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("この曲はAPI検索しない")
                    }
                }
            }
        }
    }
}

// ===== エラー =====
// NotFoundView と同様に「検索条件を変更」ボタンを表示する。
// エラー（通信エラー等）が起きても、そこで行き止まりにせず
// 手動で条件を変えて再検索できるようにするため。
@Composable
fun ErrorView(
    message: String,
    isBlurBg: Boolean = false,
    mediaState: MediaState? = null,
    onLyricsLoaded: ((LyricsState) -> Unit)? = null
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    var showSearchDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "エラー: $message",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            // mediaState（現在再生中の曲情報）が無いと再検索できないため、ある場合のみ表示
            if (mediaState != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showSearchDialog = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("検索条件を変更")
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

// ===== 非同期歌詞ビュー =====
@Composable
fun PlainLyricsView(lines: List<LyricLine>, isBlurBg: Boolean = false) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(lines) { _, line ->
            if (line.text.isBlank()) {
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text = line.text,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlurBg) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(0.75f),
                    style = if (isBlurBg) LocalTextStyle.current.copy(shadow = lyricOutlineShadow)
                    else LocalTextStyle.current,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            }
        }
    }
}