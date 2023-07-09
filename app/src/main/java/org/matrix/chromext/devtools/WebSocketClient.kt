package org.matrix.chromext.devtools

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook

fun connectDevTools(client: LocalSocket) {
  val address =
      if (UserScriptHook.isInit) {
        "chrome_devtools_remote"
      } else if (WebViewHook.isInit) {
        "webview_devtools_remote"
      } else {
        throw Exception("DevTools is started unexpectedly")
      }

  runCatching { client.connect(LocalSocketAddress(address)) }
      .onFailure { client.connect(LocalSocketAddress(address + "_" + Process.myPid())) }
}
