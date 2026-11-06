package org.matrix.chromext.extension

import android.content.Intent
import android.net.Uri
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

/**
 * Runs extension background pages inside a dedicated Chrome tab.
 *
 * A WebView cannot be used here: instantiating one inside the browser process loads the WebView
 * provider's native library next to Chromium's own, which aborts the process (verified on Chrome
 * 156 / Android 16). So the background pages live as iframes of the local host page instead, one
 * iframe per extension, each served from its own loopback port to keep origins apart.
 */
object Background {

  private var hostTab: Any? = null
  private var hostReady = false
  private var opening = false
  private var lastOpenAttempt = 0L

  fun hostUrl(): String = "http://127.0.0.1:${ExtServer.managerPort}/host.html"

  fun findHostTab(): Any? {
    val url = hostUrl()
    hostTab?.let { tab ->
      val current = runCatching { Chrome.getUrl(tab) }.getOrNull()
      if (current != null && current.startsWith(url)) return tab
    }
    val tabs = Chrome.tabList()
    val found = tabs.firstOrNull {
      runCatching { Chrome.getUrl(it) }.getOrNull()?.startsWith(url) == true
    }
    if (found == null && tabs.isNotEmpty()) {
      Log.d(
          "Host tab not found among " + tabs.map { runCatching { Chrome.getUrl(it) }.getOrNull() })
    }
    hostTab = found
    return found
  }

  fun isReady(): Boolean = hostReady && findHostTab() != null

  fun status(): String =
      when {
        findHostTab() == null -> "stopped"
        hostReady -> "running"
        else -> "loading"
      }

  /** Make sure the host tab exists, opening it when needed. Never blocks. */
  fun ensureHost() {
    if (findHostTab() != null || opening) return
    val now = System.currentTimeMillis()
    if (now - lastOpenAttempt < 10000) return
    lastOpenAttempt = now
    opening = true
    runCatching {
      val intent =
          Intent(Intent.ACTION_VIEW, Uri.parse(hostUrl()))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              .setPackage(Chrome.packageName)
      Chrome.getContext().startActivity(intent)
      Log.i("Opening the extension host tab at ${hostUrl()}")
    }
        .onFailure {
          opening = false
          Log.ex(it, "Fail to open the extension host tab")
        }
  }

  /**
   * Queue code for the host page.
   *
   * Delivery goes through the loopback server instead of evaluating in the tab: the host page polls
   * for commands, which keeps this side free of tab reflection and works whichever tab is in front.
   */
  fun evaluate(code: String): Boolean {
    if (findHostTab() == null) ensureHost()
    HostQueue.push(code)
    return true
  }

  fun onHostReady() {
    if (!hostReady) Log.d("Extension host page is ready")
    hostReady = true
    opening = false
  }

  fun openHostPage() = ensureHost()

  /** Called when the host tab goes away, so the next call reopens it. */
  fun stop() {
    hostTab = null
    hostReady = false
    opening = false
  }
}
