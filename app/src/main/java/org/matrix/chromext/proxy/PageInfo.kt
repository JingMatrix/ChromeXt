package org.matrix.chromext.proxy

import android.app.Activity
import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.firstDeclared

// Nothing here may throw: an exception in this initializer surfaces as ExceptionInInitializerError
// and poisons the object for good, while PageInfoHook.init needs to report a plain
// NoSuchMethod/NoSuchField so that MainHook can fall back to ContextMenuHook.
object PageInfoProxy {

  private fun load(name: String): Class<*>? =
      runCatching { Chrome.load("org.chromium.components.page_info." + name) }
          .onFailure { Log.e("PageInfoProxy: cannot load " + name) }
          .getOrNull()

  private val pageInfoRowView = load("PageInfoRowView")

  // PageInfoRowView is an XML widget, so (Context, AttributeSet) is its only constructor
  val rowConstructor: Constructor<*>? =
      pageInfoRowView?.declaredConstructors?.firstOrNull {
        it.parameterTypes.size == 2 && Context::class.java.isAssignableFrom(it.parameterTypes[0])
      }

  private val rowFields = pageInfoRowView?.declaredFields?.onEach { it.isAccessible = true }
  val mIcon: Field? = rowFields?.firstOrNull { ImageView::class.java.isAssignableFrom(it.type) }
  private val rowTexts =
      rowFields?.filter { TextView::class.java.isAssignableFrom(it.type) } ?: emptyList()
  val mTitle: Field? = rowTexts.firstDeclared()
  val mSubtitle: Field? = rowTexts.filter { it != mTitle }.firstDeclared()

  private val pageInfoController = load("PageInfoController")
  private val controllerMethods =
      pageInfoController
          ?.declaredMethods
          ?.filterNot { it.isSynthetic }
          ?.onEach { it.isAccessible = true } ?: emptyList()

  // The single static entry point, show(Activity, WebContents, ...), which builds the whole dialog
  val showPageInfo: Method? =
      controllerMethods.firstOrNull {
        Modifier.isStatic(it.modifiers) &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.firstOrNull() == Activity::class.java
      }

  // Native calls these back from within show(), once the dialog view tree has been inflated
  private val callbackNames =
      setOf("setSecurityDescription", "updatePermissionDisplay", "addPermissionSection")
  val nativeCallbacks = controllerMethods.filter { it.name in callbackNames }

  // destroy() unregisters the observer and dismisses the dialog. It takes no argument and is the
  // first method declared in PageInfoController on every build we checked, hence the lowest R8 rank
  // among the obfuscated no-argument ones: a() in Chrome 151 and Edge 150, b() in CocCoc 155.
  val destroy: Method? =
      controllerMethods
          .filter {
            !Modifier.isStatic(it.modifiers) &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.isEmpty()
          }
          .let { noArgs -> noArgs.firstOrNull { it.name == "destroy" } ?: noArgs.firstDeclared() }
}
