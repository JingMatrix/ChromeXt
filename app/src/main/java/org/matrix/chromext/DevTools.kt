package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
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

  private var forwarding: Forward? = null
  private var port = 0
    set(value) {
      UserScriptHook.proxy!!.evaluateJavaScript(
          "window.dispatchEvent(new CustomEvent('cdp_port',{detail:${value}}))")
      field = value
    }

  var pages = ""

  fun start() {
    thread { pages = refreshDevTool() }
    if (forwarding != null) {
      // Awake front end
      val old_port = port
      port = 0
      port = old_port
      return
    }
    forwarding = Forward(bind())
    forwarding!!.start()
  }

  fun stop() {
    if (forwarding != null) {
      forwarding!!.discard()
      forwarding = null
      port = 0
    }
  }

  private fun bind(): ServerSocket {
    val forwardServer = ServerSocket(0)
    port = forwardServer.getLocalPort()
    Log.d("Forward server bind to port ${port}")
    return forwardServer
  }
}

private class Forward(forwarder: ServerSocket) : Thread() {

  private val forwardSocket: ServerSocket
  private var client = LocalSocket()
  private var server = Socket()
  var dispatched = false

  init {
    forwardSocket = forwarder
  }

  override fun run() {
    client.connect(LocalSocketAddress("chrome_devtools_remote"))

    if (client.isConnected()) {
      Log.d("Connected to Chrome DevTools")
    } else {
      return
    }

    server = forwardSocket.accept()
    forwardStreamThread(client.inputStream, server.outputStream).start()
    forwardStreamThread(server.inputStream, client.outputStream).start()
    dispatched = true
  }

  fun discard() {
    if (dispatched) {
      Log.d("Closing client socket")
      client.close()
      Log.d("Closing server socket")
      server.close()
      Log.d("Closing forward server")
      forwardSocket.close()
    }
  }
}

private class forwardStreamThread(inputStream: InputStream, outputStream: OutputStream) : Thread() {
  val input: InputStream
  val output: OutputStream
  init {
    input = inputStream
    output = outputStream
  }
  override fun run() {
    runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            while (true) {
              input.transferTo(output)
            }
          } else {
            while (true) {
              val bit = input.read()
              if (bit != -1) {
                output.write(bit)
              }
            }
          }
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            // The msg should be just `Socket closed`
            Log.ex(it)
          } else {
            Log.d(msg)
          }
        }
  }
}

private fun refreshDevTool(): String {
  val client = LocalSocket()
  client.connect(LocalSocketAddress("chrome_devtools_remote"))
  val writer = PrintWriter(client.getOutputStream())
  writer.print("GET /json HTTP/1.1\r\n")
  writer.print("\r\n")
  writer.flush()
  val reader = BufferedReader(InputStreamReader(client.getInputStream()))
  var header = true
  var pages = ""
  while (true) {
    val text = reader.readLine()
    if (!header) {
      pages += text + "\n"
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
  return pages
}
