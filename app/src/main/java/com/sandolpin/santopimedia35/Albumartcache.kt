package com.sandolpin.santopimedia35

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * AlbumArtCache
 *
 * URIを提供せず Bitmap（METADATA_KEY_ART / METADATA_KEY_ALBUM_ART）のみでアートワークを
 * 渡してくるアプリ（例: 一部の音楽アプリ）向けの対応。
 *
 * SQLiteの albumArtUri カラムは String? のため Bitmap を直接保存できない。
 * そこで Bitmap を内部キャッシュフォルダ（/cache/album_art/）に JPEG ファイルとして保存し、
 * その file:// URI 文字列を albumArtUri として扱えるようにする。
 *
 * ファイル名は「曲を一意に識別するキー（title|artist|packageName）」のハッシュを使うため、
 * 同じ曲であれば重複保存されず、2回目以降はキャッシュ済みファイルの存在チェックのみで済む。
 */
object AlbumArtCache {

    private const val DIR_NAME = "album_art"

    /**
     * Bitmap をキャッシュファイルとして保存し、file:// 形式のURI文字列を返す。
     * 既に同じキーで保存済みの場合はそのファイルのURIをそのまま返す（再保存しない）。
     * 保存に失敗した場合は null を返す。
     */
    fun saveAndGetUri(context: Context, bitmap: Bitmap, trackKey: String): String? {
        return try {
            val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
            val fileName = "art_${trackKey.hashCode()}.jpg"
            val file = File(dir, fileName)

            // 既にキャッシュ済みならそのまま返す（毎回エンコードし直さない）
            if (file.exists() && file.length() > 0L) {
                return file.toUri()
            }

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.toUri()
        } catch (e: Exception) {
            android.util.Log.e("SantopiMedia", "AlbumArtCache: 保存失敗", e)
            null
        }
    }

    /** 古いキャッシュファイルを一括削除（設定画面のキャッシュクリア等から呼び出す想定）*/
    fun clearAll(context: Context) {
        try {
            val dir = File(context.cacheDir, DIR_NAME)
            if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.e("SantopiMedia", "AlbumArtCache: クリア失敗", e)
        }
    }

    private fun File.toUri(): String = android.net.Uri.fromFile(this).toString()
}