package org.matrix.chromext.hook

import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.reflect.Modifier
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.proxy.PageMenuProxy
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.*

enum class AppMenuItemType(val value: Int) {
  /** Regular Android menu item that contains a title and an icon if icon is specified. */
  STANDARD(0),

  /**
   * Menu item that has two buttons, the first one is a title and the second one is an icon. It is
   * different from the regular menu item because it contains two separate buttons.
   */
  TITLE_BUTTON(1),

  /**
   * Menu item that has multiple buttons (no more than 5). Every one of these buttons is displayed
   * as an icon.
   */
  BUTTON_ROW(2),

  /** A divider item to distinguish between menu item groupings. */
  DIVIDER(3),

  /**
   * The number of menu item types specified above. If you add a menu item type you MUST increment
   * this.
   */
  NUM_ENTRIES(4)
}

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

    readerModeManager::class
        .java
        .declaredMethods
        .filter {
          // There exist other methods with the same signatures,
          // which might be tryShowingPrompt
          it.parameterTypes.size == 0 &&
              !Modifier.isStatic(it.modifiers) &&
              it.returnType == Void.TYPE &&
              it.name != "destroy"
        }
        .forEach { it.invoke(readerModeManager) }
  }
}

object PageMenuHook : BaseHook() {

  private fun getUrl(): String {
    return Chrome.getUrl()!!
  }

  override fun init() {

    if (ContextMenuHook.isInit) return
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
          Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, null, sandBoxed)
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
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ? triggeringMotion)
          (parameterCount == 2 || parameterCount == 3) &&
              parameterTypes[0] == Int::class.java &&
              parameterTypes[1] == Boolean::class.java &&
              returnType == Boolean::class.java
        }
        .hookBefore {
          if (menuHandler(it.thisObject as Context, it.args[0] as Int)) {
            it.result = true
          }
        }

    findMethod(proxy.customTabActivity) {
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ? triggeringMotion)
          (parameterCount == 2 || parameterCount == 3) &&
              parameterTypes[0] == Int::class.java &&
              parameterTypes[1] == Boolean::class.java &&
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
                  returnType.declaredMethods.size >= 6 &&
                  (returnType.declaredMethods.find {
                    // Bundle getBundleForMenuItem(int itemId);
                    it.returnType == Bundle::class.java && it.parameterTypes.size == 1
                  } != null) &&
                  (returnType.declaredFields.size == 0 ||
                      returnType.declaredFields.find {
                        Context::class.java.isAssignableFrom(it.type)
                      } != null) &&
                  (returnType.isInterface() || Modifier.isAbstract(returnType.modifiers))
            }
            // public AppMenuPropertiesDelegate createAppMenuPropertiesDelegate()
            .hookAfter {
              findMenuHook!!.unhook()
              val tabbedAppMenuPropertiesDelegate = it.result::class.java
              inflateAppMenu(tabbedAppMenuPropertiesDelegate)
            }

    isInit = true
  }

  fun inflateAppMenu(tabbedAppMenuPropertiesDelegate: Class<*>) {
    val proxy = PageMenuProxy
    val appMenuPropertiesDelegateImpl = tabbedAppMenuPropertiesDelegate.superclass as Class<*>
    // Can be found by searching `Android.PrepareMenu`

    val parameters = appMenuPropertiesDelegateImpl.declaredConstructors[0].parameterTypes
    val mContext = findField(appMenuPropertiesDelegateImpl, true) { type == parameters[0] }
    val mActivityTabProvider =
        findField(appMenuPropertiesDelegateImpl, true) { type == parameters[1] }

    if (Chrome.isBrave) {
      // Brave browser replaces the first row menu with class AppMenuIconRowFooter,
      // and it customize the menu by onFooterViewInflated() function in
      // https://github.com/brave/brave-core/blob/master/android/java/
      // org/chromium/chrome/browser/appmenu/BraveTabbedAppMenuPropertiesDelegate.java
      findMethod(tabbedAppMenuPropertiesDelegate, true) {
            parameterTypes.size == 2 && getParameterTypes()[1] == View::class.java
          }
          // public void onFooterViewInflated(AppMenuHandler appMenuHandler, View view)
          .hookAfter {
            val appMenuIconRowFooter = it.args[1] as LinearLayout
            val bookmarkButton =
                (appMenuIconRowFooter.getChildAt(1) as LinearLayout).getChildAt(1) as ImageButton
            bookmarkButton.setVisibility(View.VISIBLE)
            val ctx = mContext.get(it.thisObject) as Context
            Resource.enrich(ctx)
            bookmarkButton.setImageResource(R.drawable.ic_book)
            bookmarkButton.setId(readerMode.ID)
          }
    }

    val prepareMenu =
        findMethodOrNull(appMenuPropertiesDelegateImpl, true) {
          parameterTypes.size == 2 &&
              parameterTypes.first() == Menu::class.java &&
              returnType == Void.TYPE &&
              !Modifier.isStatic(modifiers) &&
              !Modifier.isAbstract(modifiers)
        }
    // public void prepareMenu(Menu menu, AppMenuHandler handler)

    prepareMenu?.hookAfter prepare@{
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

      val mItems = menu::class.java.getDeclaredField("mItems").also { it.setAccessible(true) }

      @Suppress("UNCHECKED_CAST") val items = mItems.get(menu) as ArrayList<MenuItem>

      val skip = items.filter { it.isVisible() }.size <= 10 || isChromeScheme(url)
      // Inflate only for the main_menu, which has more than visible 10 items at least

      if (skip && !isUserScript(url)) return@prepare
      MenuInflater(ctx).inflate(R.menu.main_menu, menu)

      // Show items with indices in main_menu.xml
      val toShow = mutableListOf<Int>(1) // Reversed index in main_menu

      if (isDevToolsFrontEnd(url)) {
        toShow.clear()
      }

      if (isUserScript(url)) {
        toShow.clear()
        toShow.add(2)
        if (skip) {
          // Show this menu for local preview pages (Custom Tab) of UserScripts
          items.find { it.itemId == R.id.install_script_id }?.setVisible(true)
          return@prepare
        }
      }

      if (isChromeXtFrontEnd(url)) {
        toShow.clear()
        toShow.addAll(listOf(3, 4))
      }

      if (!Chrome.isVivaldi &&
          ctx.resources.configuration.smallestScreenWidthDp >= DisplayMetrics.DENSITY_XXHIGH &&
          toShow.size == 1 &&
          toShow.first() == 1) {
        iconRowMenu.setVisible(true)
      }

      val position =
          items
              .withIndex()
              .filter {
                ctx.resources.getResourceName(it.value.getItemId()).endsWith("id/divider_line_id")
              }
              .map { it.index }[1]

      toShow.forEach {
        val newMenuItem: MenuItem = items[items.size - it]
        newMenuItem.setVisible(true)
        items.add(position + 1, newMenuItem)
      }
      for (i in 0..3) items.removeLast()
    }

    val maybeAddDividerLine =
        findMethodOrNull(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 2 &&
              parameterTypes[1] == Int::class.java &&
              returnType == Void.TYPE &&
              !Modifier.isAbstract(modifiers)
        }
    // private void maybeAddDividerLine(MVCListAdapter.ModelList modelList, @IdRes int id)

    if (prepareMenu == null && maybeAddDividerLine == null) return

    val buildModelForStandardMenuItem =
        findMethod(appMenuPropertiesDelegateImpl) {
          parameterTypes contentDeepEquals
              arrayOf(Int::class.java, Int::class.java, Int::class.java) &&
              returnType == proxy.propertyModel
        }
    // public PropertyModel buildModelForStandardMenuItem(
    // @IdRes int id, @StringRes int titleId, @DrawableRes int iconResId)

    val MVCListAdapter_ModelList = maybeAddDividerLine!!.parameterTypes.first()
    val mItems = findField(MVCListAdapter_ModelList, true) { type == ArrayList::class.java }

    val excludedReturnValuesForModelItem =
        arrayOf(
            MVCListAdapter_ModelList,
            Int::class.java,
            Boolean::class.java,
            Void.TYPE,
            View::class.java)
    val buildNewIncognitoTabItem =
        findMethod(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 0 &&
              !Modifier.isStatic(modifiers) &&
              !excludedReturnValuesForModelItem.contains(returnType)
        }
    // private MVCListAdapter.ListItem buildNewIncognitoTabItem()
    val MVCListAdapter_ListItem = buildNewIncognitoTabItem.returnType
    val model = findField(MVCListAdapter_ListItem) { type == proxy.propertyModel }
    val mType = findField(MVCListAdapter_ListItem) { type == Int::class.java }
    // the original field name was "type"

    val mData = findField(proxy.propertyModel) { type == Map::class.java }

    findMethod(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 0 && returnType == MVCListAdapter_ModelList
        }
        // public MVCListAdapter.ModelList buildMenuModelList()
        .hookAfter {
          val tabProvider = mActivityTabProvider.get(it.thisObject)!!
          Chrome.updateTab(tabProvider.invokeMethod { name == "get" })
          val ctx = mContext.get(it.thisObject) as Context

          Resource.enrich(ctx)
          val url = getUrl()

          @Suppress("UNCHECKED_CAST") val menuModels = mItems.get(it.result) as MutableList<Any>

          @Suppress("UNCHECKED_CAST")
          val iconModels = mData.get(model.get(menuModels[0])) as Map<Any, Any?>
          val additionalIcons =
              iconModels.entries
                  .find { it.key.toString() == "ADDITIONAL_ICONS" }
                  ?.let {
                    val _value = it.value!!::class.java.declaredFields[0]
                    _value.get(it.value)
                  }
          if (additionalIcons != null && !Chrome.isBrave) {
            @Suppress("UNCHECKED_CAST") val icons = mItems.get(additionalIcons) as ArrayList<Any>
            @Suppress("UNCHECKED_CAST")
            val pageInfoModel = mData.get(model.get(icons[3])) as Map<Any, Any?>
            pageInfoModel.forEach {
              if (it.value == null) {
                return@forEach
              }
              val _value = it.value!!::class.java.declaredFields[0].also { it.setAccessible(true) }
              if (it.key.toString() == "MENU_ITEM_ID") {
                _value.set(it.value, readerMode.ID)
              } else if (it.key.toString() == "ICON") {
                _value.set(it.value, ctx.resources.getDrawable(R.drawable.ic_book, null))
              }
            }
          }

          val skip = menuModels.size <= 10 || isChromeScheme(url)
          if (skip && !isUserScript(url)) return@hookAfter

          val localMenus =
              listOf(
                  buildModelForStandardMenuItem.invoke(
                      it.thisObject,
                      R.id.developer_tools_id,
                      R.string.main_menu_developer_tools,
                      R.drawable.ic_devtools),
                  buildModelForStandardMenuItem.invoke(
                      it.thisObject,
                      R.id.extension_id,
                      R.string.main_menu_extension,
                      R.drawable.ic_extension),
                  buildModelForStandardMenuItem.invoke(
                      it.thisObject,
                      R.id.install_script_id,
                      R.string.main_menu_install_script,
                      R.drawable.ic_install_script),
                  buildModelForStandardMenuItem.invoke(
                      it.thisObject,
                      R.id.eruda_console_id,
                      R.string.main_menu_eruda_console,
                      R.drawable.ic_devtools))

          val menusToAdd = mutableListOf<Any>()

          val itemConstuctor = MVCListAdapter_ListItem.declaredConstructors[0]
          if (isChromeXtFrontEnd(url)) {
            menusToAdd.add(
                itemConstuctor.newInstance(AppMenuItemType.STANDARD.value, localMenus[0]))
            menusToAdd.add(
                itemConstuctor.newInstance(AppMenuItemType.STANDARD.value, localMenus[1]))
          } else if (isUserScript(url)) {
            menusToAdd.add(
                itemConstuctor.newInstance(AppMenuItemType.STANDARD.value, localMenus[2]))
          } else {
            menusToAdd.add(
                itemConstuctor.newInstance(AppMenuItemType.STANDARD.value, localMenus[3]))
          }

          val injectPosition =
              menuModels
                  .filter { mType.get(it) == AppMenuItemType.DIVIDER.value }[2]
                  .let { menuModels.indexOf(it) }
          menuModels.addAll(injectPosition + 1, menusToAdd)
        }
  }
}
