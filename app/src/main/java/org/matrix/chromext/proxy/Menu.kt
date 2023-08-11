package org.matrix.chromext.proxy

import android.content.Context
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.io.File
import java.io.FileReader
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.text.Regex
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findFieldOrNull
import org.matrix.chromext.utils.findMethod

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

object MenuProxy {
  private var clickHooked: Boolean = false

  val developerSettings =
      Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
  val chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  val propertyModel = Chrome.load("org.chromium.ui.modelutil.PropertyModel")
  val tab = Chrome.load("org.chromium.chrome.browser.tab.Tab")

  // val tabWebContentsUserData =
  //     Chrome.load("org.chromium.chrome.browser.tab.TabFavicon").superclass as Class<*>
  // val overscrollRefreshHandler = Chrome.load("org.chromium.ui.OverscrollRefreshHandler")

  private val pageInfoController =
      Chrome.load("org.chromium.components.page_info.PageInfoController")
  val pageInfoRowView = Chrome.load("org.chromium.components.page_info.PageInfoRowView")
  val mIcon = pageInfoRowView.declaredFields[0]
  val mTitle = pageInfoRowView.declaredFields[1]
  val mSubtitle = pageInfoRowView.declaredFields[2]
  val pageInfoView =
      if (Chrome.isEdge) {
        Chrome.load("org.chromium.components.page_info.PageInfoView")
      } else {
        findField(pageInfoController) { type.superclass == FrameLayout::class.java }.type
      }
  val mRowWrapper = findFieldOrNull(pageInfoView) { type == LinearLayout::class.java }
  val pageInfoControllerRef =
      // A particular WebContentsObserver designed for PageInfoController
      findField(pageInfoController) {
            type.declaredFields.size == 1 &&
                (type.declaredFields[0].type == pageInfoController ||
                    type.declaredFields[0].type == WeakReference::class.java)
          }
          .type

  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").superclass as Class<*>
  val tabImpl = Chrome.load("org.chromium.chrome.browser.tab.TabImpl")

  val mIsLoading =
      tabImpl.declaredFields.run {
        val loadUrlParams = Chrome.load("org.chromium.content_public.browser.LoadUrlParams")
        val anchorIndex = indexOfFirst { it.type == loadUrlParams }
        slice(anchorIndex..size - 1).find { it.type == Boolean::class.java }!!
      }

  private val preference = Chrome.load("androidx.preference.Preference")
  private val mClickListener =
      findField(preference) {
        type == OnClickListener::class.java || type.interfaces.contains(OnClickListener::class.java)
      }
  private val setSummary =
      findMethod(preference) {
        parameterTypes contentDeepEquals arrayOf(CharSequence::class.java) &&
            returnType == Void.TYPE
      }
  private val twoStatePreference =
      Chrome.load("org.chromium.components.browser_ui.settings.ChromeSwitchPreference").superclass
          as Class<*>
  private val setChecked =
      findMethod(twoStatePreference, true) {
        parameterTypes contentDeepEquals arrayOf(Boolean::class.java)
      }

  private val preferenceFragmentCompat =
      if (Chrome.isBrave) {
        developerSettings.superclass.superclass
      } else {
        developerSettings.superclass
      }
          as Class<*>
  val findPreference =
      findMethod(preferenceFragmentCompat) {
        parameterTypes contentDeepEquals arrayOf(CharSequence::class.java) &&
            returnType == preference
      }
  val addPreferencesFromResource =
      preferenceFragmentCompat.declaredMethods
          .filter {
            it.parameterTypes contentDeepEquals arrayOf(Int::class.java) &&
                it.returnType == Void.TYPE
            // There exist other methods with the same signatures
          }
          .last()

  fun setClickListener(preferences: Map<String, Any>) {
    val ctx = Chrome.getContext()
    val sharedPref = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)

    var summary = "Click to install eruda, size around 0.5 MiB"
    val version = sharedPref.getString("eruda_version", "unknown")
    if (version != "unknown") {
      summary = "Current version: v" + version
    }
    setSummary.invoke(preferences["eruda"], summary)

    setChecked.invoke(
        preferences["gesture_mod"],
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            sharedPref.getBoolean("gesture_mod", true))

    var reset_confirming = 1
    val listeners =
        mapOf(
            "bookmark" to
                fun(_: Any) {
                  val bookmark = File(ctx.filesDir, "../app_chrome/Default/Bookmarks")
                  if (bookmark.exists()) {
                    var html =
                        "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file. It will be read and overwritten. DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n"
                    runCatching {
                          val data =
                              JSONObject(FileReader(bookmark).use { it.readText() })
                                  .getJSONObject("roots")

                          val DIV = 10000000
                          fun loopChildren(folder: JSONObject, indent: String = "\t") {
                            if (folder.optString("type") == "folder" && folder.has("children")) {
                              val children = folder.getJSONArray("children")
                              html +=
                                  indent +
                                      "<DT><H3 ADD_DATE=\"${folder.getLong("date_added") / DIV }\" LAST_MODIFIED=\"${folder.getLong("date_modified") / DIV}\" PERSONAL_TOOLBAR_FOLDER=\"true\">${folder.getString("name")}</H3>\n"
                              html += indent + "<DL><p>\n"
                              for (i in 0 until children.length()) {
                                val item = children.getJSONObject(i)
                                if (item.getString("type") == "url") {
                                  html +=
                                      indent +
                                          "\t<DT><A HREF=\"${item.getString("url")}\" ADD_DATE=\"${item.getLong("date_added") / DIV }\">${item.getString("name")}</A>\n"
                                } else if (item.getString("type") == "folder") {
                                  loopChildren(item, indent + "\t")
                                }
                              }
                              html += indent + "</DL><p>\n"
                            }
                          }

                          val bookmarks = data.names()!!
                          for (i in 0 until bookmarks.length()) {
                            loopChildren(data.getJSONObject(bookmarks.getString(i)))
                          }

                          html += "</DL><p>"
                          File(
                                  Environment.getExternalStoragePublicDirectory(
                                      Environment.DIRECTORY_DOWNLOADS),
                                  "Booksmarks.html")
                              .writeText(html)
                          Log.toast(
                              ctx,
                              "Bookmarks exported to " +
                                  Environment.DIRECTORY_DOWNLOADS +
                                  "/Booksmarks.html")
                        }
                        .onFailure { Log.ex(it) }
                  } else {
                    Log.toast(ctx, "Bookmarks data not found")
                  }
                },
            "eruda" to
                fun(obj: Any) {
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
                      setSummary.invoke(obj, "Current version: v" + new_version)
                    } else {
                      Log.toast(ctx, "Eruda is already the lastest")
                    }
                  }
                },
            "exit" to
                fun(_: Any) {
                  if (Chrome.isDev || Chrome.isVivaldi) {
                    Log.toast(ctx, "This function is not available for your Chrome build")
                    return
                  }
                  with(
                      ctx.getSharedPreferences(
                              Chrome.getContext().packageName + "_preferences",
                              Context.MODE_PRIVATE)
                          .edit()) {
                        putBoolean("developer", false)
                        apply()
                      }
                  Log.toast(ctx, "Please restart Chrome to apply the changes")
                },
            "gesture_mod" to
                fun(obj: Any) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val enabled = sharedPref.getBoolean("gesture_mod", true)
                    setChecked.invoke(obj, !enabled)
                    with(sharedPref.edit()) {
                      putBoolean("gesture_mod", !enabled)
                      apply()
                    }
                  } else {
                    setChecked.invoke(obj, false)
                    Log.toast(ctx, "Feature unavaible for your Chrome or Android versions")
                  }
                },
            "reset" to
                fun(_: Any) {
                  if (reset_confirming < 3) {
                    Log.toast(
                        ctx,
                        "Clik ${3 - reset_confirming} more times if you confirm to reset ChromeXt")
                    reset_confirming += 1
                    return
                  }
                  arrayOf("ChromeXt", "CosmeticFilter", "UserAgent", "CSPRule").forEach {
                    with(ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit()) {
                      clear()
                      apply()
                    }
                  }
                  ctx.deleteDatabase("userscript")
                  Log.toast(ctx, "ChromeXt data are reset")
                })

    mClickListener.setAccessible(true)
    preferences.forEach { (name, pref) ->
      mClickListener.set(
          pref,
          Proxy.newProxyInstance(
              Chrome.getContext().classLoader,
              arrayOf(mClickListener.type),
              object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<Any>) {
                  if (method.name == "onClick" && args.size == 1 && args[0] is View) {
                    listeners[name]?.invoke(pref)
                  }
                }
              }))
    }
    mClickListener.setAccessible(false)
  }
}
