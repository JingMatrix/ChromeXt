package org.matrix.chromext.settings

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.View.OnClickListener
import java.io.FileReader
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.utils.Log

object DownloadEruda : OnClickListener {

  var id: Long? = null

  override fun onClick(v: View) {
    Chrome.getContext()
        .registerReceiver(
            writeSharedPreference, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

    val request = Request(Uri.parse("https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"))
    request.setDescription("Console for mobile browsers")
    request.setTitle("Eruda.js")
    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Eruda.js")
    val downloadManager =
        Chrome.getContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    id = downloadManager.enqueue(request)
  }

  object writeSharedPreference : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
      if (intent.getAction() == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
        if (downloadId == id) {
          val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
          val fd = downloadManager.openDownloadedFile(downloadId).getFileDescriptor()
          val eruda = FileReader(fd).use { it.readText() }
          val old_version = MenuProxy.getErudaVersion()
          val sharedPref = ctx.getSharedPreferences("Eruda", Context.MODE_PRIVATE)
          with(sharedPref!!.edit()) {
            putString("eruda", eruda)
            apply()
          }
          val new_version = MenuProxy.getErudaVersion()
          if (old_version != new_version) {
            Log.toast(ctx, "Updated to eruda v" + MenuProxy.getErudaVersion()!!)
          } else {
            Log.toast(ctx, "Eruda is already the lastest")
          }
          downloadManager.remove(downloadId)
        }
      }
    }
  }
}
