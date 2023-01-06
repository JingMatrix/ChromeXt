package org.matrix.chromext.settings

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import org.matrix.chromext.Chrome
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

object DownloadEruda : OnClickListener {
  override fun onClick(v: View) {
    Download.start(ERUD_URL, "Download/Eruda.js") {
      val old_version = MenuProxy.getErudaVersion()
      val ctx = Chrome.getContext()
      val sharedPref = ctx.getSharedPreferences("Eruda", Context.MODE_PRIVATE)
      with(sharedPref!!.edit()) {
        putString("eruda", it)
        apply()
      }
      val new_version = MenuProxy.getErudaVersion()
      if (old_version != new_version) {
        Log.toast(ctx, "Updated to eruda v" + MenuProxy.getErudaVersion()!!)
      } else {
        Log.toast(ctx, "Eruda is already the lastest")
      }
    }
  }
}
