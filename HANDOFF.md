# ChromeXt 扩展支持 · 交接文档

> 最后更新：2026-11-06 12:35 ｜ 状态：**已向上游开 PR，等维护者回复**
> 代码：`/storage/emulated/0/Tools/LSPilot/projects/chromext`（分支 `extension-support`，工作树干净）

## 一句话现状

WebExtensions 支持已经从"骨架"做成**能用**：内容脚本注入、后台页常驻、`chrome.*` 子集、本地管理页都跑通了，
在自己写的自测扩展上 **11/11 全 PASS**，真实扩展 **Dark Reader 4.9.134 (MV3)** 生效，PR 已提交到上游：

**https://github.com/JingMatrix/ChromeXt/pull/319**（OPEN / MERGEABLE / 29 文件 +3562 −154）

CI 显示 `action_required` —— 这是 GitHub 对**首次贡献者**的默认行为，需要维护者点 "Approve and run"，不是失败。

## 交接时的事实清单

| 项 | 值 |
|---|---|
| 上游基线 | `b71fc3e`（= 当前 JingMatrix/ChromeXt master tip） |
| 我们的分支 | `extension-support` → `73b2075`（3 个 commit） |
| fork | `https://github.com/RONUKUIGO/ChromeXt`（公开） |
| 设备上装的模块 | ChromeXt 3.9.1 debug 构建，`lastUpdateTime=2026-11-06 12:15:55`，包名与上游相同、debug 签名 |
| 成品 APK | `outputs/apk/ChromeXt-v39-extsupport.apk`，sha256 `92a2b812c1002f5c642aee55b0fceab739974b8dec6ecaf3f7c7ef3b507d3736` |
| 设备上已装的扩展 | `api-test`（自测）、`eimadpbcbfnmbkopoojfekhnkhdbieeh`（Dark Reader 4.9.134）、`msg-extname`（Tampermonkey 5.5.1，从商店装的） |
| 扩展存储 | `/data/data/com.android.chrome/files/ExtensionStorage/*.json` |

commit 明细：

| commit | 内容 | 备注 |
|---|---|---|
| `0de5720` | Support unpacked WebExtensions | 主体功能 |
| `2619835` | Build with AGP 9.5.0 so that arm64 hosts work | **可单独丢弃**：9.4.1 没有 `linux_arm64` 的 aapt2，只影响 arm64 本机构建，CI 是 x86_64 |
| `73b2075` | Deliver page-bound messages through a per-extension poll | 后台→内容脚本打通 + 两个 bug 修复 |

## 怎么继续干活（环境已就绪，零摸索）

```sh
cd /storage/emulated/0/Tools/LSPilot/projects/chromext
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
       JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64

gradle format app:assembleDebug            # 用系统 gradle 9.8.2，别用 ./gradlew（要下 9.8.1）
cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/ws/out/ChromeXt-debug.apk

# 装 + 重启被注入的进程（Chrome 是测试靶子，force-stop 它没问题）
pm install -r /data/local/tmp/ws/out/ChromeXt-debug.apk
am force-stop com.android.chrome
am start -a android.intent.action.VIEW -d "https://example.com"

# 看结果
logcat -d | grep -E "CXTEST (PASS|FAIL)" | tail -12      # 自测扩展的结果
logcat -d | grep -E "ChromeXt:" | tail -20               # 模块日志（Debug 构建才有 Log.d）
```

自测扩展的源码在仓库里（`test-ext/api-test/`），设备上的副本在
`/storage/emulated/0/Android/data/com.android.chrome/files/Extension/api-test/`。
改完要 `cp` 回去并 `chown -R 10347:1078`（Chrome 的 uid:gid），否则扩展读不到。

**调试小抄**：沙箱（`run_command`）和 Chrome 共享网络命名空间，可以直接和
`@chrome_devtools_remote` 通话，用 python 列 `/json`、甚至发 CDP 命令，排查页面侧问题很方便
（这也是定位 "DevTools 一次只能服务一个 client" 的手段）。

## 代码地图

```
app/src/main/kotlin/org/matrix/chromext/extension/
  Manifest.kt    match pattern、content_scripts 解析、Extension（含 i18n 解析）
  Extensions.kt  注册表：扫描目录、启停、storage 读写、zip/crx 安装、商店下载、CRX3 id 提取
  Server.kt      loopback HTTP：每扩展一个端口（扩展 origin）+ 管理页端口 + /_api/* + HostQueue
  ChromeApi.kt   生成注入用的 JS：config + 共用 shim + 各扩展的 content bundle / 后台页脚本
  Runtime.kt     动作分发：storage/i18n/tabs/ports/消息，以及 poll 队列的出口
  Outbox.kt      每个 (扩展, tab) 的命令队列，等内容脚本自己来取
app/src/main/assets/
  chrome-api.js  chrome.* 兼容层（内容脚本与后台页共用；每个实例自带轮询循环）
  manager.html/js/css  本地管理页：装/卸/启停/看后台页
  host.html/host.js    宿主页：每扩展一个 iframe + 长轮询 /_api/poll
```
接线点：`Listener.kt`（`ext` 动作 + 打开管理页）、`script/Manager.kt`（每次导航注入 content scripts）、
`hook/PageMenu.kt` + `hook/ContextMenu.kt`（Extensions 入口，任意页面都有）、`Chrome.kt`（`tabList()`/`registerTab()`）。

## 五条实测结论（都是踩出来的，改动前务必读）

1. **浏览器进程里不能 `new WebView`** —— 会和 Chromium 的原生库撞车、`JNI_OnLoad` 失败、**Chrome 整个崩**。后台页只能走"宿主标签页 + iframe"。
2. **DevTools 端点不能当消息通道** —— 一次只服务一个 client，之后 `/json` 超时、`/devtools/page/<id>` 返回 **500**，约 40 秒才恢复（`/proc/net/unix` 里 `@chrome_devtools_remote` 卡在 state 03）。
3. **Chrome 156 删了 `WebContents#evaluateJavaScript`** —— 只剩内部 `onEvaluateJavaScriptResult`，反射进页面也断了。
   ⇒ 后两条合起来决定了：**消息只能让内容脚本自己轮询拉取**（`runtime.poll`，可见 400ms / 隐藏 1.5s）。
4. **轮询循环必须每个扩展一份**（在各自闭包里）。写成页面级单例会让最后加载的扩展独占轮询、把别人队列一起取走，
   表现为"port 测试随机超时"。
5. **ChromeXt 每次 URL update 会重复注入**，第二份 bundle 会顶掉第一份的 onMessage 监听器 ⇒ `__chromextInjected[extId]` 去重。

补充：`ChromeTabbedActivity.getDeclaredMethods()` 在 Chrome 156 会抛 `NoClassDefFoundError`，`TabModelSelector` 类也加载不到，
所以 tab 枚举用**登记表**（`onUpdateUrl` 钩子把见过的 Tab 收进 `WeakHashMap`）。

## 已知短板（维护者回复后按这个顺序啃）

| 优先级 | 事项 | 说明 |
|---|---|---|
| P0 | `webRequest` / `declarativeNetRequest` | Tampermonkey、拦截类扩展全靠它；不做就是"装得上跑不动" |
| P1 | `action` 弹窗 + `contextMenus` 接到菜单 | 现在只存不显示；`setBadgeText` 也一样 |
| P2 | MV3 真 service worker 生命周期 | 现在当常驻页面跑 |
| P3 | `run_at: document_start` | 现在统一 `document_idle` |
| P4 | poll 开销 | 有扩展的页面每秒 2~3 次动作；若 Chromium 以后恢复页面 eval API，可整体替换掉（shim 表面不用改） |
| — | 冷启动延迟 | 会话第一条消息要等宿主标签页起来（3 个扩展时约 3~5 秒）。必要时可在页面加载时预热宿主页 |

## 维护者回复后怎么接

1. `gh pr view 319 --repo JingMatrix/ChromeXt --comments` 看意见；`gh pr checks 319` 看 CI（批准后才会跑）。
2. 要改代码：在本分支上改 → `gradle format app:assembleDebug` → 设备验证 → `git commit` → `git push fork extension-support`（PR 自动更新）。
3. 若要求去掉 `test-ext/` 或换掉本地管理页（接回他的 `manager.vue`）：保留 `LocalFiles.start()` 原来的
   `{type:"init", manifests:[…]}` 载荷即可，`Extensions.list` 直接能映射过去。
4. 若被要求拆 commit：`2619835`（AGP）可以单独 revert；`73b2075` 里的改动也可以再拆成"消息通道"与"安装 id"两条。

## 收尾状态

- 工作树干净（`git status --short` = 0），ktfmt 已跑过，`gradle app:assembleDebug` 通过。
- `ws.sh check` 只剩一条**不是这次产生**的历史散落：`/data/local/tmp/cb-backup-20261104-2200`（11-04 的旧备份，要清就 `rm -rf`）。
- 设备上保留的三个扩展可以直接在管理页（页面菜单 → Extensions）里启停/卸载，方便下次继续验证。
