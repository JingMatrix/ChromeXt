package org.matrix.chromext.hook

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.reflect.Modifier
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.DevTools
import org.matrix.chromext.R
import org.matrix.chromext.proxy.MenuProxy
import org.matrix.chromext.proxy.TabModel
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.openEruda
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
        readerModeManager!!.getDeclaredFields().filter { it.type == UserScriptProxy.gURL }.last()!!
    val activateReaderMode =
        // There exist other methods with the same signatures
        findMethod(readerModeManager!!) {
          getParameterTypes().size == 0 && getReturnType() == Void.TYPE
        }

    val manager =
        readerModeManager!!.getDeclaredConstructors()[0].newInstance(TabModel.getTab(), null)

    mDistillerUrl.setAccessible(true)
    mDistillerUrl.set(
        manager,
        UserScriptProxy.gURL
            .getDeclaredConstructors()[1]
            .newInstance("https://github.com/JingMatrix/ChromeXt"))
    mDistillerUrl.setAccessible(false)

    activateReaderMode.invoke(manager)
  }
}

object MenuHook : BaseHook() {

  override fun init() {

    val proxy = MenuProxy

    if (Chrome.isEdge) {
      // Add eruda menu to page_info dialog
      var pageInfoController: Any? = null
      proxy.pageInfoControllerRef.getDeclaredConstructors()[0].hookAfter {
        pageInfoController = it.thisObject
      }
      proxy.pageInfoView.getDeclaredConstructors()[0].hookAfter {
        val ctx = it.args[0] as Context
        ResourceMerge.enrich(ctx)
        val url = TabModel.getUrl()
        if (!url.startsWith("edge://")) {
          val erudaRow =
              proxy.pageInfoRowView.getDeclaredConstructors()[0].newInstance(ctx, null) as ViewGroup
          erudaRow.setVisibility(View.VISIBLE)
          val icon = proxy.mIcon.get(erudaRow) as ImageView
          icon.setImageResource(R.drawable.ic_devtools)
          val subTitle = proxy.mSubtitle.get(erudaRow) as TextView
          (subTitle.getParent() as? ViewGroup)?.removeView(subTitle)
          val title = proxy.mTitle.get(erudaRow) as TextView
          if (url.endsWith("/ChromeXt/")) {
            title.setText("Open developer tools")
            erudaRow.setOnClickListener {
              DevTools.start()
              pageInfoController!!.invokeMethod() { name == "destroy" }
            }
          } else {
            title.setText("Open eruda console")
            erudaRow.setOnClickListener {
              UserScriptProxy.evaluateJavascript(openEruda)
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
          "org.matrix.chromext:id/install_script_id" -> {
            if (TabModel.getUrl().startsWith("https://raw.githubusercontent.com/")) {
              Download.start(TabModel.getUrl(), "UserScript/script.js") {
                ScriptDbManager.on("installScript", it)
              }
            } else {
              UserScriptProxy.evaluateJavascript("installScript(true);")
            }
          }
          "org.matrix.chromext:id/developer_tools_id" -> DevTools.start()
          "org.matrix.chromext:id/eruda_console_id" -> UserScriptProxy.evaluateJavascript(openEruda)
          "${ctx.getPackageName()}:id/reload_menu_id" -> {
            return ScriptDbManager.on("userAgentSpoof", TabModel.getUrl()) != null
          }
        }
        return false
      }

      findMethod(proxy.chromeTabbedActivity) {
            // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
            getParameterTypes() contentDeepEquals arrayOf(Int::class.java, Boolean::class.java) &&
                getReturnType() == Boolean::class.java
          }
          .hookBefore {
            if (menuHandler(it.thisObject as Context, it.args[0] as Int)) {
              it.result = true
            }
          }

      var findMenuHook: Unhook? = null
      findMenuHook =
          findMethod(proxy.chromeTabbedActivity) {
                getParameterTypes().size == 0 &&
                    getReturnType().isInterface() &&
                    getReturnType().getDeclaredMethods().size > 6
              }
              .hookAfter {
                findMenuHook!!.unhook()
                val appMenuPropertiesDelegateImpl =
                    it.result::class.java.getSuperclass() as Class<*>
                val mContext =
                    appMenuPropertiesDelegateImpl.getDeclaredFields().find {
                      it.type == Context::class.java
                    }!!
                mContext.setAccessible(true)
                findMethod(appMenuPropertiesDelegateImpl, true) {
                      getParameterTypes() contentDeepEquals
                          arrayOf(
                              Menu::class.java,
                              proxy.tab,
                              Boolean::class.java,
                              Boolean::class.java) && getReturnType() == Void.TYPE
                    }
                    // protected void updateRequestDesktopSiteMenuItem(Menu menu, @Nullable Tab
                    // currentTab, boolean canShowRequestDesktopSite, boolean isChromeScheme)
                    .hookBefore {
                      val ctx = mContext.get(it.thisObject) as Context
                      ResourceMerge.enrich(ctx)
                      val menu = it.args[0] as Menu
                      TabModel.refresh(it.args[1])

                      if (menu.size() <= 20 ||
                          !(it.args[2] as Boolean) ||
                          (it.args[3] as Boolean)) {
                        // Infalte only for the main_menu, which has more than 20 items at least
                        return@hookBefore
                      }

                      if (menu.getItem(0).hasSubMenu() && readerMode.isInit()) {
                        // The first menu item shou be the row_menu
                        // Brave browser has already replaced this menu
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

                      if (TabModel.getUrl().endsWith("/ChromeXt/")) {
                        // Drop the Eruda console menu
                        items.removeLast()
                      }

                      if (TabModel.getUrl().endsWith(".user.js")) {
                        // Drop the Eruda console and the Dev Tools menus
                        items.removeLast()
                        items.removeLast()
                      }

                      val magicMenuItem: MenuItem = items.removeLast()
                      magicMenuItem.setVisible(true)
                      val position =
                          items
                              .withIndex()
                              .filter {
                                ctx.resources
                                    .getResourceName(it.value.getItemId())
                                    .endsWith("id/divider_line_id")
                              }
                              .map { it.index }[1]
                      items.add(position + 1, magicMenuItem)
                      mItems.setAccessible(false)
                    }
              }

      var findReaderHook: Unhook? = null
      findReaderHook =
          findMethod(proxy.tabImpl) {
                getParameterTypes() contentDeepEquals arrayOf(proxy.emptyTabObserver) &&
                    getReturnType() == Void.TYPE
                // There exist other methods with the same signatures
              }
              // public void addObserver(TabObserver observer)
              .hookAfter {
                val subType = it.args[0]::class.java
                if (subType.getInterfaces().size == 1 &&
                    subType.getDeclaredFields().find { it.getType() == proxy.propertyModel } !=
                        null) {
                  readerMode.init(subType)
                  TabModel.refresh(it.thisObject)
                  findReaderHook!!.unhook()
                }
              }
    }

    // var findSwipeRefreshHandler: Unhook? = null
    // findSwipeRefreshHandler =
    //     proxy.tabWebContentsUserData.getDeclaredConstructors()[0].hookAfter {
    //       val subType = it.thisObject::class.java
    //       if (subType.getInterfaces() contentDeepEquals arrayOf(proxy.overscrollRefreshHandler))
    //		{
    //         findSwipeRefreshHandler!!.unhook()
    //         findMethod(subType) { name == "release" }
    //             // public void release(boolean allowRefresh)
    //             .hookBefore {
    //               if (it.args[0] as Boolean) {
    //                 it.args[0] = ScriptDbManager.on("userAgentSpoof", TabModel.getUrl()) == null
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
              arrayOf("eruda", "exit", "gesture_mod").forEach {
                preferences[it] = proxy.findPreference.invoke(refThis.thisObject, it)!!
              }
              proxy.setClickListener(preferences.toMap())
            }
          }
        }

    findMethod(proxy.developerSettings, true) {
          Modifier.isStatic(getModifiers()) &&
              getParameterTypes() contentDeepEquals
                  arrayOf(Context::class.java, String::class.java, Bundle::class.java)
          // public static Fragment instantiate(Context context,
          // String fname, @Nullable Bundle args)
        }
        .hookAfter {
          if (it.result::class.java == proxy.developerSettings) {
            ResourceMerge.enrich(it.args[0] as Context)
          }
        }
  }
}
