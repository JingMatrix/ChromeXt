package org.matrix.chromext.hook

import android.content.Context
import android.os.Handler
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

  fun activate() {
    @Suppress("UNCHECKED_CAST")
    val observers = (PageMenuProxy.mObservers.get(Chrome.getTab()) as Iterable<Any>).toList()
    val readerModeManager =
        observers.find {
          it::class.java.interfaces.size == 1 &&
              findFieldOrNull(it::class.java) { type == PageMenuProxy.propertyModel } != null
        }!!

    val activateReaderMode =
        // There exist other methods with the same signatures
        findMethod(readerModeManager::class.java) {
          parameterTypes.size == 0 && returnType == Void.TYPE && name != "destroy"
        }

    activateReaderMode.invoke(readerModeManager)
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
        readerMode.activate()
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
                  returnType.declaredMethods.size >= 6
            }
            // public AppMenuPropertiesDelegate createAppMenuPropertiesDelegate()
            .hookAfter {
              findMenuHook!!.unhook()
              val appMenuPropertiesDelegateImpl = it.result::class.java.superclass as Class<*>
              // Can be found by searching `Android.PrepareMenu`
              val mContext =
                  findField(appMenuPropertiesDelegateImpl, true) { type == Context::class.java }
              mContext.setAccessible(true)
              val mActivityTabProvider =
                  findField(appMenuPropertiesDelegateImpl, true) {
                    type.interfaces.size == 1 &&
                        findFieldOrNull(type.superclass) { type == Handler::class.java } != null
                  }
              mActivityTabProvider.setAccessible(true)

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
                    parameterTypes.size == 2 &&
                        parameterTypes.first() == Menu::class.java &&
                        returnType == Void.TYPE
                  }
                  // public void prepareMenu(Menu menu, AppMenuHandler handler)
                  .hookAfter inflate@{
                    val tabProvider = mActivityTabProvider.get(it.thisObject)!!
                    Chrome.updateTab(tabProvider.invokeMethod { name == "get" })
                    val ctx = mContext.get(it.thisObject) as Context
                    Resource.enrich(ctx)

                    val menu = it.args[0] as Menu
                    val url = getUrl()

                    val iconRowMenu = menu.getItem(0)
                    if (iconRowMenu.hasSubMenu() && !Chrome.isBrave) {
                      val infoMenu = iconRowMenu.getSubMenu()!!.getItem(3)
                      infoMenu.setIcon(R.drawable.ic_book)
                      infoMenu.setEnabled(true)
                      val mId = infoMenu::class.java.getDeclaredField("mId")
                      mId.setAccessible(true)
                      mId.set(infoMenu, readerMode.ID)
                      mId.setAccessible(false)
                    }

                    val skip = menu.size() <= 20 || isChromeScheme(url)
                    // Inflate only for the main_menu, which has more than 20 items at least

                    if (skip && !isUserScript(url)) return@inflate
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
                        return@inflate
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
  }
}
