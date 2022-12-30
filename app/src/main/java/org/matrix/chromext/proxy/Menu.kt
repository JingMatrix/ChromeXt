package org.matrix.chromext.proxy

import android.content.Context
import java.lang.reflect.Field
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.settings.DownloadEruda
import org.matrix.chromext.settings.ExitDevMode
import org.matrix.chromext.utils.invokeMethod

class MenuProxy() {

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"

  // Find following method prepareMenu, it has two params:
  // the first one is menu, the second one unknown
  // Also, using frida, this method invokes many other methods
  // val PREPARE_MENU = "m"

  // Grep (Landroid/view/Menu;Lorg/chromium/chrome/browser/tab/Tab;ZZ)V
  // to get method updateRequestDesktopSiteMenuItem of AppMenuPropertiesDelegateImpl
  var UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM = "q"

  // Use frida to find getAppMenuLayoutId, whose return value is resource id
  // val GET_APPMENU_LAYOUT_ID = "h"

  // Grep ()Ljava/util/ArrayList to get method getCustomViewBinder
  // val GET_CUSTOM_VIEW_BINDERS = "b"

  // Grep MobileMenuSettings to get method onMenuOrKeyboardAction
  // in the class ChromeTabbedActivity.smali
  var MENU_KEYBOARD_ACTION = "h0"

  // Find the only public method onCreatePreferences in DeveloperSettings.smali
  // val DEVELOPER_SETTINGS = "W0"

  // Find the super class PreferenceFragmentCompat of DeveloperSettings
  var PREFERENCE_FRAGMENT_COMPAT = "pk2"

  // Grep (I)V to get method addPreferencesFromResource
  // in the class PreferenceFragmentCompat
  var ADD_PREFERENCES_FROM_RESOURCE = "T0"
  // Inside the smali code of this method,
  // we see its super class androidx/fragment/app/c
  // has a method getContext()
  var GET_CONTEXT = "I0"

  // Grep ()Landroidx/preference/PreferenceScreen to get method getPreferenceScreen
  // in the class PreferenceFragmentCompat
  // val GET_PREFERENCE_SCREEN = "V0"

  // Grep (Ljava/lang/CharSequence;)Landroidx/preference/Preference;
  // to get method findPreference of PreferenceFragmentCompat
  var FIND_PREFERENCE = "U0"

  // Grep "Preference already has a SummaryProvider set"
  // to get method setSummary of Preference
  // and the corresponding field
  var SET_SUMMARY = "Q"

  // Compare method code length in Preference.smali,
  // the shortest one with a unknown class should be
  // setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener)
  // We should use its field instead of method
  // val ON_PREFERENCE_CLICK_LISTENER = "yk2"
  // val PREFERENCE_CLICK_LISTENER_FIELD = "l"

  // Find field with Landroid/view/View$OnClickListener
  var CLICK_LISTENER_FIELD = "X"

  companion object {
    fun getErudaVersion(): String? {
      val sharedPref = Chrome.getContext().getSharedPreferences("Eruda", Context.MODE_PRIVATE)
      if (!sharedPref.contains("eruda")) {
        return null
      }
      var eruda = sharedPref.getString("eruda", "")
      if (eruda == "") {
        return null
      } else {
        if (eruda!!.length > 200) {
          eruda = eruda.take(150)
        }
        val verisonReg = Regex("""/npm/eruda@(?<version>[\d\.]+)/eruda""")
        val vMatchGroup = verisonReg.find(eruda)?.groups as? MatchNamedGroupCollection
        if (vMatchGroup != null) {
          return vMatchGroup.get("version")?.value as String
        }
        return "unknown"
      }
    }
  }

  val chromeTabbedActivity: Class<*>
  val appMenuPropertiesDelegateImpl: Class<*>
  val developerSettings: Class<*>
  val preferenceFragmentCompat: Class<*>
  // val onPreferenceClickListener: Class<*>? = null

  private val preference: Class<*>
  private val mClickListener: Field
  // private val mOnClickListener: Field? = null

  var isDeveloper: Boolean = false

  init {
    if (!Chrome.split) {
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "lg"
      MENU_KEYBOARD_ACTION = "v0"
      PREFERENCE_FRAGMENT_COMPAT = "Ey2"
      SET_SUMMARY = "P"
      CLICK_LISTENER_FIELD = "R"
    }

    val sharedPref =
        Chrome.getContext()
            .getSharedPreferences("com.android.chrome_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false)

    preference = Chrome.load("androidx.preference.Preference")
    // onPreferenceClickListener = Chrome.load(ON_PREFERENCE_CLICK_LISTENER)
    developerSettings =
        Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = Chrome.load(PREFERENCE_FRAGMENT_COMPAT)
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    appMenuPropertiesDelegateImpl = Chrome.load(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    mClickListener = preference.getDeclaredField(CLICK_LISTENER_FIELD)
    // mOnClickListener = preference!!.getDeclaredField(PREFERENCE_CLICK_LISTENER_FIELD)
    mClickListener.setAccessible(true)
  }

  fun setClickListenerAndSummary(obj: Any, pref: String) {
    when (pref) {
      "exit" -> {
        mClickListener.set(obj, ExitDevMode)
      }
      "eruda" -> {
        mClickListener.set(obj, DownloadEruda)
        val version = getErudaVersion()
        var summary = "Click to install Eruda, size around 0.5 MiB"
        if (version != null) {
          summary = "Current version: " + version
        }
        obj.invokeMethod(summary) { name == SET_SUMMARY }
      }
    }
  }
}
