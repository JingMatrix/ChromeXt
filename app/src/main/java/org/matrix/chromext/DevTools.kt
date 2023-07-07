package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Log

const val DEV_FRONT_END = "https://chrome-devtools-frontend.appspot.com"

object DevTools {
  val forwardServer = ServerSocket(0)
  val detail = JSONObject(mapOf("port" to forwardServer.getLocalPort()))

  fun start() {
    thread {
      detail.put("pages", JSONArray(refreshDevTool()))
      val code = "window.dispatchEvent(new CustomEvent('cdp_info',{detail: ${detail}}));"
      if (UserScriptHook.isInit) {
        UserScriptProxy.evaluateJavascript(code)
      } else if (WebViewHook.isInit) {
        WebViewHook.evaluateJavascript(code)
      }

      val sockets = mutableListOf<Closeable>()
      runCatching {
            while (true) {
              val client = LocalSocket()

              connectDevTools(client)

              if (client.isConnected()) {
                Log.d("New connection to Chrome DevTools")
                sockets.add(client)
              } else {
                throw Exception("Fail to connect to Chrome DevTools localsocket")
              }

              val server = forwardServer.accept()
              sockets.add(server)
              forwardStreamThread(client.inputStream, server.outputStream, "client").start()
              forwardStreamThread(server.inputStream, client.outputStream, "server").start()
            }
          }
          .onFailure {
            val msg = it.message
            if (it is SocketTimeoutException) {
              Log.d("A socket not accepted due to timeout")
            } else if (msg == "Socket closed") {
              Log.d("A forwarding server is closed")
              sockets.forEach { it.close() }
            } else {
              Log.ex(it)
            }
          }
    }
  }

  private fun connectDevTools(client: LocalSocket) {
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

  private fun refreshDevTool(): String {
    val client = LocalSocket()
    connectDevTools(client)
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
          if (tag == "server") {
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

          input.pipe(output)

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

  private fun InputStream.pipe(
      out: OutputStream,
      debug: Boolean = false,
      bufferSize: Int = DEFAULT_BUFFER_SIZE
  ): Long {
    if (!debug) {
      return this.copyTo(out, bufferSize)
    }
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    Log.d(String(buffer))
    while (bytes >= 0) {
      out.write(buffer, 0, bytes)
      bytesCopied += bytes
      bytes = read(buffer)
    }
    return bytesCopied
  }
}
