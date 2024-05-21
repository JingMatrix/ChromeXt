package org.matrix.chromext.hook

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.Class
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.*

object ContextMenuHook : BaseHook() {

  val erudaMenuId = 31415926
  private var text: WeakReference<TextView>? = null

  private fun openEruda(url: String) {
    if (WebViewHook.isInit) {
      val webSettings = Chrome.getTab()?.invokeMethod { name == "getSettings" }
      if (webSettings == null) return
      val javaScriptEnabled = webSettings.invokeMethod { name == "getJavaScriptEnabled" } as Boolean
      if (!javaScriptEnabled) {
        webSettings.invokeMethod(true) { name == "setJavaScriptEnabled" }
        Chrome.evaluateJavascript(listOf(Local.initChromeXt))
      }
    }
    if (isChromeXtFrontEnd(url)) {
      Listener.on("inspectPages")
    } else if (isUserScript(url)) {
      val sandBoxed = shouldBypassSandbox(url)
      Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, sandBoxed)
    } else {
      Chrome.evaluateJavascript(listOf(Local.openEruda))
    }
  }

  private val clickListnerFactory = { mode: ActionMode, url: String ->
    MenuItem.OnMenuItemClickListener {
      openEruda(url)
      mode.finish()
      true
    }
  }

  private var actionModeFinder: Unhook? = null
  private var popupWindowFinder: Unhook? = null

  private fun hookActionMode(cls: Class<*>) {
    actionModeFinder?.unhook()
    findMethod(cls) { name == "onCreateActionMode" }
        // public abstract boolean onCreateActionMode (ActionMode mode, Menu menu)
        .hookAfter {
          val url = Chrome.getUrl()
          val mode = it.args[0] as ActionMode
          val menu = it.args[1] as Menu
          if (menu.findItem(erudaMenuId) != null) return@hookAfter
          Resource.enrich(Chrome.getContext())
          val titleId =
              if (isChromeXtFrontEnd(url)) R.string.main_menu_developer_tools
              else if (isUserScript(url)) R.string.main_menu_install_script
              else R.string.main_menu_eruda_console
          val erudaMenu = menu.add(titleId)
          val mId = erudaMenu::class.java.getDeclaredField("mId")
          mId.setAccessible(true)
          mId.set(erudaMenu, erudaMenuId)
          erudaMenu.setOnMenuItemClickListener(clickListnerFactory(mode, url!!))
        }
  }

  private fun hookPopupWindow(cls: Class<*>) {
    popupWindowFinder?.unhook()
    cls.declaredConstructors.first().hookAfter {
      val ctx = it.args[0] as Context
      Resource.enrich(ctx)
      val popupWindow = it.thisObject as PopupWindow
      val view = popupWindow.contentView
      if (!(view is ViewGroup && view.getChildAt(0) is TextView)) return@hookAfter
      val sampleView = view.getChildAt(0) as TextView
      val textView = TextView(ctx)
      textView!!.setHorizontallyScrolling(true)
      textView!!.setSingleLine(true)
      textView!!.ellipsize = sampleView.ellipsize
      textView!!.gravity = sampleView.gravity
      textView!!.transformationMethod = sampleView.transformationMethod
      textView!!.layoutParams = sampleView.layoutParams
      textView!!.typeface = sampleView.typeface
      textView!!.setOnClickListener {
        openEruda(Chrome.getUrl()!!)
        popupWindow.dismiss()
      }
      view.addView(textView!!, view.childCount)
      text = WeakReference(textView)
    }
  }

  override fun init() {
    if (Chrome.isSamsung) {
      hookActionMode(Chrome.load("com.sec.terrace.content.browser.TinActionModeCallback"))
    } else if (Chrome.isQihoo) {
      val WebViewExtensionClient = Chrome.load("com.qihoo.webkit.extension.WebViewExtensionClient")
      popupWindowFinder =
          WebViewExtensionClient.declaredConstructors.first().hookAfter {
            val selectionMenuWrapper = it.thisObject
            val showSelectionMenu =
                findMethodOrNull(selectionMenuWrapper::class.java) { name == "showSelectionMenu" }
            if (showSelectionMenu == null) return@hookAfter
            val selectionMenu =
                selectionMenuWrapper::class.java.declaredFields.first().get(selectionMenuWrapper)
                    as kotlin.Any
            val horizontalCustomPopupDialog =
                selectionMenu::class
                    .java
                    .declaredFields
                    .find { it.type.superclass == PopupWindow::class.java }!!
                    .type
            hookPopupWindow(horizontalCustomPopupDialog)
            showSelectionMenu.hookAfter {
              val view = it.args[0]
              if (WebViewHook.WebView!!.isAssignableFrom(view::class.java)) Chrome.updateTab(view)
              val url = Chrome.getUrl()!!
              val titleId =
                  if (isChromeXtFrontEnd(url)) R.string.main_menu_developer_tools
                  else if (isUserScript(url)) R.string.main_menu_install_script
                  else R.string.main_menu_eruda_console
              text?.get()?.setText(titleId)
            }
          }
    } else if (Chrome.isMi) {
      val miuiFloatingSelectPopupWindow =
          Chrome.load("com.miui.org.chromium.content.browser.miui.MiuiFloatingSelectPopupWindow")
      val mContentView = findField(miuiFloatingSelectPopupWindow) { name == "mContentView" }
      val mContext = findField(miuiFloatingSelectPopupWindow) { name == "mContext" }
      val mParent = findField(miuiFloatingSelectPopupWindow) { name == "mParent" }
      val mDelegate = findField(miuiFloatingSelectPopupWindow) { name == "mDelegate" }
      val mShareImageView = findField(miuiFloatingSelectPopupWindow) { name == "mShareImageView" }

      val selectionPopupController =
          Chrome.load(
              "com.miui.org.chromium.content.browser.selection.SelectionPopupControllerImpl")
      val miuiSelectPopupMenuDelegate =
          Chrome.load("com.miui.org.chromium.content.browser.miui.MiuiSelectPopupMenuDelegate")
      val getDarkOrNightModeEnabled =
          findMethod(miuiSelectPopupMenuDelegate) { name == "getDarkOrNightModeEnabled" }

      val miuiTextSelectResources =
          Chrome.load("com.miui.org.chromium.content.browser.miui.MiuiTextSelectResources")
      val createImageView = findMethod(miuiTextSelectResources) { name == "createImageView" }
      findMethod(miuiFloatingSelectPopupWindow) { name == "showPopup" }
          .hookAfter {
            val popupWindow = it.thisObject
            val context = mContext.get(popupWindow) as Context
            val view = mParent.get(popupWindow)
            val delegate = mDelegate.get(popupWindow)!!
            val this0 = findField(delegate::class.java) { type == selectionPopupController }
            val controller = this0.get(delegate)!!
            if (WebViewHook.WebView!!.isAssignableFrom(view::class.java)) Chrome.updateTab(view)
            Resource.enrich(context)
            val url = Chrome.getUrl()

            val contentView = mContentView.get(it.thisObject) as ViewGroup
            val listener = OnClickListener {
              controller.invokeMethod { name == "finishActionMode" }
              openEruda(url!!)
            }

            val shareImageView = mShareImageView.get(popupWindow) as ImageView
            val erudaImageView =
                createImageView.invoke(
                    null, context, listener, getDarkOrNightModeEnabled.invoke(delegate))
                    as ImageView
            erudaImageView.setImageResource(R.drawable.ic_devtools)
            erudaImageView.setId(erudaMenuId)
            erudaImageView.getLayoutParams().height = shareImageView.getMeasuredHeight()
            erudaImageView.getLayoutParams().width = shareImageView.getMeasuredWidth()
            val paddingY = contentView.getMeasuredHeight() - shareImageView.getMeasuredHeight() - 10
            erudaImageView.setPadding(
                shareImageView.paddingLeft, paddingY / 2, shareImageView.paddingRight, paddingY / 2)
            contentView.addView(erudaImageView)
          }
    } else {
      actionModeFinder =
          findMethod(View::class.java) { name == "startActionMode" && parameterTypes.size == 2 }
              // public ActionMode startActionMode (ActionMode.Callback callback, int type)
              .hookBefore {
                if (it.args[1] as Int != ActionMode.TYPE_FLOATING) return@hookBefore
                hookActionMode(it.args[0]::class.java)
              }
    }
    isInit = true
  }
}
