package org.matrix.chromext.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

/**
 * Binds the browser to [HostService], which lives in ChromeXt's own process.
 *
 * A bound service stays alive as long as the browser is, needs no notification and disappears with
 * the browser, which is exactly the lifetime a background page should have.
 */
object HostClient {

  private const val PACKAGE = "org.matrix.chromext"

  private var bound = false
  private var connecting = false
  private var lastAttempt = 0L
  private var connection: ServiceConnection? = null

  fun ensure(url: String): Boolean {
    val context = runCatching { Chrome.getContext() }.getOrNull() ?: return false
    if (bound) return true
    val now = System.currentTimeMillis()
    if (connecting || now - lastAttempt < 5000) return bound
    lastAttempt = now
    connecting = true
    return runCatching {
          val intent =
              Intent()
                  .setComponent(ComponentName(PACKAGE, HostService::class.java.name))
                  .putExtra(HostService.EXTRA_URL, url)
          val conn =
              object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                  bound = true
                  connecting = false
                  Log.i("Bound to the extension host service")
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                  bound = false
                  Log.i("Extension host service disconnected")
                }
              }
          connection = conn
          context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }
        .onFailure { Log.ex(it, "Fail to bind the extension host service") }
        .getOrDefault(false)
        .also { if (!it) connecting = false }
  }

  private var lastPoke = 0L

  /**
   * Wake the host process.
   *
   * Android freezes a background process even when it holds a binding, and a frozen host page stops
   * polling, so every delivery pokes the service when the polls have gone quiet.
   */
  fun poke(url: String) {
    val now = System.currentTimeMillis()
    if (now - lastPoke < 3000) return
    lastPoke = now
    val context = runCatching { Chrome.getContext() }.getOrNull() ?: return
    runCatching {
      val intent =
          Intent()
              .setComponent(ComponentName(PACKAGE, HostService::class.java.name))
              .putExtra(HostService.EXTRA_URL, url)
      context.startService(intent)
    }
        .onFailure { Log.d("Cannot poke the host service: ${it.message}") }
  }

  fun release() {
    val context = runCatching { Chrome.getContext() }.getOrNull() ?: return
    connection?.let { runCatching { context.unbindService(it) } }
    connection = null
    bound = false
  }
}
