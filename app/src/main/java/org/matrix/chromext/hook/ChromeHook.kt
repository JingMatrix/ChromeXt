package org.matrix.chromext.hook

import android.content.Context
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import java.lang.reflect.Field

const val youtubeScript =
    """
setTimeout(()=>{
	const skipAds = () => {
		let btn = document
		.getElementsByClassName("ytp-ad-skip-button ytp-button")
		.item(0);
		if (btn) {
			btn.click();
		}

		const ad = [...document.querySelectorAll(".ad-showing")][0];
		const vid = document.querySelector("video");
		if (ad) {
			vid.muted = true;
			vid.currentTime = vid.duration;
		} else { 
			if (vid != undefined) {
				vid.muted = false;
			}   
		}
	};
	const main = new MutationObserver(() => {
		let adComponent = document.querySelector("ytd-ad-slot-renderer");
		if (adComponent) {
			const node = adComponent.closest('ytd-rich-item-renderer')
			|| adComponent.closest('ytd-search-pyv-renderer') || adComponent;
			node.remove();
		}
		let shortsNav = document.querySelector("div.pivot-bar-item-tab.pivot-shorts");
		if (shortsNav) {
			const node = shortsNav.closest('ytm-pivot-bar-item-renderer') || shortsNav;
			node.remove();
		}

		const adContainer = document
		.getElementsByClassName("video-ads ytp-ad-module")
		.item(0);
		if (adContainer) {
			new MutationObserver(skipAds).observe(adContainer, {
				attributes: true,
				characterData: true,
				childList: true,
			});
		}
	});

	main.observe(document.body, {
		attributes: false,
		characterData: false,
		childList: true,
		subtree: true,
	});
}, 50);
"""

object ChromeHook : BaseHook() {
  override fun init() {
    findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
          name == "attachBaseContext"
        }
        .hookAfter {
          val ctx: Context = (it.args[0] as Context).createContextForSplit("chrome")
          val UrlParams =
              ctx.getClassLoader()
                  .loadClass("org.chromium.content_public.browser.LoadUrlParams")
                  .getDeclaredConstructor(String::class.java)
          val tabDelegateImpl =
              ctx.getClassLoader()
                  .loadClass("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
          val tabImpl: Field = tabDelegateImpl.getDeclaredField(TAB_FIELD)
          tabImpl.setAccessible(true)
          findMethod(tabDelegateImpl) { name == "onUpdateUrl" }
              .hookBefore {
                val url = it.args[0].invokeMethod() { name == SHOW_URL } as String
                if (url.startsWith("chrome://xt")) {
                  // Reserve chrome://xt for futur usage
                  it.thisObject.invokeMethod() { name == "closeContents" }
                } else if (url.startsWith("https://m.youtube.com")) {
                  Log.d("Inject userscript for m.youtube.com")
                  tabImpl.get(it.thisObject)?.invokeMethod(
                      UrlParams.newInstance("javascript: ${youtubeScript}")) {
                        name == LOAD_URL
                      }
                }
              }

          findMethod(tabDelegateImpl) { name == "addMessageToConsole" }
              .hookAfter {
                // addMessageToConsole(int level, String message, int lineNumber, String sourceId) {
                Log.i("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
              }
        }
  }
}
