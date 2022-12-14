package org.matrix.chromext.script

const val youtubeScript =
    """
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
"""
