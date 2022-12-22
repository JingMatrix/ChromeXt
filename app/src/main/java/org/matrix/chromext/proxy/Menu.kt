package org.matrix.chromext.proxy

import android.content.Context
import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import java.lang.reflect.Field
import org.matrix.chromext.R

// import org.matrix.chromext.R

class MenuProxy(ctx: Context) {

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"

  // Find following method prepareMenu, it has two params:
  // the first one is menu, the second one unknown
  // Also, using frida, this method invokes many other methods
  var PREPARE_MENU = "m"

  // Use frida to find getAppMenuLayoutId, whose return value is resource id
  var GET_APPMENU_LAYOUT_ID = "h"

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

  private var mContext: Field? = null
  private var mDecorView: Field? = null

  var chromeTabbedActivity: Class<*>? = null
  var appMenuPropertiesDelegateImpl: Class<*>? = null

  var isDeveloper: Boolean = false

  init {
    val sharedPref: SharedPreferences =
        ctx.getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref.getBoolean("developer", false)

    chromeTabbedActivity =
        ctx.getClassLoader().loadClass("org.chromium.chrome.browser.ChromeTabbedActivity")
    appMenuPropertiesDelegateImpl =
        ctx.getClassLoader().loadClass(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    mContext = appMenuPropertiesDelegateImpl!!.getDeclaredField(CONTEXT_FIELD)
    mDecorView = appMenuPropertiesDelegateImpl!!.getDeclaredField(DECOR_VIEW_FIELD)
  }

  fun injectLocalMenu(obj: Any, ctx: Context, menu: Menu) {
    val localPopup = PopupMenu(ctx, mDecorView!!.get(obj) as View)
    val localInflater: MenuInflater = localPopup.getMenuInflater()
    localInflater.inflate(R.menu.main_menu, menu)
  }
}
