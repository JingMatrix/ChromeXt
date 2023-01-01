package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log

object DevTools {

  private var server = Socket()
  private var running = false
  private var client = LocalSocket()
  private var port = 0
    set(value) {
      UserScriptHook.proxy!!.evaluateJavaScript(
          "window.dispatchEvent(new CustomEvent('cdp_port',{detail:${value}}))")
      field = value
    }

  var pages = ""

  fun toggle() {
    if (running) {
      running = false
      stop()
    }
    thread { pages = getPages() }
    connect()
    if (running) {
      val forwardServer = bind()
      thread {
        server = forwardServer.accept()
        thread { forward(client.getInputStream(), server.getOutputStream()) }
        thread { forward(server.getInputStream(), client.getOutputStream()) }
      }
    }
  }

  private fun bind(): ServerSocket {
    val forwardServer = ServerSocket(0)
    port = forwardServer.getLocalPort()
    Log.d("Forward server bind to port ${port}")
    return forwardServer
  }

  private fun connect() {
    client = LocalSocket()
    client.connect(LocalSocketAddress("chrome_devtools_remote"))
    if (client.isConnected()) {
      running = true
      Log.d("Connected to Chrome DevTools")
    } else {
      running = false
      Log.e("Fail to connect to Chrome DevTools")
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
    Log.d("Close all sockets")
  }
}

private fun getPages(): String {
  val client = LocalSocket()
  client.connect(LocalSocketAddress("chrome_devtools_remote"))
  val writer = PrintWriter(client.getOutputStream())
  writer.print("GET /json HTTP/1.1\r\n")
  writer.print("\r\n")
  writer.flush()
  val reader = BufferedReader(InputStreamReader(client.getInputStream()))
  var header = true
  var res = ""
  while (true) {
    val text = reader.readLine()
    if (!header) {
      res += text + "\n"
    }
    if (text.trim() == "") {
      header = false
    }
    if (text.startsWith("} ]")) {
      reader.close()
      writer.close()
      client.close()
      break
    }
  }
  return res
}
