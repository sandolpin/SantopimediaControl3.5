package com.sandolpin.santopimedia35

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.remember.PlayHistoryEntity
import com.sandolpin.santopimedia35.remember.formatPlayedAt
import java.util.Calendar

/**
 * HistoryScreenの「きいた曲」カードを長押しすると表示される詳細ダイアログ。
 *
 * 表示内容:
 *  - 再生したアプリ（アプリアイコン・アプリ名・「この曲を再生」ボタン）
 *  - この曲のメタデータ（タイトル・アーティスト・アルバム・サブタイトル・メディアID・追加情報・アートワークの種類）
 *  - 再生した時間（開始時間・停止時間・曲全体の再生時間）
 *
 * 再生リクエスト:
 *  entity.mediaId を使って対象アプリ（entity.packageName）の MediaController に
 *  playFromMediaId() を送る。対象アプリが現在アクティブセッションを持っていない場合は
 *  NO_SESSION が返るため、アプリを起動して再度試すよう促す。
 */
@Composable
fun HistoryLongTapDialog(
    entity: PlayHistoryEntity,
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var requestResultMessage by remember { mutableStateOf<String?>(null) }

    val bgColor = MaterialTheme.colorScheme.surface
    val isDark = bgColor.luminance() < 0.5f
    val contentColor = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)
    val subColor = contentColor.copy(alpha = 0.55f)
    val sectionBorderColor = contentColor.copy(alpha = 0.18f)

    // 曲全体の再生時間（mm:ss / mm:ss 形式）。stop時点のシークバー位置(listenedMs)を分子に使う。
    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    // 停止時間 = 開始時刻 + 実際に再生していた時間（playTimeMs）として近似表示
    val stoppedAtMs = entity.playedAtMs + entity.playTimeMs

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "詳細",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ===== 再生したアプリ =====
                DetailSection(title = "再生したアプリ", borderColor = sectionBorderColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconBox(packageName = entity.packageName, borderColor = sectionBorderColor)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entity.appLabel.ifEmpty { entity.packageName },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val result = viewModel.playFromMediaIdForSession(
                                    entity.packageName, entity.mediaId
                                )
                                requestResultMessage = when {
                                    entity.mediaId.isEmpty() ->
                                        "メディアIDが記録されていないため再生できません"
                                    result == MediaPlayerViewModel.PlayRequestResult.SUCCESS ->
                                        null // 成功時はダイアログを閉じる
                                    else ->
                                        "アプリが起動していないため再生できません。アプリを起動しますか？"
                                }
                                if (result == MediaPlayerViewModel.PlayRequestResult.SUCCESS) {
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("この曲を再生", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ===== この曲のメタデータ =====
                DetailSection(title = "この曲のメタデータ", borderColor = sectionBorderColor) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (entity.albumArtUri != null) {
                            AsyncImage(
                                model = entity.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(sectionBorderColor, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.MusicNote, null,
                                    tint = subColor, modifier = Modifier.size(26.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                entity.title.ifEmpty { "タイトル不明" },
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                entity.artist.ifEmpty { "アーティスト不明" },
                                fontSize = 12.sp, color = subColor
                            )
                            if (entity.album.isNotEmpty()) {
                                Text(entity.album, fontSize = 12.sp, color = subColor)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = sectionBorderColor)
                    Spacer(Modifier.height(10.dp))

                    // 履歴DBには保存されていない項目は「—」で表示
                    DetailRow("サブタイトル", "—", subColor, contentColor)
                    DetailRow(
                        "メディアID",
                        entity.mediaId.ifEmpty { "—" },
                        subColor, contentColor
                    )
                    DetailRow("追加情報", "—", subColor, contentColor)
                    DetailRow(
                        "アートワークの種類",
                        if (entity.albumArtUri != null) "ART_URI" else "なし",
                        subColor, contentColor
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ===== 再生した時間 =====
                DetailSection(title = "再生した時間", borderColor = sectionBorderColor) {
                    DetailRow("開始時間", formatFullDateTime(entity.playedAtMs), subColor, contentColor)
                    DetailRow("停止時間", formatFullDateTime(stoppedAtMs), subColor, contentColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "曲全体の再生時間 ${formatMs(entity.listenedMs)} / ${formatMs(entity.durationMs)}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor
                    )
                    Text(
                        "※これは停止時間時点のシークバーの位置です。",
                        fontSize = 10.sp, color = subColor
                    )
                }

                // ===== 再生リクエスト結果メッセージ（NO_SESSION時のフォールバック導線）=====
                requestResultMessage?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(msg, fontSize = 12.sp, color = contentColor)
                            if (entity.mediaId.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.openApp(entity.packageName)
                                        requestResultMessage = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("アプリを起動する", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("閉じる", fontSize = 15.sp, color = contentColor)
                }
            }
        }
    }
}

// ===== セクションラッパー（枠線スタイル・画像デザインに準拠）=====
@Composable
private fun DetailSection(
    title: String,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

// ===== 詳細行（ラベル: 値）=====
@Composable
private fun DetailRow(label: String, value: String, subColor: Color, contentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$label:",
            fontSize = 12.sp,
            color = subColor,
            modifier = Modifier.width(110.dp)
        )
        Text(
            value,
            fontSize = 12.sp,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

// ===== アプリアイコン表示（AppSelectScreen.ktのtoBitmap()を再利用）=====
@Composable
private fun AppIconBox(packageName: String, borderColor: Color) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) }
        catch (e: Exception) { null }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(18.dp))
        }
    }
}

// ===== 日時フォーマット（年/月/日 時:分）=====
private fun formatFullDateTime(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "%d/%d/%d %02d:%02d:%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND)
    )
}