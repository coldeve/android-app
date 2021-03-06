package com.kelsos.mbrc.protocol

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.net.toUri
import com.kelsos.mbrc.content.activestatus.livedata.PlayingTrackState
import com.kelsos.mbrc.features.player.cover.CoverApi
import com.kelsos.mbrc.features.player.cover.CoverModel
import com.kelsos.mbrc.features.player.cover.CoverPayload
import com.kelsos.mbrc.common.ui.extensions.md5
import com.kelsos.mbrc.features.widgets.WidgetUpdater
import com.kelsos.mbrc.utilities.AppCoroutineDispatchers
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class UpdateCover(
  private val app: Application,
  private val updater: WidgetUpdater,
  private val mapper: Moshi,
  private val dispatchers: AppCoroutineDispatchers,
  private val coverApi: CoverApi,
  private val coverModel: CoverModel,
  private val playingTrackLiveDataProvider: PlayingTrackState
) : ProtocolAction {
  private val coverDir: File

  private val job = Job()
  private val scope = CoroutineScope(dispatchers.disk + job)

  init {
    coverDir = File(app.filesDir, COVER_DIR)
    scope.launch(dispatchers.disk) {
      playingTrackLiveDataProvider.set {
        copy(coverUrl = coverModel.coverPath)
      }
    }
  }

  override fun execute(message: ProtocolMessage) {
    val adapter = mapper.adapter(CoverPayload::class.java)
    val payload = adapter.fromJsonValue(message.data) ?: return

    if (payload.status == CoverPayload.NOT_FOUND) {
      playingTrackLiveDataProvider.set { copy(coverUrl = "") }
      updater.updateCover("")
    } else if (payload.status == CoverPayload.READY) {
      scope.launch(dispatchers.disk) {
        retrieveCover()
      }
    }
  }

  private suspend fun retrieveCover() {
    withContext(dispatchers.network) {
      try {
        val response = coverApi.getCover().await()
        val bitmap = getBitmap(response)
        val file = storeCover(bitmap)

        playingTrackLiveDataProvider.set {
          val coverUri = file.toUri().toString()
          coverModel.coverPath = coverUri
          copy(coverUrl = coverUri)
        }
        updater.updateCover(file.absolutePath)
      } catch (e: Exception) {
        removeCover(e)
      }
    }

    Timber.v("Message received for available cover")
    return
  }

  private fun getBitmap(base64: String): Bitmap {
    val decodedImage = Base64.decode(base64, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(decodedImage, 0, decodedImage.size)
    if (bitmap != null) {
      return bitmap
    } else {
      throw RuntimeException("Base64 was not an image")
    }
  }

  private fun removeCover(it: Throwable? = null) {
    clearPreviousCovers(0)

    it?.let {
      Timber.v(it, "Failed to store path")
    }

    playingTrackLiveDataProvider.set {
      copy(coverUrl = "")
    }
  }

  private fun storeCover(bitmap: Bitmap): File {
    checkIfExists()
    clearPreviousCovers()

    val file = temporaryCover()
    val fileStream = FileOutputStream(file)
    val success = bitmap.compress(JPEG, 100, fileStream)
    fileStream.close()

    val md5 = file.md5()
    val extension = file.extension
    val newFile = File(app.filesDir, "$md5.$extension")
    if (newFile.exists()) {
      file.delete()
      return newFile
    }

    if (success) {
      file.renameTo(newFile)
      Timber.v("file was renamed to ${newFile.absolutePath}")
      return newFile
    } else {
      throw RuntimeException("unable to store cover")
    }
  }

  private fun checkIfExists() {
    if (!coverDir.exists()) {
      coverDir.mkdir()
    }
  }

  private fun clearPreviousCovers(keep: Int = 1) {
    if (!coverDir.exists()) {
      return
    }
    val storedCovers = coverDir.listFiles()
    storedCovers.sortByDescending(File::lastModified)
    val elementsToKeep = if (storedCovers.size - keep < 0) 0 else storedCovers.size - keep
    storedCovers.takeLast(elementsToKeep).forEach {
      it.delete()
    }
  }

  private fun temporaryCover(): File {
    val file = File(app.cacheDir, TEMP_COVER)
    if (file.exists()) {
      file.delete()
    }
    return file
  }

  companion object {
    const val COVER_DIR = "cover"
    const val TEMP_COVER = "temp_cover.jpg"
  }
}