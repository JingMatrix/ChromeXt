package org.matrix.chromext.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.reflect.Modifier
import java.util.ArrayList
import kotlin.math.roundToInt
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.*

object readerMode {
  val ID = 31415926
  private var readerModeManager: Class<*>? = null

  fun isInit(): Boolean {
    return readerModeManager != null
  }

  fun init(managerClass: Class<*>) {
    readerModeManager = managerClass
  }

  fun enable() {
    val mDistillerUrl =
        readerModeManager!!.declaredFields.filter { it.type == UserScriptProxy.gURL }.last()!!
    val activateReaderMode =
        // There exist other methods with the same signatures
        findMethod(readerModeManager!!) {
          parameterTypes.size == 0 && returnType == Void.TYPE && name != "destroy"
        }

    val manager = readerModeManager!!.declaredConstructors[0].newInstance(Chrome.getTab(), null)

    mDistillerUrl.setAccessible(true)
    mDistillerUrl.set(
        manager,
        UserScriptProxy.gURL.declaredConstructors[1].newInstance(
            "https://github.com/JingMatrix/ChromeXt"))
    mDistillerUrl.setAccessible(false)

    activateReaderMode.invoke(manager)
  }
}

object MenuHook : BaseHook() {

  private fun getUrl(): String {
    return Chrome.getUrl()!!
  }

  override fun init() {

    val proxy = MenuProxy

    if (Chrome.isEdge) {
      // Add eruda menu to page_info dialog
      var pageInfoController: Any? = null
      proxy.pageInfoControllerRef.declaredConstructors[0].hookAfter {
        pageInfoController = it.thisObject
      }
      proxy.pageInfoView.declaredConstructors[0].hookAfter {
        val ctx = it.args[0] as Context
        Resource.enrich(ctx)
        val url = getUrl()
        if (!url.startsWith("edge://")) {
          val erudaRow =
              proxy.pageInfoRowView.declaredConstructors[0].newInstance(ctx, null) as ViewGroup
          erudaRow.setVisibility(View.VISIBLE)
          val icon = proxy.mIcon.get(erudaRow) as ImageView
          icon.setImageResource(R.drawable.ic_devtools)
          val subTitle = proxy.mSubtitle.get(erudaRow) as TextView
          (subTitle.getParent() as? ViewGroup)?.removeView(subTitle)
          val title = proxy.mTitle.get(erudaRow) as TextView
          if (isChromeXtFrontEnd(url)) {
            title.setText(R.string.main_menu_developer_tools)
            erudaRow.setOnClickListener {
              Listener.on("inspectPages")
              pageInfoController!!.invokeMethod() { name == "destroy" }
            }
          } else if (isUserScript(url)) {
            title.setText(R.string.main_menu_install_script)
            erudaRow.setOnClickListener {
              val sandBoxed = shouldBypassSandbox(url)
              Chrome.evaluateJavascript(listOf("installScript(true);"), null, sandBoxed)
              pageInfoController!!.invokeMethod() { name == "destroy" }
            }
          } else {
            title.setText(R.string.main_menu_eruda_console)
            erudaRow.setOnClickListener {
              UserScriptProxy.evaluateJavascript(Local.openEruda)
              pageInfoController!!.invokeMethod() { name == "destroy" }
            }
          }
          (proxy.mRowWrapper!!.get(it.thisObject) as LinearLayout).addView(erudaRow)
        }
      }

      // readerMode.init(Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager"))
    } else {

      fun menuHandler(ctx: Context, id: Int): Boolean {
        if (id == readerMode.ID) {
          readerMode.enable()
          return true
        }
        when (ctx.resources.getResourceName(id)) {
          "org.matrix.chromext:id/extension_id" -> {
            Listener.on("extension")
          }
          "org.matrix.chromext:id/install_script_id" -> {
            val sandBoxed = shouldBypassSandbox(getUrl())
            Chrome.evaluateJavascript(listOf("installScript(true);"), null, sandBoxed)
          }
          "org.matrix.chromext:id/developer_tools_id" -> Listener.on("inspectPages")
          "org.matrix.chromext:id/eruda_console_id" ->
              UserScriptProxy.evaluateJavascript(Local.openEruda)
          "${ctx.packageName}:id/reload_menu_id" -> {
            val isLoading = proxy.mIsLoading.get(Chrome.getTab()) as Boolean
            if (!isLoading) return Listener.on("userAgentSpoof", getUrl()) != null
          }
        }
        return false
      }

      findMethod(proxy.chromeTabbedActivity) {
            // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
            parameterTypes contentDeepEquals arrayOf(Int::class.java, Boolean::class.java) &&
                returnType == Boolean::class.java
          }
          .hookBefore {
            if (menuHandler(it.thisObject as Context, it.args[0] as Int)) {
              it.result = true
            }
          }

      var findMenuHook: Unhook? = null
      findMenuHook =
          findMethod(proxy.chromeTabbedActivity) {
                parameterTypes.size == 0 &&
                    returnType.isInterface() &&
                    returnType.declaredMethods.size > 6
              }
              .hookAfter {
                findMenuHook!!.unhook()
                val appMenuPropertiesDelegateImpl = it.result::class.java.superclass as Class<*>
                val mContext =
                    findField(appMenuPropertiesDelegateImpl, true) { type == Context::class.java }
                mContext.setAccessible(true)

                if (Chrome.isBrave) {
                  // Brave browser replaces the first row menu with class AppMenuIconRowFooter,
                  // and it customize the menu by onFooterViewInflated() function in
                  // https://github.com/brave/brave-core/blob/master/android/java/
                  // org/chromium/chrome/browser/appmenu/BraveTabbedAppMenuPropertiesDelegate.java
                  findMethod(it.result::class.java, true) {
                        parameterTypes.size == 2 && getParameterTypes()[1] == View::class.java
                      }
                      // public void onFooterViewInflated(AppMenuHandler appMenuHandler, View view)
                      .hookAfter {
                        val appMenuIconRowFooter = it.args[1] as LinearLayout
                        val bookmarkButton =
                            (appMenuIconRowFooter.getChildAt(1) as LinearLayout).getChildAt(1)
                                as ImageButton
                        bookmarkButton.setVisibility(View.VISIBLE)
                        val ctx = mContext.get(it.thisObject) as Context
                        Resource.enrich(ctx)
                        bookmarkButton.setImageResource(R.drawable.ic_book)
                        bookmarkButton.setId(readerMode.ID)
                      }
                }

                findMethod(appMenuPropertiesDelegateImpl, true) {
                      parameterTypes contentDeepEquals
                          arrayOf(
                              Menu::class.java,
                              proxy.tab,
                              Boolean::class.java,
                              Boolean::class.java) && returnType == Void.TYPE
                    }
                    // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab
                    // currentTab, boolean canShowRequestDesktopSite, boolean isChromeScheme)
                    .hookBefore {
                      val ctx = mContext.get(it.thisObject) as Context
                      Resource.enrich(ctx)
                      val menu = it.args[0] as Menu
                      Chrome.updateTab(it.args[1])
                      val url = getUrl()
                      val skip =
                          (menu.size() <= 20 || !(it.args[2] as Boolean) || (it.args[3] as Boolean))
                      // Infalte only for the main_menu, which has more than 20 items at least

                      if (skip && !isUserScript(url)) return@hookBefore

                      if (!skip &&
                          menu.getItem(0).hasSubMenu() &&
                          readerMode.isInit() &&
                          !Chrome.isBrave) {
                        // The first menu item should be the @id/icon_row_menu_id

                        val infoMenu = menu.getItem(0).getSubMenu()!!.getItem(3)
                        infoMenu.setIcon(R.drawable.ic_book)
                        infoMenu.setEnabled(true)
                        val mId = infoMenu::class.java.getDeclaredField("mId")
                        mId.setAccessible(true)
                        mId.set(infoMenu, readerMode.ID)
                        mId.setAccessible(false)
                      }

                      MenuInflater(ctx).inflate(R.menu.main_menu, menu)

                      val mItems = menu::class.java.getDeclaredField("mItems")
                      mItems.setAccessible(true)

                      @Suppress("UNCHECKED_CAST")
                      val items = mItems.get(menu) as ArrayList<MenuItem>

                      // Show items with indices in main_menu.xml
                      val toShow = mutableListOf<Int>(1)

                      if (isDevToolsFrontEnd(url)) {
                        toShow.clear()
                      }

                      if (isUserScript(url)) {
                        toShow.clear()
                        toShow.add(2)
                        if (skip) {
                          items.find { it.itemId == R.id.install_script_id }?.setVisible(true)
                          mItems.setAccessible(false)
                          return@hookBefore
                        }
                      }

                      if (isChromeXtFrontEnd(url)) {
                        toShow.clear()
                        toShow.addAll(listOf(3, 4))
                      }

                      val position =
                          items
                              .withIndex()
                              .filter {
                                ctx.resources
                                    .getResourceName(it.value.getItemId())
                                    .endsWith("id/divider_line_id")
                              }
                              .map { it.index }[1]

                      toShow.forEach {
                        val newMenuItem: MenuItem = items[items.size - it]
                        newMenuItem.setVisible(true)
                        items.add(position + 1, newMenuItem)
                      }
                      for (i in 0..3) items.removeLast()
                      mItems.setAccessible(false)
                    }
              }

      var findReaderHook: Unhook? = null
      findReaderHook =
          findMethod(proxy.tabImpl) {
                parameterTypes contentDeepEquals arrayOf(proxy.emptyTabObserver) &&
                    returnType == Void.TYPE
                // There exist other methods with the same signatures
              }
              // public void addObserver(TabObserver observer)
              .hookAfter {
                val subType = it.args[0]::class.java
                if (subType.interfaces.size == 1 &&
                    findFieldOrNull(subType) { type == proxy.propertyModel } != null) {
                  readerMode.init(subType)
                  Chrome.updateTab(it.thisObject)
                  findReaderHook!!.unhook()
                }
              }
    }

    // var findSwipeRefreshHandler: Unhook? = null
    // findSwipeRefreshHandler =
    //     proxy.tabWebContentsUserData.declaredConstructors[0].hookAfter {
    //       val subType = it.thisObject::class.java
    //       if (subType.interfaces contentDeepEquals arrayOf(proxy.overscrollRefreshHandler))
    //		{
    //         findSwipeRefreshHandler!!.unhook()
    //         findMethod(subType) { name == "release" }
    //             // public void release(boolean allowRefresh)
    //             .hookBefore {
    //               if (it.args[0] as Boolean) {
    //                 it.args[0] = ScriptDbManager.on("userAgentSpoof", getUrl()) == null
    //               }
    //             }
    //       }
    //     }

    proxy.addPreferencesFromResource
        // public void addPreferencesFromResource(Int preferencesResId)
        .hookMethod {
          before {
            if (it.thisObject::class.java == proxy.developerSettings) {
              it.args[0] = R.xml.developer_preferences
            }
          }

          after {
            if (it.thisObject::class.java == proxy.developerSettings) {
              val refThis = it
              val preferences = mutableMapOf<String, Any>()
              arrayOf("eruda", "gesture_mod", "bookmark", "reset", "exit").forEach {
                preferences[it] = proxy.findPreference.invoke(refThis.thisObject, it)!!
              }
              proxy.setClickListener(preferences.toMap())
            }
          }
        }

    findMethod(proxy.developerSettings, true) {
          Modifier.isStatic(modifiers) &&
              parameterTypes contentDeepEquals
                  arrayOf(Context::class.java, String::class.java, Bundle::class.java)
          // public static Fragment instantiate(Context context,
          // String fname, @Nullable Bundle args)
        }
        .hookAfter {
          if (it.result::class.java == proxy.developerSettings) {
            Resource.enrich(it.args[0] as Context)
          }
        }

    findMethod(proxy.chromeTabbedActivity) { name == "onNewIntent" || name == "onMAMNewIntent" }
        .hookBefore {
          val intent = it.args[0] as Intent
          if (intent.hasExtra("ChromeXt")) {
            intent.setAction(Intent.ACTION_VIEW)
            var url = intent.getStringExtra("ChromeXt") as String
            intent.setData(Uri.parse(url))
          }
        }

    findMethod(WindowInsets::class.java) { name == "getSystemGestureInsets" }
        .hookBefore {
          val ctx = Chrome.getContext()
          val sharedPref = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
          if (sharedPref.getBoolean("gesture_mod", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              it.result = Insets.of(0, 0, 0, 0)
            }
            toggleGestureConflict(true)
          } else {
            toggleGestureConflict(false)
          }
        }

    findMethod(proxy.intentHandler, true) {
          Modifier.isStatic(getModifiers()) &&
              getParameterTypes() contentDeepEquals
                  arrayOf(Context::class.java, Intent::class.java, String::class.java)
        }
        // private static void startActivityForTrustedIntentInternal(Context context,
        // Intent intent, String componentClassName)
        .hookBefore {
          val intent = it.args[1] as Intent
          if (intent.hasExtra("org.chromium.chrome.browser.customtabs.MEDIA_VIEWER_URL")) {
            val fileurl = resolveContentUrl(intent.getData()!!.toString())!!
            if (fileurl.startsWith("/")) {
              intent.setData(Uri.parse("file://" + fileurl))
            }
          }
        }
  }

  private fun toggleGestureConflict(excludeSystemGesture: Boolean) {
    val activity = Chrome.getContext()
    if (activity is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity.window.decorView
      if (excludeSystemGesture) {
        val width = decoView.width
        val height = decoView.height
        val excludeHeight: Int = (activity.resources.displayMetrics.density * 100).roundToInt()
        decoView.setSystemGestureExclusionRects(
            // public Rect (int left, int top, int right, int bottom)
            listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
      } else {
        decoView.setSystemGestureExclusionRects(listOf(Rect(0, 0, 0, 0)))
      }
    }
  }
}
