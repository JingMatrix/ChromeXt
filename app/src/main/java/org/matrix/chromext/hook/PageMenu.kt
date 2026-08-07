package org.matrix.chromext.hook

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.LinkedHashSet
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

  /** Menu item that when contains submenus. */
  MENU_ITEM_WITH_SUBMENU(3),

  /** The header for submenus when submenus are displayed in drilldown. */
  SUBMENU_HEADER(4),

  /**
   * A divider item to distinguish between menu item groupings. Chrome renumbered this to 7 in M151,
   * so the value below only serves the forks that kept the original numbering.
   */
  DIVIDER(5),

  /**
   * The number of menu item types specified above. If you add a menu item type you MUST increment
   * this.
   */
  NUM_ENTRIES(6)
}

enum class EntryPoint(val value: Int) {
  UNKNOWN(0),

  /** The user opened reader mode through an app message. */
  MESSAGE(1),

  /** The user opened reader mode through the app menu. */
  APP_MENU(2),

  /** The user opened reader mode through the toolbar button. */
  TOOLBAR_BUTTON(3),
}

object readerMode {
  val ID = 31415926

  fun activate() {
    val observers = PageMenuProxy.mObservers?.get(Chrome.getTab()) as? Iterable<*>
    val readerModeManager =
        observers?.filterNotNull()?.find {
          findFieldOrNull(it::class.java) {
            type == LinkedHashSet::class.java && Modifier.isStatic(modifiers)
          } != null &&
              findFieldOrNull(it::class.java) { type == PageMenuProxy.propertyModel } != null
        }
    if (readerModeManager == null) {
      Log.e("No ReaderModeManager is observing the current tab")
      return
    }

    readerModeManager::class
        .java
        .declaredMethods
        .find {
          // public void activateReaderMode(@EntryPoint int entryPoint)
          it.parameterTypes contentEquals arrayOf(Int::class.java) &&
              !Modifier.isStatic(it.modifiers) &&
              it.returnType == Void.TYPE
        }
        ?.invoke(readerModeManager, EntryPoint.UNKNOWN.value)
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
          val tab = Chrome.getTab()
          if (tab != null && !UserScriptProxy.isLoading(tab))
              return Listener.on("userAgentSpoof", getUrl()) != null
        }
      }
      return false
    }

    findMethod(proxy.chromeTabbedActivity) {
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ...)
          // Chrome keeps appending optional trailing arguments, a Bundle and a MotionEventInfo as
          // of M151, so only the leading two are worth matching on.
          parameterCount >= 2 &&
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
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ...)
          parameterCount >= 2 &&
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

  fun inflateAppMenu(tabbedAppMenuPropertiesDelegate: Class<*>): Unhook {
    val proxy = PageMenuProxy
    val appMenuPropertiesDelegateImpl = tabbedAppMenuPropertiesDelegate.superclass as Class<*>
    // Can be found by searching `Android.PrepareMenu`

    val parameters = appMenuPropertiesDelegateImpl.declaredConstructors[0].parameterTypes
    val mContext = findField(appMenuPropertiesDelegateImpl, true) { type == parameters[0] }
    val mActivityTabProvider =
        findField(appMenuPropertiesDelegateImpl, true) { type == parameters[1] }

    if (Chrome.isBrave) {
      // Brave replaces the first row of the menu with AppMenuIconRowFooter, see
      // https://github.com/brave/brave-core/blob/master/android/java/
      // org/chromium/chrome/browser/appmenu/BraveTabbedAppMenuPropertiesDelegate.java
      // It used to hand that view to the delegate as onFooterViewInflated(handler, view); since
      // 1.93 the delegate returns it from a one argument factory instead, and the row holds
      // MaterialButtons looked up by id rather than nested ImageButtons. Everything here is
      // best-effort: this only restyles one button, and it must never abort the caller, which is
      // what actually adds the ChromeXt entries to the menu.
      fun brandBookmarkButton(delegate: Any, footer: View?) {
        val ctx = mContext.get(delegate) as Context
        Resource.enrich(ctx)
        val id = ctx.resources.getIdentifier("bookmark_this_page_id", "id", ctx.packageName)
        val button = if (id == 0) null else footer?.findViewById<View>(id)
        if (button == null) {
          Log.e("No bookmark button in the Brave app menu footer")
          return
        }
        button.setVisibility(View.VISIBLE)
        // Only the icon is cosmetic; the id is what makes the button reach readerMode, so a browser
        // whose icon setter we cannot name still gets a working button.
        runCatching {
              if (button is ImageButton) {
                button.setImageResource(R.drawable.ic_book)
              } else {
                // MaterialButton takes a Drawable, and its setIcon does not survive obfuscation, so
                // match the signature and keep the inherited background setters out of the way.
                button.invokeMethod(ctx.getDrawable(R.drawable.ic_book)) {
                  name == "setIcon" ||
                      (parameterTypes contentDeepEquals arrayOf(Drawable::class.java) &&
                          returnType == Void.TYPE &&
                          !name.startsWith("setBackground"))
                }
              }
            }
            .onFailure { Log.e("Cannot set the reader mode icon: ${it}") }
        button.setId(readerMode.ID)
      }

      runCatching {
            val onFooterViewInflated =
                findMethodOrNull(tabbedAppMenuPropertiesDelegate, true) {
                  parameterTypes.size == 2 && parameterTypes[1] == View::class.java
                }
            if (onFooterViewInflated != null) {
              onFooterViewInflated.hookAfter {
                brandBookmarkButton(it.thisObject, it.args[1] as? View)
              }
            } else {
              val footerFactory =
                  findMethod(tabbedAppMenuPropertiesDelegate, true) {
                    parameterTypes.size == 1 && returnType == View::class.java
                  }
              footerFactory.hookAfter { brandBookmarkButton(it.thisObject, it.result as? View) }
            }
          }
          .onFailure { Log.ex(it, "Cannot reach the Brave app menu footer") }
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

    if (prepareMenu != null)
        return prepareMenu.hookAfter prepare@{
          val tabProvider = mActivityTabProvider.get(it.thisObject)!!
          Chrome.updateTab(tabProvider.invokeMethod { name == "get" })
          val ctx = mContext.get(it.thisObject) as Context
          Resource.enrich(ctx)

          val menu = it.args[0] as Menu
          val url = getUrl()

          val iconRowMenu = menu.getItem(0)
          if (iconRowMenu.hasSubMenu() && !Chrome.isBrave) {
            // Anchor on the page-info entry by id. Taking the fourth icon on faith is what made
            // ChromeXt overwrite a user-configurable quick command on some forks (issue #290).
            val iconRow = iconRowMenu.getSubMenu()!!
            val infoMenu =
                (0 until iconRow.size())
                    .map { iconRow.getItem(it) }
                    .firstOrNull {
                      runCatching { ctx.resources.getResourceName(it.getItemId()) }
                          .getOrNull()
                          ?.endsWith("id/info_menu_id") == true
                    }
            if (infoMenu == null) {
              // Only the reader mode button is lost; the ChromeXt entries below still go in.
              Log.e("No page info entry in the icon row, skipping the reader mode button")
            } else {
              infoMenu.setIcon(R.drawable.ic_book)
              infoMenu.setEnabled(true)
              val mId = infoMenu::class.java.getDeclaredField("mId")
              mId.setAccessible(true)
              mId.set(infoMenu, readerMode.ID)
              mId.setAccessible(false)
            }
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
        }

    // Inflate for MVC UI model
    val namesModelList =
        findMethodOrNull(tabbedAppMenuPropertiesDelegate) {
          // void maybeAddDividerLine(MVCListAdapter.ModelList modelList, @IdRes int id), static
          // since M150 and moved off the delegate entirely in M153
          parameterTypes.size == 2 &&
              parameterTypes[1] == Int::class.java &&
              returnType == Void.TYPE &&
              !Modifier.isAbstract(modifiers) &&
              !parameterTypes[0].isPrimitive &&
              findFieldOrNull(parameterTypes[0], true) { type == ArrayList::class.java } != null
        }
            ?: findMethod(appMenuPropertiesDelegateImpl) {
              // public abstract MVCListAdapter.ModelList buildMenuModelList(), the one member the
              // delegate cannot delegate away
              parameterTypes.size == 0 &&
                  Modifier.isAbstract(modifiers) &&
                  !returnType.isPrimitive &&
                  findFieldOrNull(returnType, true) { type == ArrayList::class.java } != null
            }
    // Either way the ModelList is the only non primitive type the signature mentions.
    val MVCListAdapter_ModelList =
        namesModelList.parameterTypes.firstOrNull() ?: namesModelList.returnType
    val mItems = findField(MVCListAdapter_ModelList, true) { type == ArrayList::class.java }

    val buildModelForStandardMenuItem =
        findMethodOrNull(appMenuPropertiesDelegateImpl) {
          parameterTypes contentDeepEquals
              arrayOf(Int::class.java, Int::class.java, Int::class.java) &&
              returnType == proxy.propertyModel
        }
    // public PropertyModel buildModelForStandardMenuItem(
    // @IdRes int id, @StringRes int titleId, @DrawableRes int iconResId)
    // M153 hoisted every model factory into a static helper class that nothing we can name refers
    // to, so when the method is gone we assemble the same model out of PropertyModel itself.

    val modelOfKeys =
        proxy.propertyModel.declaredConstructors
            .firstOrNull {
              it.parameterTypes.size == 1 &&
                  it.parameterTypes[0].isAssignableFrom(ArrayList::class.java)
            }
            ?.also { it.isAccessible = true }
    // public PropertyModel(List<PropertyKey> keys), the only constructor that registers the keys
    val propertySetters =
        proxy.propertyModel.declaredMethods
            .filter {
              it.parameterTypes.size == 2 &&
                  it.returnType == Void.TYPE &&
                  !Modifier.isStatic(it.modifiers)
            }
            .onEach { it.isAccessible = true }
    // set(WritableIntPropertyKey, int) and its siblings, one overload per value type

    val buildNewIncognitoTabItem =
        findMethod(tabbedAppMenuPropertiesDelegate) {
          // Anchor on the shape of MVCListAdapter.ListItem, a PropertyModel plus an int type,
          // otherwise zero argument getters such as getProfile() match just as well.
          parameterTypes.size == 0 &&
              !Modifier.isStatic(modifiers) &&
              !returnType.isPrimitive &&
              returnType != MVCListAdapter_ModelList &&
              findFieldOrNull(returnType) { type == proxy.propertyModel } != null &&
              findFieldOrNull(returnType) { type == Int::class.java } != null
        }
    // private MVCListAdapter.ListItem buildNewIncognitoTabItem()
    val MVCListAdapter_ListItem = buildNewIncognitoTabItem.returnType
    val model = findField(MVCListAdapter_ListItem) { type == proxy.propertyModel }
    val mType = findField(MVCListAdapter_ListItem) { type == Int::class.java }
    // the original field name was "type"

    val mData = findField(proxy.propertyModel) { Map::class.java.isAssignableFrom(type) }
    // declared as a raw HashMap since M150

    val itemConstructor =
        MVCListAdapter_ListItem.declaredConstructors
            .first {
              it.parameterTypes.size == 2 &&
                  it.parameterTypes.contains(proxy.propertyModel) &&
                  it.parameterTypes.contains(Int::class.java)
            }
            .also { it.isAccessible = true }
    // R8 is free to swap the (int type, PropertyModel model) parameters around, and it did in M150
    val typeComesFirst = itemConstructor.parameterTypes[0] == Int::class.java
    fun newListItem(type: Int, menuModel: Any?): Any =
        if (typeComesFirst) itemConstructor.newInstance(type, menuModel)
        else itemConstructor.newInstance(menuModel, type)

    // Brave hangs its own zero-argument ModelList getters off the delegate, and matching on the
    // signature alone lands on one of those, so our entries end up in a list nobody ever shows. The
    // superclass declares buildMenuModelList abstract and an override keeps the name, so use it.
    val buildMenuModelList =
        findMethodOrNull(appMenuPropertiesDelegateImpl, true) {
              parameterTypes.size == 0 &&
                  Modifier.isAbstract(modifiers) &&
                  returnType == MVCListAdapter_ModelList
            }
            ?.name

    return findMethod(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 0 &&
              returnType == MVCListAdapter_ModelList &&
              (buildMenuModelList == null || name == buildMenuModelList)
        }
        // public MVCListAdapter.ModelList buildMenuModelList()
        .hookAfter {
          val delegate = it.thisObject
          val tabProvider = mActivityTabProvider.get(delegate)!!
          Chrome.updateTab(tabProvider.invokeMethod { name == "get" })
          val ctx = mContext.get(delegate) as Context

          Resource.enrich(ctx)
          val url = getUrl()

          @Suppress("UNCHECKED_CAST") val menuModels = mItems.get(it.result) as MutableList<Any>

          // Every PropertyModel value is boxed in a one field holder, null until the key is written
          fun propertyOf(item: Any, key: String): Any? {
            @Suppress("UNCHECKED_CAST")
            val properties = mData.get(model.get(item)) as Map<Any, Any?>
            val holder = properties.entries.find { it.key.toString() == key }?.value ?: return null
            val boxed = holder::class.java.declaredFields.firstOrNull() ?: return null
            return boxed.also { it.setAccessible(true) }.get(holder)
          }

          fun menuIdNameOf(item: Any): String? {
            val id = propertyOf(item, "MENU_ITEM_ID") as? Int ?: return null
            return runCatching { ctx.resources.getResourceName(id) }.getOrNull()
          }

          // Rebranding the page info entry is cosmetic, so it must never cost us the menu entries
          // that follow: a throw here would be swallowed by the hook and look like a missing menu.
          runCatching {
                val additionalIcons =
                    menuModels.firstOrNull()?.let { row -> propertyOf(row, "ADDITIONAL_ICONS") }
                if (additionalIcons != null && !Chrome.isBrave) {
                  @Suppress("UNCHECKED_CAST")
                  val icons = mItems.get(additionalIcons) as ArrayList<Any>
                  // Vivaldi fills this row with five user configurable quick commands picked from
                  // kzd.c/kzd.d/kzd.a, and the page info entry is in none of those lists, so a
                  // positional guess just steals whichever command sits fourth (issue #290).
                  val pageInfo =
                      icons.find { menuIdNameOf(it)?.endsWith(":id/info_menu_id") == true }
                  if (pageInfo == null) {
                    Log.d("No page info entry to turn into the reader mode one")
                  } else {
                    @Suppress("UNCHECKED_CAST")
                    val pageInfoModel = mData.get(model.get(pageInfo)) as Map<Any, Any?>
                    pageInfoModel.forEach {
                      if (it.value == null) {
                        return@forEach
                      }
                      val _value =
                          it.value!!::class.java.declaredFields[0].also { it.setAccessible(true) }
                      if (it.key.toString() == "MENU_ITEM_ID") {
                        _value.set(it.value, readerMode.ID)
                      } else if (it.key.toString() == "ICON") {
                        _value.set(it.value, ctx.resources.getDrawable(R.drawable.ic_book, null))
                      }
                    }
                  }
                }
              }
              .onFailure { Log.ex(it, "Cannot reach the page info entry of the app menu") }

          val skip = menuModels.size <= 10 || isChromeScheme(url)
          if (skip && !isUserScript(url)) return@hookAfter

          fun writeProperty(menuModel: Any, key: Any, value: Any) {
            val valueType =
                when (value) {
                  is Int -> Int::class.java
                  is Boolean -> Boolean::class.java
                  else -> Any::class.java
                }
            propertySetters
                .find { it.parameterTypes[0].isInstance(key) && it.parameterTypes[1] == valueType }
                ?.invoke(menuModel, key, value)
          }

          fun standardMenuItem(id: Int, titleId: Int, iconResId: Int): Any? {
            if (buildModelForStandardMenuItem != null)
                return buildModelForStandardMenuItem.invoke(delegate, id, titleId, iconResId)
            // Any menu item already in the list knows the full key set of a menu item, which is all
            // PropertyModel needs to build an empty one of the same shape. Only the properties
            // buildModelForStandardMenuItem itself writes get filled in: CLICK_HANDLER and the
            // other listeners have to stay unset, or our entry would run the template's action.
            val candidates =
                menuModels.filter {
                  propertyOf(it, "TITLE") is String && propertyOf(it, "ICON") != null
                }
            // Only a STANDARD row will do: the key set is copied wholesale, and a TITLE_BUTTON or
            // BUTTON_ROW template carries keys for buttons we never fill in, which the adapter then
            // dereferences. Better no entry than a malformed one.
            val template =
                candidates.firstOrNull { mType.get(it) == AppMenuItemType.STANDARD.value }
            if (template == null || modelOfKeys == null) return null
            @Suppress("UNCHECKED_CAST")
            val keys = (mData.get(model.get(template)) as Map<Any, Any?>).keys
            val menuModel = modelOfKeys.newInstance(keys.toList())
            keys.forEach { key ->
              val value =
                  when (key.toString()) {
                    "MENU_ITEM_ID" -> id
                    "TITLE" -> ctx.getString(titleId)
                    "ICON" -> ctx.resources.getDrawable(iconResId, null)
                    "ENABLED" -> true
                    "ICON_COLOR_RES",
                    "ICON_NO_TINT",
                    "ICON_SHOW_BADGE",
                    "MENU_ICON_AT_START" -> propertyOf(template, key.toString())
                    else -> null
                  }
              if (value != null) writeProperty(menuModel, key, value)
            }
            return menuModel
          }

          val entries =
              if (isChromeXtFrontEnd(url)) {
                listOf(
                    Triple(
                        R.id.developer_tools_id,
                        R.string.main_menu_developer_tools,
                        R.drawable.ic_devtools),
                    Triple(
                        R.id.extension_id, R.string.main_menu_extension, R.drawable.ic_extension))
              } else if (isUserScript(url)) {
                listOf(
                    Triple(
                        R.id.install_script_id,
                        R.string.main_menu_install_script,
                        R.drawable.ic_install_script))
              } else {
                listOf(
                    Triple(
                        R.id.eruda_console_id,
                        R.string.main_menu_eruda_console,
                        R.drawable.ic_devtools))
              }

          val menusToAdd = mutableListOf<Any>()
          entries.forEach { (id, titleId, iconResId) ->
            val menuModel = standardMenuItem(id, titleId, iconResId)
            if (menuModel == null) {
              Log.e("Cannot build a standard app menu item for the ChromeXt entries")
              return@hookAfter
            }
            menusToAdd.add(newListItem(AppMenuItemType.STANDARD.value, menuModel))
          }

          // Chrome renumbered AppMenuItemType, DIVIDER moved from 5 to 7 in M151, so anchor on the
          // divider resource ids and keep the ordinal only as a fallback for forks
          val dividers =
              menuModels
                  .filter { menuIdNameOf(it)?.endsWith("divider_line_id") == true }
                  .ifEmpty { menuModels.filter { mType.get(it) == AppMenuItemType.DIVIDER.value } }
          val anchor = dividers.getOrNull(2) ?: dividers.lastOrNull()
          val injectPosition = anchor?.let { menuModels.indexOf(it) } ?: (menuModels.size - 1)
          menuModels.addAll(injectPosition + 1, menusToAdd)
        }
  }
}
