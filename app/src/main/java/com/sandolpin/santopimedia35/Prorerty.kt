package com.sandolpin.santopimedia35

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
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
import com.sandolpin.santopimedia35.database.MediaState

// ===== プロパティダイアログ =====
@Composable
fun PropertyDialog(
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val context = LocalContext.current

    val bgColor = MaterialTheme.colorScheme.surface
    val isDark = bgColor.luminance() < 0.5f
    val contentColor = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)
    val subColor = contentColor.copy(alpha = 0.55f)
    val sectionBgColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
    val borderColor = contentColor.copy(alpha = 0.10f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ===== ヘッダー =====
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        "プロパティ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close, "閉じる",
                            tint = subColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ===== 現在再生中のアプリ =====
                PropertySection(
                    title = "現在再生中のアプリ",
                    bgColor = sectionBgColor,
                    borderColor = borderColor
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // アプリアイコン
                        val appIcon = remember(mediaState.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(mediaState.packageName)
                            } catch (e: Exception) { null }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIcon != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = appIcon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.MusicNote, null,
                                    tint = subColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            mediaState.appLabel.ifEmpty { mediaState.packageName },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ===== 現在再生中のメタデータ =====
                PropertySection(
                    title = "現在再生中のメタデータ",
                    bgColor = sectionBgColor,
                    borderColor = borderColor
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        // アルバムアート
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mediaState.albumArtUri != null) {
                                AsyncImage(
                                    model = mediaState.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.MusicNote, null,
                                    tint = subColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                mediaState.title.ifEmpty { "—" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                mediaState.artist.ifEmpty { "—" },
                                fontSize = 12.sp,
                                color = subColor
                            )
                            Text(
                                mediaState.album.ifEmpty { "—" },
                                fontSize = 12.sp,
                                color = subColor
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = borderColor)
                    Spacer(Modifier.height(10.dp))

                    PropertyRow("サブタイトル", mediaState.displaySubtitle, subColor, contentColor)
                    PropertyRow("メディアID", mediaState.mediaId, subColor, contentColor)
                    PropertyRow("追加情報", mediaState.displayDescription, subColor, contentColor)
                    PropertyRow(
                        "アートワークの種類",
                        when (mediaState.artUriType) {
                            "art"       -> "ART_URI"
                            "album_art" -> "ALBUM_ART_URI"
                            else        -> "なし"
                        },
                        subColor, contentColor
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ===== キューアイテムの情報 =====
                PropertySection(
                    title = "キューアイテムの情報",
                    bgColor = sectionBgColor,
                    borderColor = borderColor
                ) {
                    // キューの提供状況
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            "キューの提供状況",
                            fontSize = 12.sp,
                            color = subColor,
                            modifier = Modifier.width(130.dp)
                        )
                        Icon(
                            imageVector = if (mediaState.queueAvailable)
                                Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            tint = if (mediaState.queueAvailable)
                                Color(0xFF4CAF50) else subColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    PropertyRow("キュータイトル", mediaState.queueTitle, subColor, contentColor)
                    PropertyRow(
                        "現在再生中のリスト番号",
                        if (mediaState.activeQueueItemId >= 0L)
                            mediaState.activeQueueItemId.toString()
                        else "—",
                        subColor, contentColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ===== 閉じるボタン =====
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "閉じる",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ===== セクションラッパー =====
@Composable
private fun PropertySection(
    title: String,
    bgColor: Color,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}

// ===== プロパティ行（ラベル: 値）=====
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    subColor: Color,
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$label:",
            fontSize = 12.sp,
            color = subColor,
            modifier = Modifier.width(130.dp)
        )
        Text(
            value.ifEmpty { "—" },
            fontSize = 12.sp,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}