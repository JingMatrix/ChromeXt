package org.matrix.chromext.proxy

import android.content.Context
import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.View.OnClickListener
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
  val GET_APPMENU_LAYOUT_ID = "h"

  // Grep ()Ljava/util/ArrayList to get method getCustomViewBinder
  val GET_CUSTOM_VIEW_BINDERS = "b"

  // Grep MobileMenuSettings to get method onMenuOrKeyboardAction
  // in the class ChromeTabbedActivity.smali
  var MENU_KEYBOARD_ACTION = "h0"

  // Find the only public method onCreatePreferences in DeveloperSettings.smali
  val DEVELOPER_SETTINGS = "W0"

  // Find the super class PreferenceFragmentCompat of DeveloperSettings
  var PREFERENCE_FRAGMENT_COMPAT = "pk2"

  // Grep (I)V to get method addPreferencesFromResource
  // in the class PreferenceFragmentCompat
  var ADD_PREFERENCES_FROM_RESOURCE = "T0"

  // Grep ()Landroidx/preference/PreferenceScreen to get method getPreferenceScreen
  // in the class PreferenceFragmentCompat
  var GET_PREFERENCE_SCREEN = "V0"

  // Grep (Ljava/lang/CharSequence;)Landroidx/preference/Preference;
  // to get method findPreference of PreferenceFragmentCompat
  var FIND_PREFERENCE = "U0"

  companion object {
    // Find context and view fields from AppMenuPropertiesDelegateImpl class
    val CONTEXT_FIELD = "b"
    val DECOR_VIEW_FIELD = "h"

    // Find field with Landroid/view/View$OnClickListener
    val CLICK_LISTENER_FIELD = "X"

    var sharedPref: SharedPreferences? = null
  }

  private val mContext: Field? = null
  private var mDecorView: Field? = null

  var chromeTabbedActivity: Class<*>? = null
  var appMenuPropertiesDelegateImpl: Class<*>? = null
  var developerSettings: Class<*>? = null
  var preferenceFragmentCompat: Class<*>? = null

  private var preference: Class<*>? = null
  private var mClickListener: Field? = null

  var isDeveloper: Boolean = false

  init {
    sharedPref = ctx.getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false)

    preference = ctx.getClassLoader().loadClass("androidx.preference.Preference")
    developerSettings =
        ctx.getClassLoader()
            .loadClass("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = ctx.getClassLoader().loadClass(PREFERENCE_FRAGMENT_COMPAT)
    chromeTabbedActivity =
        ctx.getClassLoader().loadClass("org.chromium.chrome.browser.ChromeTabbedActivity")
    appMenuPropertiesDelegateImpl =
        ctx.getClassLoader().loadClass(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    // mContext = appMenuPropertiesDelegateImpl!!.getDeclaredField(CONTEXT_FIELD)
    mDecorView = appMenuPropertiesDelegateImpl!!.getDeclaredField(DECOR_VIEW_FIELD)
    mClickListener = preference!!.getDeclaredField(CLICK_LISTENER_FIELD)
    mClickListener!!.setAccessible(true)
  }

  fun injectLocalMenu(obj: Any, ctx: Context, menu: Menu) {
    val localPopup = PopupMenu(ctx, mDecorView!!.get(obj) as View)
    val localInflater: MenuInflater = localPopup.getMenuInflater()
    localInflater.inflate(R.menu.main_menu, menu)
  }

  private object disableDevMode : OnClickListener {
    override fun onClick(v: View) {
      with(sharedPref!!.edit()) {
        putBoolean("developer", false)
        apply()
      }
    }
  }

  fun setClickListener(obj: Any, pref: String) {
    when (pref) {
      "exit" -> {
        mClickListener!!.set(obj, disableDevMode)
      }
    }
  }
}
