package org.matrix.chromext.hook

import org.matrix.chromext.utils.*

object SkooveHook : BaseHook() {

  var loader = this::class.java.classLoader!!

  override fun init() {

    val homeBridge = loader.loadClass("com.skoove.piano.Bridges.HomeBridge")

    findMethod(homeBridge) { name == "setPremium" }.hookBefore { it.args[0] = true }

    val userInfoViewState =
        loader.loadClass("com.skoove.home.shared.ui.viewmodel.data.UserInfoViewState")
    val isPremium = findField(userInfoViewState) { name == "isPremium" }

    findMethod(userInfoViewState) { name == "isPremium" }
        .hookBefore { isPremium.set(it.thisObject, true) }

    val memoryUserInfoRepository =
        loader.loadClass(
            "com.skoove.home.shared.data.repository.userInfo.memory.MemoryUserInfoRepository")
    val setIsPremium = findMethod(memoryUserInfoRepository) { name == "setIsPremium" }

    findMethod(memoryUserInfoRepository) { name == "isPremium" }
        .hookBefore { setIsPremium.invoke(it.thisObject, true) }

    val userInfoRepositoryKt =
        loader.loadClass("com.skoove.home.shared.data.repository.userInfo.UserInfoRepositoryKt")

    userInfoRepositoryKt.declaredMethods
        .filter { it.name == "isAccessibleForUser" }
        .forEach { it.hookBefore { it.result = true } }
    isInit = true
  }
}
