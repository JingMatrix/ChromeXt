package org.matrix.chromext.proxy

import android.content.Context
import android.view.View
import android.view.View.OnClickListener
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.invokeMethod

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

class MenuProxy() {
  // Grep chrome-distiller to get the class ReaderModeManager
  var READER_MODE_MANAGER = "js2"
  // Grep DomDistiller.InfoBarUsage for its method activateReaderMode
  // One get two methods, the other one is onClosed, which has
  // much shorter implements
  var ACTIVATE_READER_MODE = "Y0"
  // Get its current tab field
  var TAB_FIELD = "v"
  // Also we need its (second) gURL field
  var DISTILLER_URL_FIELD = "p"

  // Grep Android.PrepareMenu.OpenWebApkVisibilityCheck to get the class
  // org/chromium/chrome/browser/app/appmenu/AppMenuPropertiesDelegateImpl.java
  var APP_MENU_PROPERTIES_DELEGATE_IMPL = "of"
  // Grep (Landroid/view/Menu;Lorg/chromium/chrome/browser/tab/Tab;ZZ)V
  // to get method updateRequestDesktopSiteMenuItem of AppMenuPropertiesDelegateImpl
  var UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM = "q"

  // Grep MobileMenuSettings to get method onMenuOrKeyboardAction
  // in the class ChromeActivity.smali
  var MENU_KEYBOARD_ACTION = "h0"

  // Find the super class PreferenceFragmentCompat of DeveloperSettings.smali
  var PREFERENCE_FRAGMENT_COMPAT = "pk2"
  // Grep (I)V to get method addPreferencesFromResource of PreferenceFragmentCompat
  // var ADD_PREFERENCES_FROM_RESOURCE = "T0"
  // NOT NEEDED FOR MOST SPLIT VERSION
  // Inside the smali code of this method,
  // we see its super class androidx/fragment/app/c
  // has a method getContext()
  var GET_CONTEXT = "H0"

  // Grep "Preference already has a SummaryProvider set"
  // to get method setSummary of Preference
  var SET_SUMMARY = "Q"
  // And find its field with Landroid/view/View$OnClickListener
  var CLICK_LISTENER_FIELD = "X"

  // Grep ()Landroidx/preference/PreferenceScreen to get method getPreferenceScreen
  // in the class PreferenceFragmentCompat
  // val GET_PREFERENCE_SCREEN = "V0"

  // Compare method code length in Preference.smali,
  // the shortest one with a unknown class should be
  // setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener)
  // We should use its field instead of method
  // val ON_PREFERENCE_CLICK_LISTENER = "yk2"
  // val PREFERENCE_CLICK_LISTENER_FIELD = "l"

  val chromeTabbedActivity: Class<*>
  val customTabActivity: Class<*>
  val appMenuPropertiesDelegateImpl: Class<*>
  val developerSettings: Class<*>
  val preferenceFragmentCompat: Class<*>
  val readerModeManager: Class<*>
  val twoStatePreference: Class<*>
  val gURL: Class<*>
  // val onPreferenceClickListener: Class<*>? = null

  private val preference: Class<*>
  private val mClickListener: Field
  val mDistillerUrl: Field
  val mTab: Field
  // private val mOnClickListener: Field? = null

  val setChecked: Method

  val findPreference: Method

  val addPreferencesFromResource: Method

  var isDeveloper: Boolean = false

  init {
    if (Chrome.split && Chrome.version == 103) {
      READER_MODE_MANAGER = "BD2"
      ACTIVATE_READER_MODE = "X0"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Hg"
      UPDATE_REQUEST_DESKTOP_SITE_MENU_ITEM = "t"
      MENU_KEYBOARD_ACTION = "l0"
      PREFERENCE_FRAGMENT_COMPAT = "Fu2"
      SET_SUMMARY = "P"
    }

    if (!Chrome.split && Chrome.version >= 108) {
      READER_MODE_MANAGER = "bH2"
      TAB_FIELD = "p"
      DISTILLER_URL_FIELD = "j"
      ACTIVATE_READER_MODE = "Z0"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "lg"
      MENU_KEYBOARD_ACTION = "v0"
      PREFERENCE_FRAGMENT_COMPAT = "Ey2"
      GET_CONTEXT = "I0"
      // FIND_PREFERENCE = "U0"
      SET_SUMMARY = "P"
      CLICK_LISTENER_FIELD = "R"
    }

    if (!Chrome.split && Chrome.version >= 109) {
      READER_MODE_MANAGER = "KH2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "rg"
      MENU_KEYBOARD_ACTION = "w0"
      PREFERENCE_FRAGMENT_COMPAT = "qz2"
      GET_CONTEXT = "J0"
      SET_SUMMARY = "R"
      // CLICK_LISTENER_FIELD = "R"
    }

    if (Chrome.split && Chrome.version >= 109) {
      READER_MODE_MANAGER = "Ss2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "tf"
      MENU_KEYBOARD_ACTION = "i0"
      PREFERENCE_FRAGMENT_COMPAT = "al2"
      GET_CONTEXT = "I0"
      SET_SUMMARY = "R"
    }

    if (!Chrome.split && Chrome.version >= 110) {
      READER_MODE_MANAGER = "lB2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Fg"
      MENU_KEYBOARD_ACTION = "u0"
      PREFERENCE_FRAGMENT_COMPAT = "Ds2"
      GET_CONTEXT = "J0"
      // SET_SUMMARY = "R"
      // CLICK_LISTENER_FIELD = "R"
    }

    if (Chrome.split && Chrome.version >= 110) {
      READER_MODE_MANAGER = "pm2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Jf"
      MENU_KEYBOARD_ACTION = "h0"
      PREFERENCE_FRAGMENT_COMPAT = "ke2"
      // SET_SUMMARY = "R"
    }

    if (Chrome.split && Chrome.version >= 111) {
      READER_MODE_MANAGER = "io2"
      APP_MENU_PROPERTIES_DELEGATE_IMPL = "Of"
      MENU_KEYBOARD_ACTION = "B0"
      PREFERENCE_FRAGMENT_COMPAT = "Zf2"
    }

    val sharedPref =
        Chrome.getContext()
            .getSharedPreferences(
                Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false) || Chrome.isDev

    preference = Chrome.load("androidx.preference.Preference")
    // onPreferenceClickListener = Chrome.load(ON_PREFERENCE_CLICK_LISTENER)
    developerSettings =
        Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = Chrome.load(PREFERENCE_FRAGMENT_COMPAT)
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
    appMenuPropertiesDelegateImpl = Chrome.load(APP_MENU_PROPERTIES_DELEGATE_IMPL)
    twoStatePreference = Chrome.load("androidx/preference/g")
    gURL = Chrome.load("org.chromium.url.GURL")
    readerModeManager = Chrome.load(READER_MODE_MANAGER)
    mClickListener = preference.getDeclaredField(CLICK_LISTENER_FIELD)
    // mOnClickListener = preference!!.getDeclaredField(PREFERENCE_CLICK_LISTENER_FIELD)
    mClickListener.setAccessible(true)
    mTab = readerModeManager.getDeclaredField(TAB_FIELD)
    mTab.setAccessible(true)
    mDistillerUrl = readerModeManager.getDeclaredField(DISTILLER_URL_FIELD)
    mDistillerUrl.setAccessible(true)
    setChecked =
        findMethod(twoStatePreference) {
          getParameterCount() == 1 && getParameterTypes().first() == Boolean::class.java
        }
    findPreference =
        findMethod(preferenceFragmentCompat) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == CharSequence::class.java &&
              getReturnType() == preference
        }
    addPreferencesFromResource =
        findMethod(preferenceFragmentCompat) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == Int::class.java &&
              getReturnType() == Void.TYPE
        }
  }

  fun setClickListener(obj: Any, pref: String) {
    val ctx = Chrome.getContext()
    val sharedPref =
        ctx.getSharedPreferences(
            Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    when (pref) {
      "exit" -> {
        mClickListener.set(
            obj,
            object : OnClickListener {
              override fun onClick(v: View) {
                if (Chrome.isDev) {
                  Log.toast(ctx, "This function is not available for your Chrome build")
                  return
                }
                with(sharedPref.edit()) {
                  putBoolean("developer", false)
                  apply()
                  Log.toast(ctx, "Please restart Chrome to apply the changes")
                }
              }
            })
      }
      "eruda" -> {
        val version = sharedPref.getString("eruda_version", "unknown")
        var summary = "Click to install Eruda, size around 0.5 MiB"
        if (version != "unknown") {
          summary = "Current version: v" + version
        }
        obj.invokeMethod(summary) { name == SET_SUMMARY }
        mClickListener.set(
            obj,
            object : OnClickListener {
              override fun onClick(v: View) {
                Download.start(ERUD_URL, "Download/Eruda.js", true) {
                  val old_version = sharedPref.getString("eruda_version", "unknown")
                  var new_version = old_version
                  val verisonReg = Regex("""/npm/eruda@(?<version>[\d\.]+)/eruda""")
                  val vMatchGroup =
                      verisonReg.find(it.take(150))?.groups as? MatchNamedGroupCollection
                  if (vMatchGroup != null) {
                    new_version = vMatchGroup.get("version")?.value as String
                  }
                  if (old_version != new_version) {
                    with(sharedPref.edit()) {
                      putString("eruda_version", new_version)
                      apply()
                    }
                    Log.toast(ctx, "Updated to eruda v" + new_version)
                    obj.invokeMethod("Current version: v" + new_version) { name == SET_SUMMARY }
                  } else {
                    Log.toast(ctx, "Eruda is already the lastest")
                  }
                }
              }
            })
      }
      "gesture_mod" -> {
        setChecked.invoke(obj, sharedPref.getBoolean("gesture_mod", true))
        mClickListener.set(
            obj,
            object : OnClickListener {
              override fun onClick(v: View) {
                with(sharedPref.edit()) {
                  putBoolean("gesture_mod", !sharedPref.getBoolean("gesture_mod", true))
                  apply()
                }
                setChecked.invoke(obj, sharedPref.getBoolean("gesture_mod", true))
              }
            })
      }
    }
  }
}
