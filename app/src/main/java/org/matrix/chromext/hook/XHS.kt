package org.matrix.chromext.hook

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import org.matrix.chromext.utils.*

object XHSHook : BaseHook() {
  var loader = this::class.java.classLoader!!
  var msgTab: WeakReference<View>? = null
  var hiddenTabs = mutableListOf<WeakReference<View>>()

  fun modifyTabBarView() {
    msgTab?.get()?.performClick()
    hiddenTabs.forEach { it.get()?.visibility = View.GONE }
    Log.d("Finish modifying TabBarView")
  }

  override fun init() {
    val indexHomeFragmentNew =
        loader.loadClass("com.xingin.xhs.homepage.container.homenew.IndexHomeFragmentNew")

    findMethod(indexHomeFragmentNew) { name == "onViewCreated" }
        .hookBefore {
          it.thisObject.invokeMethod { name == "onDestroyView" }
          it.thisObject.invokeMethod { name == "onDestroy" }
          it.result = modifyTabBarView()
        }

    val tabBarView = loader.loadClass("com.xingin.xhs.homepage.tabbar.TabBarView")
    var tabCleared = false

    findMethod(tabBarView) { name == "setHomeSelected" }
        .hookBefore {
          val layout = it.thisObject as ViewGroup
          if (tabCleared) return@hookBefore
          tabCleared = true

          for (i in 0 until layout.childCount) {
            val childView = layout.getChildAt(i) as View
            val idEntryName = extractResourceId(childView)
            when (idEntryName) {
              "index_home",
              "index_store",
              "index_post" -> {
                Log.d("Saving hidden view with ID: $idEntryName")
                hiddenTabs.add(WeakReference(childView))
              }
              "index_message" -> {
                Log.d("Saving msgTab view with ID: $idEntryName")
                msgTab = WeakReference(childView)
              }

              else -> {
                Log.d("Ignoring view with ID: $idEntryName")
              }
            }
          }
          it.result = layout.invokeMethod(true) { name == "setMessageSelected" }
        }

    isInit = true
  }
}

fun extractResourceId(view: View): String? {
  val regex = """([\w\.]+:\w+/\w+)""".toRegex()
  val matchResult = regex.find(view.toString())

  return matchResult?.groupValues?.get(1)?.substringAfterLast('/')
}
