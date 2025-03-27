package org.matrix.chromext.hook

import org.matrix.chromext.utils.*

object SpotifyHook : BaseHook() {
  var loader = this::class.java.classLoader!!

  val stateOverride =
      mapOf(
          // Disables player and app ads.
          "ads" to false,
          // Works along on-demand, allows playing any song without restriction.
          "player-license" to "premium",
          // Disables shuffle being initially enabled when first playing a playlist.
          "shuffle" to false,
          // Allows playing any song on-demand, without a shuffled order.
          "on-demand" to true,
          // Make sure playing songs is not disabled remotely and playlists show up.
          "streaming" to true,
          // Allows adding songs to queue and removes the smart shuffle mode restriction,
          // allowing to pick any of the other modes.
          "pick-and-shuffle" to false,
          // Disables shuffle-mode streaming-rule, which forces songs to be played shuffled
          // and breaks the player when other patches are applied.
          "streaming-rules" to "",
          // Enables premium UI in settings and removes the premium button in the nav-bar.
          "nft-disabled" to "1")

  override fun init() {

    val productStateProto = loader.loadClass("com.spotify.remoteconfig.internal.ProductStateProto")
    val accountAttribute = loader.loadClass("com.spotify.remoteconfig.internal.AccountAttribute")
    val value_ = findField(accountAttribute) { name == "value_" }

    findMethod(productStateProto) { parameterTypes.size == 0 && returnType == Map::class.java }
        .hookAfter {
          @Suppress("UNCHECKED_CAST") val state = it.result as Map<String, Any>

          for ((key, value) in stateOverride) {
            if (state.containsKey(key)) {
              val attribute = state.get(key)
              Log.d("Overriding ${key}:${value_.get(attribute)} to ${value}")
              value_.set(attribute, value)
            } else {
              Log.d("Key ${key} not found in productState")
            }
          }
        }

    val preparePlayOptions =
        loader.loadClass("com.spotify.player.model.command.options.PreparePlayOptions")
    val option = findMethod(preparePlayOptions) { name == "configurationOverride" }.returnType

    findMethod(option) { name == "containsKey" }
        .hookAfter {
          val FIELD = "checkDeviceCapability"
          if (it.args[0] == "signal" && it.thisObject.toString().contains("${FIELD}=")) {
            @Suppress("UNCHECKED_CAST") val queryParameter = it.thisObject as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val store =
                queryParameter::class
                    .java
                    .declaredFields
                    .find { it.type == Array::class.java }!!
                    .let {
                      it.setAccessible(true)
                      it.get(queryParameter)
                    } as Array<Any>

            val index = store.indexOfLast { it == FIELD || it == "trackRows" }
            store[index] = "trackRows"
            store[index + 1] = true
          }
        }

    isInit = true
  }
}
