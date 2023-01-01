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
    thread { pages = getPages() }
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
  private val threads = mutableListOf<forwardStreamThread>()
  var dispatched = false

  init {
    forwardSocket = forwarder
  }

  override fun run() {
    val client = LocalSocket()
    client.connect(LocalSocketAddress("chrome_devtools_remote"))

    if (client.isConnected()) {
      Log.d("Connected to Chrome DevTools")
    } else {
      return
    }

    val server = forwardSocket.accept()
    threads.add(forwardStreamThread(client.inputStream, server.outputStream))
    threads.add(forwardStreamThread(server.inputStream, client.outputStream))
    threads.forEach {
      Log.d("Starting forwardStreamThread")
      it.start()
    }
    dispatched = true
  }

  fun discard() {
    if (dispatched) {
      Log.d("Interrupting forwardStreamThread")
      threads.forEach { it.interrupt() }
      forwardSocket.close()
      Log.d("Forward server closed")
      Log.d("Interrupting forwarding thread")
    }
    interrupt()
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
    while (!interrupted()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        input.transferTo(output)
      } else {
        val bit = input.read()
        if (bit != -1) {
          output.write(bit)
        }
      }
    }
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
