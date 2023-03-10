package org.matrix.chromext

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
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
          val bits = mutableListOf<Byte>()
          var originHeaderFound = Chrome.version < 111
          while (true) {
            val bit = input.read()
            if (bit != -1) {
              if (tag == "client" || originHeaderFound) {
                output.write(bit)
              } else {
                bits.add(bit.toByte())
                if (bit == 10) {
                  val header = String(bits.toByteArray())
                  if (header.startsWith("Origin: https://")) {
                    originHeaderFound = true
                  } else {
                    output.write(bits.toByteArray())
                    bits.clear()
                  }
                }
              }
            } else {
              break
            }
          }
          Log.d("An inspecting seesion from ${tag} ends")
          input.close()
          output.close()
        }
        .onFailure {
          val msg = it.message
          if (msg != "Socket closed") {
            Log.ex(it)
          } else {
            Log.d(msg + " for a ${tag} connection")
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
