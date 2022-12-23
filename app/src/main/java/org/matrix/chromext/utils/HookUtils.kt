package org.matrix.chromext.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Method

typealias Hooker = (param: XC_MethodHook.MethodHookParam) -> Unit

fun Method.hookMethod(hookCallback: XC_MethodHook): XC_MethodHook.Unhook {
  return XposedBridge.hookMethod(this, hookCallback)
}

fun Method.hookBefore(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    hook: Hooker
): XC_MethodHook.Unhook {
  return this.hookMethod(
      object : XC_MethodHook(priority) {
        override fun beforeHookedMethod(param: MethodHookParam) =
            try {
              hook(param)
            } catch (thr: Throwable) {
              Log.ex(thr)
            }
      })
}

fun Method.hookBefore(hooker: Hooker) = this.hookBefore(XCallback.PRIORITY_DEFAULT, hooker)

fun Method.hookAfter(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    hooker: Hooker
): XC_MethodHook.Unhook {
  return this.hookMethod(
      object : XC_MethodHook(priority) {
        override fun afterHookedMethod(param: MethodHookParam) =
            try {
              hooker(param)
            } catch (thr: Throwable) {
              Log.ex(thr)
            }
      })
}

fun Method.hookAfter(hooker: Hooker) = this.hookAfter(XCallback.PRIORITY_DEFAULT, hooker)

class XposedHookFactory(priority: Int = XCallback.PRIORITY_DEFAULT) : XC_MethodHook(priority) {
  private var beforeMethod: Hooker? = null
  private var afterMethod: Hooker? = null

  override fun beforeHookedMethod(param: MethodHookParam) {
    beforeMethod?.invoke(param)
  }

  override fun afterHookedMethod(param: MethodHookParam) {
    afterMethod?.invoke(param)
  }

  fun before(before: Hooker) {
    this.beforeMethod = before
  }

  fun after(after: Hooker) {
    this.afterMethod = after
  }
}

fun Method.hookMethod(
    priority: Int = XCallback.PRIORITY_DEFAULT,
    hook: XposedHookFactory.() -> Unit
): XC_MethodHook.Unhook {
  val factory = XposedHookFactory(priority)
  hook(factory)
  return this.hookMethod(factory)
}

fun Array<XC_MethodHook.Unhook>.unhookAll() {
  this.forEach { it.unhook() }
}

fun Iterable<XC_MethodHook.Unhook>.unhookAll() {
  this.forEach { it.unhook() }
}
