package org.matrix.chromext.proxy

import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findFieldOrNull

object PageInfoProxy {

  val pageInfoRowView = Chrome.load("org.chromium.components.page_info.PageInfoRowView")
  val mIcon = 
    findFieldOrNull(pageInfoRowView) { it.type.name.contains("ChromeImageView") } 
    ?: findFieldOrNull(pageInfoRowView) { it.type.name.contains("ImageView") } 
    ?: pageInfoRowView.declaredFields.first { it.type.superclass?.name == "android.widget.ImageView" }
  val mTitle = 
    findFieldOrNull(pageInfoRowView) { it.type == TextView::class.java && it.name.contains("title") }
    ?: pageInfoRowView.declaredFields.find { it.type == TextView::class.java }!!
  val mSubtitle = 
    findFieldOrNull(pageInfoRowView) { it.type == TextView::class.java && it != mTitle && it.name.contains("subtitle") }
    ?: pageInfoRowView.declaredFields.find { it != mTitle && it.type == TextView::class.java }!!

  val pageInfoController = Chrome.load("org.chromium.components.page_info.PageInfoController")
  val mView =
      findField(pageInfoController) {
        (Chrome.isEdge && (type == FrameLayout::class.java || type.superclass == FrameLayout::class.java)) ||
            (type.superclass == FrameLayout::class.java &&
                type.interfaces.contains(OnClickListener::class.java))
      }

  private val pageInfoView =
      if (Chrome.isEdge) {
        findFieldOrNull(pageInfoController) { 
          it.type.simpleName == "PageInfoView" 
        }?.type ?: Chrome.load("org.chromium.components.page_info.PageInfoView")
      } else {
        mView.type
      }
  val mRowWrapper = 
    findFieldOrNull(pageInfoView) { it.type == LinearLayout::class.java && (it.name.contains("row") || it.name.contains("wrapper")) }
    ?: findField(pageInfoView) { it.type == LinearLayout::class.java }

  val pageInfoControllerRef =
      // A particular WebContentsObserver designed for PageInfoController
      findField(pageInfoController) {
            type.declaredFields.size <= 2 &&
                (type.declaredFields.any { 
                  it.type == pageInfoController || it.type == WeakReference::class.java 
                })
          }
          .type
}
