package com.sandolpin.santopimedia35.favorite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== アルバムごとにグループ化した表示用データ =====
data class FavoriteAlbumGroup(
    val groupKey: String,          // Shared Element Transition用の一意キー（packageName||albumLabel）
    val albumLabel: String,        // 空の場合は "不明アルバム"
    val albumArtUri: String?,      // グループ内で最初に見つかったアートワーク
    val appLabel: String,
    val packageName: String,
    val tracks: List<FavoriteTrackEntity>
)

class FavoriteViewModel(
    private val repo: FavoriteRepository
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteTrackEntity>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrackEntity>> = _favorites.asStateFlow()

    // メモリキャッシュ: 「曲がお気に入り済みか」を即座に判定するため（DBアクセスを毎回行わない）
    private val favoriteKeyCache = mutableSetOf<String>()

    init {
        refresh()
    }

    private fun keyOf(title: String, artist: String, packageName: String) =
        "$title|$artist|$packageName"

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repo.queryAll()
            favoriteKeyCache.clear()
            favoriteKeyCache.addAll(list.map { keyOf(it.title, it.artist, it.packageName) })
            _favorites.value = list
        }
    }

    fun isFavorite(title: String, artist: String, packageName: String): Boolean =
        favoriteKeyCache.contains(keyOf(title, artist, packageName))

    /** ☆タップ時: 未登録なら追加、登録済みなら削除（トグル）*/
    fun toggleFavorite(
        title: String,
        artist: String,
        album: String,
        albumArtUri: String?,
        appLabel: String,
        packageName: String,
        mediaId: String,
        durationMs: Long
    ) {
        val key = keyOf(title, artist, packageName)
        val nowFavorite = favoriteKeyCache.contains(key)
        // 即時反映（UIのちらつき防止）
        if (nowFavorite) favoriteKeyCache.remove(key) else favoriteKeyCache.add(key)

        viewModelScope.launch(Dispatchers.IO) {
            if (nowFavorite) {
                repo.remove(title, artist, packageName)
            } else {
                repo.add(
                    title = title, artist = artist, album = album,
                    albumArtUri = albumArtUri, appLabel = appLabel,
                    packageName = packageName, mediaId = mediaId,
                    durationMs = durationMs
                )
            }
            val list = withContext(Dispatchers.IO) { repo.queryAll() }
            _favorites.value = list
        }
    }

    /** アルバムごとにグループ化。album名が空の曲は「不明アルバム」にまとめる。
     *  さらにアプリ(packageName)ごとにも分けてグルーピングする。 */
    fun groupedByAlbum(): List<FavoriteAlbumGroup> {
        val list = _favorites.value
        val groups = LinkedHashMap<String, MutableList<FavoriteTrackEntity>>()
        for (track in list) {
            val albumLabel = track.album.ifEmpty { "不明アルバム" }
            val groupKey = "${track.packageName}||$albumLabel"
            groups.getOrPut(groupKey) { mutableListOf() }.add(track)
        }
        return groups.entries.map { (groupKey, tracks) ->
            val first = tracks.first()
            FavoriteAlbumGroup(
                groupKey    = groupKey,
                albumLabel  = first.album.ifEmpty { "不明アルバム" },
                albumArtUri = tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                appLabel    = first.appLabel.ifEmpty { first.packageName },
                packageName = first.packageName,
                tracks      = tracks
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FavoriteViewModel(FavoriteRepository(context.applicationContext)) as T
    }
}