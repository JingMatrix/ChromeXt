package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.net.ServerSocket
import org.matrix.chromext.utils.Log

object DevTools {

  val server = ServerSocket()
  val client = LocalSocket()

  private fun init() {}

  fun toggle() {}

  private fun connect() {
    client.connect(LocalSocketAddress("chrome_devtools_remote"))
    if (client.isConnected()) {
      Log.i("Connected")
    }
  }
}
