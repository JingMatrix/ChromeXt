package org.matrix.chromext.hook

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.matrix.chromext.utils.Log

object DevSocketHook : BaseHook() {
  override fun init(ctx: Context) {
    val devSocket = LocalSocket()
	// Not working, connection refused
    devSocket.connect(LocalSocketAddress("chrome_devtools_remote"))
    Log.i("${devSocket.toString()} status: ${devSocket.isConnected()}")
  }
}
