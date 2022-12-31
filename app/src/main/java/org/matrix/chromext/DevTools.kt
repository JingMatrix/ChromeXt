package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log

object DevTools {

  private var server = Socket()
  private var client = LocalSocket()
  private var running = false
  private var port = 0

  private fun notifyFrontEnd() {
    UserScriptHook.proxy!!.evaluateJavaScript(
        "window.dispatchEvent(new CustomEvent('cdp_port',{detail:${port}}))")
  }

  fun toggle() {
    if (running) {
      running = false
      stop()
      return
    }
    connect()
    if (!running) {
      return
    }
    val forwardServer = bind()
    thread {
      server = forwardServer.accept()
      thread { forward(client.getInputStream(), server.getOutputStream()) }
      thread { forward(server.getInputStream(), client.getOutputStream()) }
    }
  }

  private fun bind(): ServerSocket {
    val forwardServer = ServerSocket(0)
    port = forwardServer.getLocalPort()
    Log.d("Forward server bind to port ${port}")
    notifyFrontEnd()
    return forwardServer
  }

  private fun connect() {
    client = LocalSocket()
    client.connect(LocalSocketAddress("chrome_devtools_remote"))
    if (client.isConnected()) {
      running = true
      Log.d("Connected to Chrome DevTools")
    }
  }

  private fun forward(inStream: InputStream, outStream: OutputStream) {
    while (running) {
      outStream.write(inStream.read())
    }
  }

  private fun stop() {
    client.close()
    server.close()
    port = 0
    notifyFrontEnd()
    Log.d("Close all sockets")
  }
}
