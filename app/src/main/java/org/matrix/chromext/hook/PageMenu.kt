package org.matrix.chromext.hook

import android.content.Context
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.proxy.PageMenuProxy
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

object PageMenuHook : BaseHook() {

  private fun getUrl(): String {
    return Chrome.getUrl()!!
  }

  override fun init() {

    val proxy = PageMenuProxy

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
          Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, sandBoxed)
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

    findMethod(proxy.customTabActivity) {
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
                    if ((it.args[3] as Boolean)) return@hookBefore
                    val ctx = mContext.get(it.thisObject) as Context
                    Resource.enrich(ctx)
                    val menu = it.args[0] as Menu
                    Chrome.updateTab(it.args[1])
                    val url = getUrl()

                    val iconRowMenu = menu.getItem(0)
                    if (iconRowMenu.hasSubMenu() && readerMode.isInit() && !Chrome.isBrave) {
                      val infoMenu = iconRowMenu.getSubMenu()!!.getItem(3)
                      infoMenu.setIcon(R.drawable.ic_book)
                      infoMenu.setEnabled(true)
                      val mId = infoMenu::class.java.getDeclaredField("mId")
                      mId.setAccessible(true)
                      mId.set(infoMenu, readerMode.ID)
                      mId.setAccessible(false)
                    }

                    val skip = menu.size() <= 20 || !(it.args[2] as Boolean)
                    // Inflate only for the main_menu, which has more than 20 items at least

                    if (skip && !isUserScript(url)) return@hookBefore
                    MenuInflater(ctx).inflate(R.menu.main_menu, menu)

                    val mItems = menu::class.java.getDeclaredField("mItems")
                    mItems.setAccessible(true)

                    @Suppress("UNCHECKED_CAST") val items = mItems.get(menu) as ArrayList<MenuItem>

                    // Show items with indices in main_menu.xml
                    val toShow = mutableListOf<Int>(1)

                    if (isDevToolsFrontEnd(url)) {
                      toShow.clear()
                    }

                    if (isUserScript(url)) {
                      toShow.clear()
                      toShow.add(2)
                      if (skip) {
                        // Show this menu for local preview pages (Custom Tab) of UserScripts
                        items.find { it.itemId == R.id.install_script_id }?.setVisible(true)
                        mItems.setAccessible(false)
                        return@hookBefore
                      }
                    }

                    if (isChromeXtFrontEnd(url)) {
                      toShow.clear()
                      toShow.addAll(listOf(3, 4))
                    }

                    if (!Chrome.isVivaldi &&
                        ctx.resources.configuration.smallestScreenWidthDp >=
                            DisplayMetrics.DENSITY_XXHIGH &&
                        toShow.size == 1 &&
                        toShow.first() == 1) {
                      iconRowMenu.setVisible(true)
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
}
