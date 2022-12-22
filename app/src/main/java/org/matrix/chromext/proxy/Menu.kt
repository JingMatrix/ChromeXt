package org.matrix.chromext.proxy

import android.content.Context
import java.lang.reflect.Field

class MenuProxy(ctx: Context) {

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"

  // Find following method prepareMenu, it has two params:
  // the first one is menu, the second one unknown
  // Also, using frida, this method invokes many other methods
  var PREPARE_MENU = "m"

  // Grep ()Ljava/util/ArrayList to get method getCustomViewBinder
  val GET_CUSTOM_VIEW_BINDERS = "b"

  // Grep MobileMenuSettings to get method onMenuOrKeyboardAction
  // in the class ChromeTabbedActivity.smali
  var MENU_KEYBOARD_ACTION = "h0"

  companion object {
    // Find context and view fields from AppMenuPropertiesDelegateImpl class
    val CONTEXT_FIELD = "b"
    val DECOR_VIEW_FIELD = "h"
  }

  private val mContext: Field? = null
  private val mDecorView: Field? = null

  var chromeTabbedActivity: Class<*>? = null
  var appMenuPropertiesDelegateImpl: Class<*>? = null

  init {
    chromeTabbedActivity =
        ctx.getClassLoader().loadClass("org.chromium.chrome.browser.ChromeTabbedActivity")
    appMenuPropertiesDelegateImpl =
        ctx.getClassLoader().loadClass(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    // mContext = appMenuPropertiesDelegateImpl!!.getDeclaredField(CONTEXT_FIELD)
    // mDecorView = appMenuPropertiesDelegateImpl!!.getDeclaredField(DECOR_VIEW_FIELD)
  }

  // private fun getMenuInflater(obj: Any): MenuInflater {
  //   val popup = PopupMenu(mContext!!.get(obj) as Context, mDecorView!!.get(obj) as View)
  //   return popup.getMenuInflater()
  // }
}
