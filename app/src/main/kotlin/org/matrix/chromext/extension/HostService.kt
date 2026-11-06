package org.matrix.chromext.extension

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.matrix.chromext.utils.Log

/**
 * Hosts the extension background pages, in ChromeXt's own process.
 *
 * They cannot live in the browser process (a WebView there loads a second copy of Chromium's native
 * library and aborts the process) and they must not live in a browser tab either (a tab steals the
 * foreground when it is created and piles up across restarts, and Chrome cannot reload a restored
 * tab without selecting it). Chrome therefore binds to this service, which owns a WebView pointed
 * at the local host page.
 */
class HostService : Service() {

  private var webView: WebView? = null

  override fun onBind(intent: Intent?): IBinder? {
    Log.i("HostService bound, url=${intent?.getStringExtra(EXTRA_URL)}")
    val url = intent?.getStringExtra(EXTRA_URL)
    if (url != null) Handler(Looper.getMainLooper()).post { load(url) }
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    intent?.getStringExtra(EXTRA_URL)?.let { load(it) }
    return START_NOT_STICKY
  }

  private fun load(url: String) {
    // Only ever load the local host page, whatever the caller asks for
    if (!url.startsWith("http://127.0.0.1:")) {
      Log.e("Refusing to load an unexpected url: $url")
      stopSelf()
      return
    }
    val current =
        webView
            ?: runCatching { create() }
                .onFailure { Log.ex(it, "Fail to create the extension host WebView") }
                .getOrNull()
    if (current == null) {
      stopSelf()
      return
    }
    Log.i("Extension host page loading $url")
    current.loadUrl(url)
  }

  private fun create(): WebView {
    val view = WebView(this)
    view.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
      userAgentString = userAgentString + " ChromeXt"
    }
    view.webChromeClient =
        object : WebChromeClient() {
          override fun onConsoleMessage(message: String, line: Int, source: String) {
            Log.d("[host] $message (${source.substringAfterLast('/')}:$line)")
          }
        }
    view.webViewClient =
        object : WebViewClient() {
          override fun onPageFinished(web: WebView, url: String) {
            Log.d("Extension host page loaded")
          }

          override fun onReceivedError(
              view: WebView,
              request: android.webkit.WebResourceRequest,
              error: android.webkit.WebResourceError,
          ) {
            Log.e("Extension host page error: ${error.description} ${request.url}")
          }

          override fun onReceivedHttpError(
              view: WebView,
              request: android.webkit.WebResourceRequest,
              response: android.webkit.WebResourceResponse,
          ) {
            Log.e("Extension host http ${response.statusCode} for ${request.url}")
          }
        }
    view.layoutParams = ViewGroup.LayoutParams(1, 1)
    view.measure(
        View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 1, 1)
    webView = view
    return view
  }

  override fun onDestroy() {
    webView?.let {
      runCatching {
        it.stopLoading()
        it.loadUrl("about:blank")
        it.destroy()
      }
    }
    webView = null
    Log.i("Extension host service destroyed")
    super.onDestroy()
  }

  companion object {
    const val EXTRA_URL = "url"
  }
}
