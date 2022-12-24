package org.matrix.chromext.proxy

import android.content.Context

class JniProxy(ctx: Context) {

  // Here are some interesting JNI functions by looking at Frida
  // N.M09VlOh_("OmniboxUpdatedConnectionSecurityIndicators") <= false
  // seems to be a flags settings

  // N.M1WDPiaY("https://github.com/") <= "<instance: java.lang.Object, $className:
  // org.chromium.url.GURL>"
  // maybe a GURL generator

  // N.Me1sexxj("https://mobile.twitter.com/fangshimin") <= false
  // No idea

  // N.MTN9MD0o maybe related to cookies

  init {
    // Avoid unused variable suggestions
    ctx.getPackageName()
  }
}
