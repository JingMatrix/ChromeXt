package org.matrix.chromext.hook

import de.robv.android.xposed.XC_MethodHook.Unhook
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
          "nft-disabled" to "1",
          // Enable Cross-Platform Spotify Car Thing.
          "can_use_superbird" to true,
          // Removes the premium button in the nav-bar for tablet users.
          "tablet-free" to false)

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
          // Log.d("${it.thisObject}", true)
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

    val localFilesProperties =
        loader.loadClass("com.spotify.localfiles.mediastoreimpl.LocalFilesProperties")

    var flagFinder: Unhook? = null
    flagFinder =
        findMethod(localFilesProperties) { name == "parse" }
            .hookAfter {
              flagFinder?.unhook()
              findMethod(it.args[0]::class.java) {
                    parameterTypes contentDeepEquals
                        arrayOf(String::class.java, String::class.java, Boolean::class.java) &&
                        returnType == Boolean::class.java
                  }
                  .hookAfter {
                    if (it.args[0] == "android-context-menu" &&
                        it.args[1] == "remove_ads_upsell_enabled") {
                      it.result = false
                    }
                    // Log.d("(${it.args[0]}, ${it.args[1]}, ${it.args[2]}) => ${it.result}")
                  }

              // @Suppress("UNCHECKED_CAST")
              // val models = it.thisObject.invokeMethod { name == "models" } as List<Any>
              // models[0]::class
              //     .java
              //     .declaredConstructors
              //     .find {
              //       it.parameterTypes contentDeepEquals
              //           arrayOf(String::class.java, String::class.java, Boolean::class.java)
              //     }!!
              //     .hookAfter {
              //       Log.d("(${it.args[0]}, ${it.args[1]}, ${it.args[2]}) -> ${it.thisObject}")
              //     }

            }

    val contextJsonAdapter =
        loader.loadClass("com.spotify.voiceassistants.playermodels.ContextJsonAdapter")
    val context = loader.loadClass("com.spotify.player.model.Context")
    val autoValue_Context = loader.loadClass("com.spotify.player.model.AutoValue_Context")

    val preparePlayOptionsJsonAdapter =
        loader.loadClass("com.spotify.voiceassistants.playermodels.PreparePlayOptionsJsonAdapter")
    val autoValue_PreparePlayOptions =
        loader.loadClass("com.spotify.player.model.command.options.AutoValue_PreparePlayOptions")

    val autoValue_PlayerOptionOverrides =
        loader.loadClass("com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides")

    val url = findField(autoValue_Context) { name == "url" }
    val uri = findField(autoValue_Context) { name == "uri" }

    val playerOptionsOverride =
        findField(autoValue_PreparePlayOptions) { name == "playerOptionsOverride" }

    val shufflingContext = findField(autoValue_PlayerOptionOverrides) { name == "shufflingContext" }

    findMethod(contextJsonAdapter) { name == "fromJson" && returnType == context }
        .hookAfter {
          url.set(it.result, (url.get(it.result) as String).replace("station:", ""))
          uri.set(it.result, (uri.get(it.result) as String).replace("station:", ""))
          Log.d("VA context: ${it.result}", true)
        }

    findMethod(preparePlayOptionsJsonAdapter) {
          name == "fromJson" && returnType == preparePlayOptions
        }
        .hookAfter {
          val player_options_override = playerOptionsOverride.get(it.result)
          var value = findField(player_options_override::class.java) { type == Any::class.java }
          val shuffling_context = shufflingContext.get(value.get(player_options_override))
          value = findField(shuffling_context::class.java) { type == Any::class.java }
          value.set(shuffling_context, false)
          Log.d("VA player options: ${it.result}", true)
        }

    isInit = true
  }
}
