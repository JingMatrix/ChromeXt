package org.matrix.chromext.devtools

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.util.Base64
import java.io.OutputStream
import java.security.SecureRandom
import kotlin.experimental.xor
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.randomString

class DevToolClient(tabId: String) : LocalSocket() {

  val tabId = tabId
  private var cspBypassed = false
  private var id = 1
  private var mClosed = false

  init {
    connectDevTools(this)
    val request =
        arrayOf(
            "GET /devtools/page/${tabId} HTTP/1.1",
            "Connection: Upgrade",
            "Upgrade: websocket",
            "Sec-WebSocket-Version: 13",
            "Sec-WebSocket-Key: ${Base64.encodeToString(randomString(16).toByteArray(), Base64.DEFAULT).trim()}")
    Log.d("Start inspecting tab ${tabId}")
    outputStream.write((request.joinToString("\r\n") + "\r\n\r\n").toByteArray())
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE / 8)
    inputStream.read(buffer)
    if (String(buffer).split("\r\n")[0] != "HTTP/1.1 101 WebSocket Protocol Handshake") {
      close()
      Log.d("Fail to inspect tab ${tabId} with response\n" + String(buffer), true)
    }
  }

  override fun close() {
    super.close()
    mClosed = true
  }

  override fun isClosed(): Boolean {
    return mClosed
  }

  fun command(id: Int?, method: String, params: JSONObject?) {
    if (isClosed()) {
      return
    }
    val msg = JSONObject(mapOf("method" to method))
    if (params != null) msg.put("params", params)

    if (id == null) {
      msg.put("id", this.id)
      this.id += 1
    } else {
      msg.put("id", id)
    }

    WebSocketFrame(msg.toString()).write(outputStream)
  }

  fun bypassCSP(bypass: Boolean) {
    if (cspBypassed == bypass) return
    command(null, "Page.enable", JSONObject())
    command(null, "Page.setBypassCSP", JSONObject().put("enabled", bypass))
    cspBypassed = bypass
    if (bypass) DevSessions.add(this)
  }

  fun evaluateJavascript(script: String) {
    command(this.id, "Runtime.evaluate", JSONObject(mapOf("expression" to script)))
    this.id += 1
  }

  fun ping(msg: String = "heartbeat") {
    WebSocketFrame(msg, 0x9).write(outputStream)
  }

  fun listen(callback: (JSONObject) -> Unit = { msg -> Log.d(msg.toString()) }) {
    runCatching {
          while (!isClosed()) {
            val type = inputStream.read()
            if (type == -1) {
              break
            } else if (type == (0x80 or 0x1) || type == (0x80 or 0xA)) {
              var len = inputStream.read()
              if (len == 0x7e) {
                len = inputStream.read() shl 8
                len += inputStream.read()
              } else if (len == 0x7f) {
                len = 0
                for (i in 0 until 8) {
                  len = len or (inputStream.read() shl (8 * (7 - i)))
                }
              } else if (len > 0x7d) {
                throw Exception("Invalid frame length ${len}")
              }
              var offset = 0
              val buffer = ByteArray(len)
              while (offset != len) offset += inputStream.read(buffer, offset, len - offset)
              val frame = String(buffer)

              if (type == (0x80 or 0xA)) {
                callback(JSONObject(mapOf("pong" to frame)))
              } else {
                callback(JSONObject(frame))
              }
            } else {
              throw Exception("Invalid frame type ${type}")
            }
          }
        }
        .onFailure {
          if (!isClosed()) {
            Log.e("Fail to listen at tab ${tabId}: ${it.message}")
          }
        }
    close()
  }
}

class WebSocketFrame(msg: String?, opcode: Int = 0x1) {
  private val mFin: Int
  private val mRsv1: Int
  private val mRsv2: Int
  private val mRsv3: Int
  private val mOpcode: Int
  private val mPayload: ByteArray

  var mMask: Boolean = false

  init {
    mFin = 0x80
    mRsv1 = 0x00
    mRsv2 = 0x00
    mRsv3 = 0x00
    mOpcode = opcode
    mPayload =
        if (msg == null) {
          ByteArray(0)
        } else {
          msg.toByteArray()
        }
  }

  fun write(os: OutputStream) {
    writeFrame0(os)
    writeFrame1(os)
    writeFrameExtendedPayloadLength(os)
    val maskingKey = ByteArray(4)
    SecureRandom().nextBytes(maskingKey)
    os.write(maskingKey)
    writeFramePayload(os, maskingKey)
  }

  private fun writeFrame0(os: OutputStream) {
    val b = mFin or mRsv1 or mRsv2 or mRsv1 or (mOpcode and 0x0F)
    os.write(b)
  }

  private fun writeFrame1(os: OutputStream) {
    var b = 0x80
    val len = mPayload.size
    if (len <= 0x7d) {
      b = b or len
    } else if (len <= 0xffff) {
      b = b or 0x7e
    } else {
      b = b or 0x7f
    }
    os.write(b)
  }

  private fun writeFrameExtendedPayloadLength(os: OutputStream) {
    var len = mPayload.size
    val buf: ByteArray
    if (len <= 0x7d) {
      return
    } else if (len <= 0xffff) {
      buf = ByteArray(2)
      buf[1] = (len and 0xff).toByte()
      buf[0] = ((len shr 8) and 0xff).toByte()
    } else {
      buf = ByteArray(8)
      for (i in 0 until 8) {
        buf[7 - i] = (len and 0xff).toByte()
        len = len shr 8
      }
    }
    os.write(buf)
  }

  private fun writeFramePayload(os: OutputStream, mask: ByteArray) {
    os.write(mPayload.mapIndexed { index, byte -> byte xor mask[index.rem(4)] }.toByteArray())
  }
}

fun connectDevTools(client: LocalSocket) {
  val address =
      if (UserScriptHook.isInit) {
        if (Chrome.isSamsung) {
          "Terrace_devtools_remote"
        } else {
          "chrome_devtools_remote"
        }
      } else if (Chrome.isMi) {
        "miui_webview_devtools_remote"
      } else if (WebViewHook.isInit) {
        "webview_devtools_remote"
      } else {
        throw Exception("DevTools started unexpectedly")
      }

  runCatching { client.connect(LocalSocketAddress(address)) }
      .onFailure { client.connect(LocalSocketAddress(address + "_" + Process.myPid())) }
}
