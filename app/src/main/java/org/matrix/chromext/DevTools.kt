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
    cleanup()
    forwarding = Forward(bind())
    forwarding!!.start()
  }

  private fun cleanup() {
    if (forwarding != null) {
      forwarding!!.discard()
      forwarding = null
      port = 0
    }
  }

  private fun bind(): ServerSocket {
    val forwardServer = ServerSocket(0)
    port = forwardServer.getLocalPort()
    Log.d("Forward server starts at port ${port}")
    return forwardServer
  }
}

private class Forward(forwarder: ServerSocket) : Thread() {

  private val forwardSocket: ServerSocket
  private var client = LocalSocket()
  private var server = Socket()

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

    runCatching {
          server = forwardSocket.accept()
          forwardStreamThread(client.inputStream, server.outputStream, "client").start()
          forwardStreamThread(server.inputStream, client.outputStream, "server").start()
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            Log.ex(it)
          } else {
            Log.d("Forward server closed before accepting any connection")
          }
        }
  }

  fun discard() {
    Log.d("Closing client socket")
    client.close()
    Log.d("Closing server socket")
    server.close()
    Log.d("Closing forward server")
    forwardSocket.close()
  }
}

private class forwardStreamThread(
    inputStream: InputStream,
    outputStream: OutputStream,
    TAG: String
) : Thread() {
  val input: InputStream
  val output: OutputStream
  val tag: String
  init {
    input = inputStream
    output = outputStream
    tag = TAG
  }
  override fun run() {
    runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            input.transferTo(output)
          } else {
            while (true) {
              val bit = input.read()
              if (bit != -1) {
                output.write(bit)
              } else {
                break
              }
            }
          }
          Log.d("An inspecting seesion of ${tag} ends")
          input.close()
          output.close()
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            Log.ex(it)
          } else {
            Log.d(msg + " for ${tag} before a session ends")
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
      client.close()
      break
    }
  }
  return pages
}
