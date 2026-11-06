# WebExtensions support in ChromeXt

Status: **working prototype**, verified on Chrome 156.0.8078.81 / Android 16 (23117RK66C).
Branch: local `master` at `b71fc3e` plus the changes described below.

## What works today

| Area | API | State |
|---|---|---|
| Content scripts | `content_scripts` (js + css, `matches`/`exclude_matches`, `all_frames`) | ✅ injected on every navigation, one private scope per extension |
| Manifest | MV2 `background.page` / `background.scripts`, MV3 `background.service_worker` (run as a page), `default_locale`, `icons`, `permissions` | ✅ |
| Identity | `chrome.runtime.id`, `getManifest()`, `getURL()` | ✅ |
| i18n | `getMessage()` incl. `placeholders`, `getUILanguage()` | ✅ |
| Storage | `storage.local/sync/session` `get/set/remove/clear`, `onChanged` (local) | ✅ persisted under `<browser files>/ExtensionStorage/<id>.json` |
| Messaging | `runtime.sendMessage` (content → background, with response), `runtime.onMessage` | ✅ |
| Ports | `runtime.connect` / `onConnect`, `postMessage`, `onDisconnect` | ✅ bidirectional |
| Tabs | `tabs.query`, `tabs.get`, `tabs.getCurrent`, `tabs.create`, `tabs.update`, `tabs.reload` | ✅ |
| Tabs (continued) | `tabs.sendMessage` (background → content) | ✅ through the content script's own poll loop |
| Misc | `permissions.contains/getAll`, `action.setBadgeText` (stored, no UI), `contextMenus.create` (stored, no UI) | 🟡 accepted, limited |

### Verified with a real extension

**Dark Reader 4.9.134 (MV3, service worker background)** loads, its 3 content scripts run,
its background page answers `cs-bg-document-connect`, and the page really turns dark
(mean luminance of the viewport dropped to 82/255, confirmed visually).

Test extension `test-ext/api-test/` exercises every row above and prints its results into a
floating panel plus a `CXTEST PASS|FAIL [name] detail` line in logcat.

## Architecture

```
browser process (hooked)                   ChromeXt's own process (:host)
┌───────────────────────────┐              ┌──────────────────────────────────────────┐
│ init script (ChromeXt)    │              │ HostService → WebView → host.html        │
│  └ content script bundle  │              │  ├ iframe http://127.0.0.1:<portA>/ → A  │
│      chrome.* shim        │              │  ├ iframe http://127.0.0.1:<portB>/ → B  │
│            │ dispatch     │              │  └ long-polls /_api/poll for commands    │
│            ▼              │              └──────────────────────────────────────────┘
│  console.debug channel ───┼──► Kotlin (ExtensionRuntime)  ── storage, tabs, i18n
└───────────────────────────┘        ▲   │
        loopback HTTP server ────────┘   └──► /_api/hostmsg (reports from the host page)
```

* **The background pages live in ChromeXt's own process**, in a `Service` (`extension/HostService.kt`,
  `android:process=":host"`) that the browser binds to. That is the only place a `WebView` can be
  used: instantiating one inside the browser process loads the WebView provider's native library
  next to Chromium's own and aborts the browser (`JniAndroid$UncaughtExceptionException`, verified on
  Chrome 156 / Android 16). A browser tab is not an option either — a new tab steals the foreground,
  piles up across restarts, and Chrome cannot revive a restored-but-unloaded tab without selecting it.
  A bound service needs no notification, dies with the browser, and keeps the tab strip untouched.
* **One loopback port per extension**, so every extension keeps its own origin (`localStorage`, ES
  modules, relative `fetch` behave like `chrome-extension://`).
* **Both directions go through the loopback server**: the host page long-polls `/_api/poll` for
  commands (deliveries, replies, port traffic, raw code) and reports back with `POST /_api/hostmsg`.
  Content scripts keep using the ChromeXt action channel (`Symbol.<random>.unlock(<key>).dispatch`)
  and poll `runtime.poll` for whatever is queued for their tab.
* **Per-extension scope.** The bundle runs in `(function(chrome, browser){ … })(api, api)`, so several
  extensions can coexist on one page without touching each other's globals, and each extension runs
  its own poll loop (a page-wide one would let the last loaded extension drain everybody's queue).

## Where extensions live

```
/storage/emulated/0/Android/data/<browser>/files/Extension/<id>/
```

`<id>` is the 32-letter Chrome Web Store id, taken from the CRX3 signed header (or derived from
the manifest `key`), and a slug of the resolved manifest name only as a last resort. Storage of `chrome.storage` lives next to it, in
`/data/data/<browser>/files/ExtensionStorage/<id>.json`.

Open the manager: **page menu (⋮) → Extensions** — the entry is injected on every page (it used to
only appear on ChromeXt's own front-end site, which is why it looked missing). On WebView based
browsers the same entry is added to the selection action mode. The manager is a local page, so its
port changes with the browser process: use the menu entry rather than a bookmark.

Three ways to install:

1. **Manager page** – page menu → *Extensions*, then
   * *从文件安装* for a `.zip` / `.crx`,
   * the text field for a Web Store id or URL (fetched from `clients2.google.com`).
   The page also lists extensions with enable/disable and uninstall.
2. **Directory** – drop an unpacked extension into the path above (owner must be the browser
   uid, e.g. `chown -R 10347:1078 …` for Chrome).
3. Both are picked up on the next browser start; *刷新* in the manager re-scans without restart.

## Known limitations

* `webRequest`, `declarativeNetRequest`, `scripting` (MV3) and `action` popups are not
  implemented; blocking extensions (uBlock Origin) will not work.
* MV3 `service_worker` runs as a normal page: no event-driven wake-up, so an extension that
  relies on being suspended and restarted may misbehave. `importScripts` is polyfilled with
  `document.write`.
* `run_at` is ignored: everything is injected at `document_idle` (ChromeXt injects on
  `onUpdateUrl`).
* Content scripts run in the page's world, not in an isolated world, so a hostile page can
  observe or tamper with them. Same trade-off ChromeXt already makes for UserScripts.
* Delivering into a content script goes through a poll loop (see below), so it costs up to half a
  second of latency and one IPC round trip per poll while a page has extensions.
* The host process is a bound service of ChromeXt: it stops when the browser does, so background
  pages restart (and re-register their listeners) on the next browser start.

## Building

The upstream build pins AGP 9.4.1, which does not publish a `linux_arm64` aapt2, so on an
arm64 host (this tablet) the build fails with *aapt2 Daemon startup failed*. `build.gradle.kts`
was bumped to **9.5.0** (the first version shipping `aapt2-…-linux_arm64.jar`); CI on x86_64 is
unaffected.

```sh
export ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
gradle app:assembleDebug          # gradle 9.8.2, no wrapper download needed
```

Deploy:

```sh
pm install -r app/build/outputs/apk/debug/app-debug.apk
am force-stop com.android.chrome   # the module is loaded per process
```

Logs: `logcat -s ChromeXt:*`, the test extension additionally prints `CXTEST …` lines.

## Process and permission notes

* `HostService` needs `android.permission.INTERNET` (it is the process that talks HTTP to the
  loopback server) and the application sets `android:usesCleartextTraffic="true"`; without it the
  WebView refuses to open `http://127.0.0.1:…`.
* `Log` no longer touches `XposedBridge` unconditionally: the Xposed API is a compile-only
  dependency and referencing it in ChromeXt's own process throws `NoClassDefFoundError`.
* `HostService` only loads urls that start with `http://127.0.0.1:`, whatever the caller passes.
