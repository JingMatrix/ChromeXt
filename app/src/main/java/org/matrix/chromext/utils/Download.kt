package org.matrix.chromext.utils

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import java.io.File
import java.io.FileReader
import org.matrix.chromext.Chrome

object Download {

  private var id: Long? = null

  fun start(
      url: String,
      path: String,
      keep: Boolean = false,
      overwrite: Boolean = true,
      callback: ((content: String) -> Unit)? = null
  ) {
    runCatching {
          val request = Request(Uri.parse(url))

          val file = File(Chrome.getContext().getExternalFilesDir(null), path)
          if (file.exists()) {
            if (overwrite) {
              file.delete()
            } else {
              return
            }
          }

          request.setDestinationUri(Uri.fromFile(file))

          val receiver = createCallBack(keep, callback)
          Chrome.getContext()
              .registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

          val downloadManager =
              Chrome.getContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
          id = downloadManager.enqueue(request)
        }
        .onFailure { Log.ex(it) }
  }

  private fun createCallBack(
      keep: Boolean,
      callback: ((content: String) -> Unit)?
  ): BroadcastReceiver {
    return object : BroadcastReceiver() {
      override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getAction() == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
          val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
          if (downloadId == id) {
            val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fd = downloadManager.openDownloadedFile(downloadId).getFileDescriptor()
            callback?.invoke(FileReader(fd).use { it.readText() })
            if (!keep) {
              downloadManager.remove(downloadId)
            }
            Chrome.getContext().unregisterReceiver(this)
          }
        }
      }
    }
  }
}
