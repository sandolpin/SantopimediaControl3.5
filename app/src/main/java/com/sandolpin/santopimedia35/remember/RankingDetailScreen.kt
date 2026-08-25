package com.sandolpin.santopimedia35.remember

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== アーティスト別曲ランキングアイテム =====
data class ArtistTrackRankItem(
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playCount: Int,
    val playTimeMs: Long
)

// ===== RankingDetailScreen =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingDetailScreen(
    artist: ArtistRankItem,
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit
) {
    var period by remember { mutableStateOf(RankingPeriod.WEEK) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var totalPlayTimeMs by remember { mutableStateOf(0L) }
    var trackRanking by remember { mutableStateOf<List<ArtistTrackRankItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // 集計
    LaunchedEffect(period) {
        scope.launch {
            val allHistory = withContext(Dispatchers.IO) { viewModel.getAllHistory() }
            val startMs = periodStartMs(period)
            val endMs   = periodEndMs(period)
            val filtered = allHistory.filter {
                it.artist == artist.artist && it.playedAtMs in startMs..endMs
            }

            totalPlayTimeMs = filtered.sumOf { it.playTimeMs }

            // 曲ごとの再生回数集計
            data class Acc(var count: Int, var time: Long, var artUri: String?)
            val map = mutableMapOf<String, Acc>()
            for (e in filtered) {
                val key = e.title.ifEmpty { "不明" }
                val acc = map.getOrPut(key) { Acc(0, 0L, null) }
                acc.count++
                acc.time += e.playTimeMs
                if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
            }
            trackRanking = map.entries
                .sortedByDescending { it.value.count }
                .map { ArtistTrackRankItem(it.key, artist.artist, it.value.artUri,
                    it.value.count, it.value.time) }
        }
    }

    val periodLabel = when (period) {
        RankingPeriod.TODAY      -> "きょう"
        RankingPeriod.WEEK       -> "今週"
        RankingPeriod.MONTH      -> "今月"
        RankingPeriod.LAST_MONTH -> "先月"
        RankingPeriod.YEAR       -> "今年"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "アーティストの詳細",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = {
                    Box {
                        OutlinedButton(
                            onClick = { periodMenuExpanded = true },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(period.label, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = periodMenuExpanded,
                            onDismissRequest = { periodMenuExpanded = false }
                        ) {
                            RankingPeriod.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.label) },
                                    onClick = { period = p; periodMenuExpanded = false },
                                    leadingIcon = if (p == period) ({
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                    }) else null
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ===== ヘッダー: アルバムアート + アーティスト名 =====
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // 背景アルバムアート（ぼかし）
                    if (artist.albumArtUri != null) {
                        AsyncImage(
                            model = artist.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(24.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }

                    // アルバムアート + アーティスト名
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (artist.albumArtUri != null) {
                            AsyncImage(
                                model = artist.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .border(3.dp, Color.White, RoundedCornerShape(14.dp))
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .border(3.dp, Color.White, RoundedCornerShape(14.dp))
                                    .padding(3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, null,
                                    modifier = Modifier.size(50.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = artist.artist,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (artist.albumArtUri != null) Color.White
                            else MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ===== 聴いた時間サマリー =====
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${periodLabel}聴いた時間",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                        Text(
                            formatListenedTime(totalPlayTimeMs),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ===== 聴いた回数ランキング =====
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outline.copy(0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // セクションヘッダー
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FormatListNumbered, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("聴いた回数ランキング",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(Modifier.height(12.dp))

                        if (trackRanking.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "データなし",
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            trackRanking.take(20).forEachIndexed { i, item ->
                                ArtistDetailTrackRow(rank = i + 1, item = item)
                                if (i < minOf(19, trackRanking.size - 1)) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(0.1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ===== アーティスト詳細の曲行 =====
@Composable
fun ArtistDetailTrackRow(rank: Int, item: ArtistTrackRankItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))

        // アルバムアート
        if (item.albumArtUri != null) {
            AsyncImage(
                model = item.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifEmpty { "タイトル不明" },
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.artist,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // 再生回数（右端）
        Text(
            "${item.playCount}回",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}