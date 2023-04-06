package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log

const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

object DevTools {

  private var forwarding: Forward? = null
  private var port = 0
    set(value) {
      UserScriptHook.proxy!!.evaluateJavaScript(
          "setTimeout(()=>{window.dispatchEvent(new CustomEvent('cdp_port',{detail:${value}}))},100)")
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

  init {
    forwardSocket = forwarder
  }

  override fun run() {
    val sockets = mutableListOf<Closeable>()
    runCatching {
          while (true) {
            val client = LocalSocket()
            client.connect(LocalSocketAddress("chrome_devtools_remote"))

            if (client.isConnected()) {
              Log.d("New connection to Chrome DevTools")
              sockets.add(client)
            } else {
              Log.e("Fail to connect to Chrome DevTools localsocket")
              return
            }

            val server = forwardSocket.accept()
            sockets.add(server)
            forwardStreamThread(client.inputStream, server.outputStream, "client").start()
            forwardStreamThread(server.inputStream, client.outputStream, "server").start()
          }
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            Log.ex(it)
          } else {
            Log.d("Forward server closed")
            sockets.forEach { it.close() }
          }
        }
  }

  fun discard() {
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
          if (Chrome.version > 110 && tag == "server") {
            val HEADERS_BEFORE_SIZE = 512
            // Hard coded number, approximate size to contains the Origin header
            val buffer = ByteArray(HEADERS_BEFORE_SIZE)
            input.read(buffer)
            output.write(
                String(buffer)
                    .split("\r\n")
                    .filter { it != "Origin: ${DEV_FRONT_END}" }
                    .joinToString("\r\n")
                    .toByteArray())
          }

          input.copyTo(output)

          Log.d("An inspecting seesion from ${tag} ends")
          input.close()
          output.close()
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            Log.ex(it)
          } else {
            Log.d(msg + " in a ${tag} connection")
          }
        }
  }
}

private fun refreshDevTool(): String {
  val client = LocalSocket()
  client.connect(LocalSocketAddress("chrome_devtools_remote"))
  client.outputStream.write("GET /json HTTP/1.1\r\n\r\n".toByteArray())
  var pages = ""
  client.inputStream.bufferedReader().use {
    while (true) {
      val line = it.readLine()
      if (line.length == 0) {
        pages = ""
      }
      pages += line + "\n"
      if (line == "} ]") {
        client.close()
        break
      }
    }
  }
  return pages
}
