package org.matrix.chromext.hook

import de.robv.android.xposed.XC_MethodHook.Unhook
import org.matrix.chromext.utils.*

object SpotifyHook : BaseHook() {
  var loader = this::class.java.classLoader!!

  data class Page(
      val structure: String,
      val section: String,
      val field: String,
      val toRemove: Set<String>
  )

  val myPlan =
      mapOf(
          "planColor_" to "#1ED760",
      )

  val myPremiumPlanRow =
      mapOf(
          "premiumPlanShortName_" to "LSPosed",
          "premiumPlanColor_" to "#1ED760",
          // "subscriptionProvider_" to 1,
          // "subscriptionType_" to 1,
          // "planTier_" to 2,
      )

  val stateOverride =
      mapOf(
          // Disables player and app ads.
          "ads" to false,
          // Works along on-demand, allows playing any song without restriction.
          "player-license" to "premium",
          "player-license-v2" to "premium",
          // Disables shuffle being initially enabled when first playing a playlist.
          "shuffle" to false,
          // Allows playing any song on-demand, without a shuffled order.
          "on-demand" to true,
          // Enable Spotify Connect and disable other premium related UI, like buying premium.
          // It also removes the download button.
          "type" to "premium",
          // Make sure playing songs is not disabled remotely and playlists show up.
          "streaming" to true,
          // Allows adding songs to queue and removes the smart shuffle mode restriction,
          // allowing to pick any of the other modes.
          "pick-and-shuffle" to false,
          // Disables shuffle-mode streaming-rule, which forces songs to be played shuffled.
          "streaming-rules" to "",
          // Enables premium UI in settings and removes the premium button in the nav-bar.
          "nft-disabled" to "1",
          // Enable Cross-Platform Spotify Car Thing.
          "can_use_superbird" to true,
          // Removes the premium button in the nav-bar for tablet users.
          "tablet-free" to false,
          "ad-session-persistence" to false,
          "allow-live-events" to false,
          "name" to "Spotify Premium",
          "catalogue" to "premium",
          "has-been-premium-mini" to true,
          "high-bitrate" to true,
          "allow-advertising-id-transmission" to false,
          "exclude-from-marketing-and-advertising" to true,
          "ab-ad-player-targeting" to false,
          "is-eligible-premium-unboxing" to true)

  override fun init() {

    // Spoof product state
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
          // for ((key, value) in state) {
          //   Log.d("${key} [${value_.get(value).javaClass}] ${value_.get(value)}")
          // }
        }

    // Enable trackRows view in artist page
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
                findField(queryParameter::class.java) { type == Array::class.java }
                    .get(queryParameter) as Array<Any>

            val index = store.indexOfLast { it == FIELD || it == "trackRows" }
            store[index] = "trackRows"
            store[index + 1] = true
          }
        }

    // Remove upsell in the context menu of track details
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
            }

    // Remove VA restrictions
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
          // Log.d("VA context: ${it.result}", true)
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

    // Remove AD sections in home page and browse page
    val homePage =
        Page(
            structure = "com.spotify.home.evopage.homeapi.proto.HomeStructure",
            section = "com.spotify.home.evopage.homeapi.proto.Section",
            field = "featureTypeCase_",
            toRemove = setOf("IMAGE_BRAND_AD_FIELD_NUMBER", "VIDEO_BRAND_AD_FIELD_NUMBER"))

    val browsePage =
        Page(
            structure = "com.spotify.browsita.v1.resolved.BrowseStructure",
            section = "com.spotify.browsita.v1.resolved.Section",
            field = "sectionTypeCase_",
            toRemove = setOf("BRAND_ADS_FIELD_NUMBER"))

    for (page in listOf(homePage, browsePage)) {
      val structure = loader.loadClass(page.structure)
      val section = loader.loadClass(page.section)

      val sections_ = findField(structure) { name == "sections_" }
      val featureTypeCase_ = findField(section) { name == page.field }
      val toRemove = page.toRemove.map { findField(section) { name == it }.get(null) as Int }

      findMethod(structure) { returnType == sections_.type }
          .hookBefore {
            @Suppress("UNCHECKED_CAST")
            val sections = sections_.get(it.thisObject) as MutableList<Any>
            // See source code of ProtobufArrayList at
            // https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/ProtobufArrayList.java
            findField(sections::class.java, true) { type == Boolean::class.java }
                .set(sections, true)
            // Set sections mutable
            sections.removeIf { toRemove.contains(featureTypeCase_.get(it)) }
          }
    }

    // Remove premium upsell
    val fetchMessageRequest =
        loader.loadClass(
            "com.spotify.messaging.clientmessagingplatform.clientmessagingplatformsdk.data.models.network.FetchMessageRequest")
    val entityUri = findField(fetchMessageRequest) { name == "entityUri" }
    val purchaseAllowed = findField(fetchMessageRequest) { name == "purchaseAllowed" }

    findMethod(fetchMessageRequest) { name == "getOpportunityId" }
        .hookAfter {
          val request = it.thisObject
          if ((entityUri.get(request) as String).startsWith("upsell")) {
            purchaseAllowed.set(request, false)
          }
        }

    // Spoof premium plan row view
    val premiumPlanRow = loader.loadClass("com.spotify.pamviewservice.v1.proto.PremiumPlanRow")
    premiumPlanRow.declaredMethods
        .filter { it.name != "dynamicMethod" && it.name != "parser" }
        .forEach {
          it.hookBefore {
            val currentRow = it.thisObject
            for ((key, value) in myPremiumPlanRow) {
              val field = findField(premiumPlanRow) { name == key }
              if (field.get(currentRow) == value) return@hookBefore
              field.set(currentRow, value)
            }
          }
        }

    // Spoof plan overview view
    val plan = loader.loadClass("com.spotify.pam.v2.Plan")
    val defaultPlan = findField(plan) { name == "DEFAULT_INSTANCE" }.get(null)

    for ((key, value) in myPlan) {
      val field = findField(plan) { name == key }
      field.set(defaultPlan, value)
    }

    isInit = true
  }
}
