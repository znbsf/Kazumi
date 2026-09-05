# TV 焦点回归记录

2026-09-05：修复主页侧栏返回、播放中选集入口和弹幕输入抢焦点。

## 行为

- 内容区向左没有下一个焦点时，回到左侧导航栏，并保留侧栏上次焦点。侧栏按右可重新进入内容区。
- TV 播放控制栏始终提供选集按钮，不受手机/平板或全屏标志限制。
- TV 的发送弹幕入口为按钮。方向键聚焦不唤起输入法，按确认才打开输入对话框。关闭对话框后恢复入口焦点。

## 验证

- 修改文件 Flutter analyze 通过；现有遥控器快捷键和焦点组件测试共 12 项通过。
- TV Debug 构建成功，versionCode 203003。
- 在 MiTV-ASTP0 通过 ADB 注入方向键验证：侧栏进入第一张卡片，向左返回侧栏，再向下确认可打开时间表。
- 真实视频播放中，方向键到达选集按钮，确认打开分集面板并聚焦当前第 2 集。
- 聚焦发送弹幕时系统 mInputShown=false；确认打开输入对话框后为 true；返回关闭输入后可向右聚焦超分辨率按钮。
- 本轮使用仅在本地测试 APK 中注入的合成弹幕验证输入入口。注入代码已撤回，合成弹幕及 APK 不提交到仓库；这不代表在线弹幕服务已验证。
- 本轮不构成所有电视、输入法及遥控器型号的覆盖。

上游开发说明：https://github.com/Predidit/Kazumi/issues/2500#issuecomment-5549231165

## 公开预览版 203004

后续按用户要求将合成弹幕放入公开预览 APK，但仍不放入普通构建。
OK 的几何推测焦点改为显式播放/选集落点，子面板退出后根焦点可再次进入控件。
同一签名/哈希 Release APK 在 Google TV AVD 与实体 MiTV 完成复测，见
[Preview 1 验证记录](TV_PREVIEW_1.md)。此前 203003 的临时注入说明仅适用于旧包。

## 透明面板与循环导航：本地回归包 203007

2026-09-05。本轮只使用专用 Google TV API 36 AVD，不操作实体电视，不更新 GitHub Release。

### 行为变化

- 选集的外层主题底板为 64% 不透明度，内层透明；卡片底色为 30% 不透明度。没有增加模糊滤镜。打开选集时隐藏播放器控制栏并排除其焦点，避免跳到面板后面的按钮。
- 底栏集中排列，播放键右侧就是带文字的选集按钮。左右在实际存在的按钮中循环；上下在两排间循环。顶部增加应用主页按钮。
- 分类与操作设置页签首尾循环；侧栏上下循环，左键可跳到内容右侧。主页首列左键仍回侧栏、首行上键仍可到分类，避免内容区成为焦点陷阱。
- 选集网格左右按当前行循环，末行只使用真实集数；底部向下回到同列开头。第一行上键保留到线路/页签的通路。主页只在已加载项目中循环，不为循环无限请求分页。
- 长按返回至少 900 ms 后松开回到 `/tab/popular/`；短按经原有 PopScope 逐层返回；输入法显示时不拦截。Android 系统 Home 不变。
- 详情页媒体 Play / PlayPause 和开始观看按钮共用入口；优先复用现有 HistoryPlaybackService 恢复历史，失败或无历史时选源。按键重复、后台详情页和文本输入不触发重复播放。

### 自动验证

- `flutter test --no-pub`：208 项通过；本轮相关焦点/遥控器/编号测试 22 项通过。
- 修改 Dart 文件静态分析无问题；`git diff --check` 通过。
- TV Release universal 构建成功，versionName 2.3.0，versionCode 203007。
- 最终 APK 与 AVD 已安装 base.apk 的 SHA-256 一致：
  `62c46b514c367040213d4923b1460e0b0ab4c4356a740b45798698e3293e84c2`。
- 合成弹幕仍仅通过原有预览构建参数注入，没有新增真实在线弹幕或密钥。

### AVD 操作证据

截图保存在未提交的 `artifacts/` 下，不使用文件名代替图像复核：

| 路径 | 观察结果 | 截图 |
| --- | --- | --- |
| 详情页按 Play | Re:Zero 第四季恢复第 2 集及已有进度，未重新要求选源 | `nav-detail-play-picker.png`（早期命名，实际是已进入播放器） |
| 无观看历史的详情页按 Play | 打开选择播放源，没有任意选择搜索结果 | `nav-no-history-picker.png` |
| 播放键左移，再右移 | 左端到最右端全屏按钮，再回播放键 | `nav-controls-last-verified.png`、`nav-controls-first-verified.png` |
| 播放键上移，再上移 | 到顶部返回键，再循环到底部播放键 | `nav-controls-top-verified.png`、`nav-controls-vertical-wrap.png` |
| 第 2 集左、左、右 | 焦点经第 1 集循环到第 4 集，再回第 1 集，不切换播放 | `nav-episode-wrap4.png`、`nav-episode-wrap1.png` |
| 节目首列向左回侧栏，再选择历史 | 成功到达历史页面 | `nav-final-rail-history.png` |
| 侧栏搜索入口上移 | 循环到遥控器入口，OK 打开说明 | `nav-rail-up-before-ok.png`、`nav-rail-up-after-ok.png` |
| 最终包透明面板 | 可见面板后面的真实视频，控制栏已隐藏 | `nav-203007-transparency.png` |
| 最终包发送按钮与输入法 | 聚焦时 mInputShown=false；OK 后为 true；返回收键盘仍停在输入对话框，再返回后右键可到超分按钮 | `nav-203007-send-focus.png`、`nav-203007-ime-open.png`、`nav-203007-ime-back.png`、`nav-203007-input-return.png` |
| 最终包播放器长按返回 | 从仍有控制栏的播放器直接回 LCN 首页，不只关闭一层 | `nav-203007-home-from-controls.png` |

### Android 16 返回键适配

中间包 203005 / 203006 的长按测试未通过，不能作为完成证据。API 36 的默认 predictive back 路径不向 Activity 分发原始 KEYCODE_BACK；最终仅在 TV flavor 的 Activity 设置 `enableOnBackInvokedCallback=false`，让实体遥控器的 DOWN/UP 可以区分长短按。手机 manifest 不变。持有时间使用单调时钟，避免部分旧输入注入器复用事件时间戳。

参考：[Android 返回机制说明](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)、[AOSP 输入注入实现](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/input/InputShellCommand.java)。这不代表系统 Home、被厂商预先拦截的键或未来 Android 版本都可由应用接管。

边界：未覆盖所有设置子对话框、长列表的每个位置、厂商遥控器重复事件及实体电视输入法。深/浅主题透明度有组件测试，真实视频截图本轮为 AVD 深色主题；不把它当作所有电视的视觉验收。

## 详情页主操作区：本地回归包 203010

2026-09-05。仅 Google TV API 36 AVD（1920×1080），不安装实体电视或发布 GitHub Release。

- TV 的开始观看、追番和发表吐槽位于海报旁、标题下方；移除右下角 FAB。手机版继续原卡片与悬浮按钮分支。
- 默认聚焦开始观看，数据加载占位不覆盖按钮、不抢走用户已移到其他操作的焦点；左右在三个操作中循环，上下明确连到返回与当前页签。
- 页签上键回到播放并展开头部，下键聚焦可见页签时不额外折叠头部。较大字体同步增加头部高度。
- 追番菜单使用当前页面的 LocalHistoryEntry 接住原生短返回；弹出时聚焦当前追番状态，关闭后回到入口。长按返回仍回主页。

### 验证与边界

- 新增 `tv_detail_actions_test.dart` 8 项测试，完整 `flutter test --no-pub` 共 216 项通过；修改文件静态分析通过，`git diff --check` 通过。
- 测试包含实际 CollectButton 与 Navigator.maybePop（不是用 Escape 冒充电视返回）、菜单箭头、默认 OK、左右循环、上下出口、加载后焦点保留、原手机版卡片、854/960/1280 逻辑宽度及 1.25 倍字体。
- 早期 203008 发现 MenuAnchor 不是路由：返回会退详情而留下菜单。已补真实组件回归测试，在 203009/203010 实测关闭菜单不退页。203008 不作为验收包。
- 最终 APK：`Kazumi-TV-2.3.0-detail-test-203010.apk`，2.3.0 / 203010，universal Release；与 AVD base.apk 的 SHA-256 一致：`262595628b7a75a2a0d92a26b1a75f4b27fda098fd81f24d3ae167cd439dcc68`。
- 预览合成弹幕仍使用已有显式构建开关，本次不增加验证码诊断、真实验证码答案、Cookie 或页面资源到 APK。

| 操作 | 实际观察 | 本地截图（未提交） |
| --- | --- | --- |
| LCN 1 进入详情 | 开始观看在标题下、海报右侧，默认描边 | `artifacts/detail-203010-play-focus.png` |
| 播放下键到页签，上键后左键 | 页签可见时不卷走头部；回播放后首尾循环到发表吐槽 | `artifacts/detail-203010-down-tabs.png`、`artifacts/detail-203010-left-wrap.png` |
| 追番 OK、下键、短返回 | 菜单内移动，返回后仍在详情并聚焦追番入口 | `artifacts/detail-203009-menu-down.png`、`artifacts/detail-203010-menu-return.png` |
| 无历史的尼古喵喵详情，直接 OK | 打开选择播放源，未猜测来源 | `artifacts/detail-203010-no-history-ok.png` |
| 明确选择尼古喵喵的 7sefun 结果；回主页再进详情，直接 OK | 第 1 集真实视频出画；第二次不再选源，直接恢复同集播放并出画 | `artifacts/detail-203010-neko-later.png`、`artifacts/detail-203010-neko-resume-later.png` |
| 有历史的 Re:Zero，主按钮 OK | 进入第 2 集播放器，但本次原历史来源仍加载中，未得到视频出画；仅证明入口与续播参数，不记作流媒体端到端成功 | `artifacts/detail-203009-ok-playback.png`、`artifacts/detail-203009-playing.png` |

验证码另外收到三类来源对照，尚未实现诊断与修复，见 [待办](TV_CAPTCHA_DIAGNOSTIC_PLAN.md)。

## 实体小米电视回归：203010（2026-09-05）

用户随后明确要求实际测试，本节是独立的真机记录，不覆盖上面的 AVD 历史边界。

- 设备：MiTV-ASTP0 / mulan，Android 9 / API 28，1920×1080，应用实际使用 armeabi-v7a；浅色主题。
- 保留数据覆盖安装，从 203004 升级为 203010，包名 `com.znbsf.kazumi.tv`。安装后的 base.apk 与本地测试 APK SHA-256 均为 `262595628b7a75a2a0d92a26b1a75f4b27fda098fd81f24d3ae167cd439dcc68`。
- 使用 ADB 向实体电视注入方向、OK、短返回、MEDIA_PLAY 键码；不是模拟器，也不是实体遥控器硬件全键验收。未改应用代码、未清数据、未修改系统配置、未发布 Release。

| 项目 | 真机观察 | 本地截图（未提交） |
| --- | --- | --- |
| 详情页布局与默认焦点 | 标题下、海报右侧的开始观看默认描边；加载占位不挡操作；退出视频仍横屏 | `mitv-203010-play-focus.png`、`mitv-203010-detail-after-video.png` |
| 主操作导航 | 播放左键到吐槽，右键回播放；下键到概览且不卷走头部；上键返回播放，再上到返回按钮 | `mitv-203010-left-wrap.png`、`mitv-203010-down-tabs.png`、`mitv-203010-up-back.png` |
| 追番菜单 | OK 打开，方向键移动；短返回只关菜单，留在详情并恢复入口焦点；未提交追番状态变更 | `mitv-203010-menu-down.png`、`mitv-203010-menu-return.png` |
| 无可用历史的 OK | 打开来源列表；方向键进入 7sefun 的第四季结果，OK 后第 1 集实际出画 | `mitv-203010-ok-play.png`、`mitv-203010-source-first.png`、`mitv-203010-play-start.png` |
| 有历史的媒体 Play | 退出播放器及下层来源弹窗回详情，MEDIA_PLAY 直接恢复同来源第 1 集约 4:36，随后到 5:12 有真实画面 | `mitv-203010-media-resume.png`、`mitv-203010-send-focus.png` |
| OK 唤醒与选集入口 | 隐藏控制栏时 OK 只唤醒并聚焦播放；右键就是选集，OK 可重新打开 | `mitv-203010-ok-wake.png`、`mitv-203010-episodes-button.png` |
| 选集透明及导航 | 浅色面板可见底下真实视频；第 1 集左键到 4，第 13 集下键回 1，第一行上键到选集页签 | `mitv-203010-episode-left.png`、`mitv-203010-episode-bottom.png`、`mitv-203010-episode-down-wrap.png`、`mitv-203010-episode-up.png` |
| 选集短返回 | 只收起面板，视频继续；再返回离开播放器，若从选源进入则先回到原来源弹窗 | `mitv-203010-panel-back.png`、`mitv-203010-exit-detail.png` |
| 发送弹幕焦点 | 方向键到发送弹幕，无软键盘；系统 `mInputShown=false`、`mShowRequested=false`，本次未提交弹幕 | `mitv-203010-send-focus.png` |
| 控制栏循环与主页 | 播放左键到最右全屏、右键回播放；上移到顶栏、右键到主页，OK 回 LCN 首页 | `mitv-203010-controls-left-wrap.png`、`mitv-203010-home-button.png`、`mitv-203010-home-return.png` |

截图均在本地 `artifacts/`，不应将整个目录加入提交。

### 新发现，尚未修复

1. **首页首列回侧栏未通过。** 从顶部分类下移可以看到卡片边框；校园分类中让第 1 张冰菓卡片出现焦点框后再按左，实际切换到顶部日常分类，而非稳定进入左侧栏。复现截图：`mitv-203010-grid-single-left.png` → `mitv-203010-card1-left.png`。源码中首列左键交还默认遍历，外层又先运行 ReadingOrderTraversalPolicy，仅在其失败时才转侧栏；需要补真实主页/侧栏组件测试并显式确定边界出口，不能仅凭网格索引单测宣称通过。上述路径的根因仍需后续确认。
2. **播放状态和焦点样式混淆。** 正在播放第 1 集、焦点移到第 4 或 13 集时，两者均有粗绿色框。应保留播放状态标识，同时让当前遥控焦点的样式可区分；本次仅记录，不修改测试包。

### 测试边界

- ADB 曾短暂无法连接本机 daemon；停止该组操作、重新核对设备和画面后重测，断连期间的按键不计入通过。`mitv-203010-episode-wrap.png` 不是循环通过证据。
- Android 9 的 `input keyevent` 不支持指定持有时长；未把 `--longpress` 当成应用要求的 900 ms 长返回验收。可见主页按钮已验证，实体遥控器长按仍待人工验证。
- 本地合成弹幕可见且标注为本地示例，不代表官方在线弹幕成功；未验证听感、长期 A/V 同步、帧率/卡顿、所有来源或硬解性能。截图中持续出画只证明本次短程播放路径。
- 本轮结束留在应用首页，归还电视；发现的问题保留待办，不将当前包标成全部遥控导航验收完成。

<a id="ui01-ui04-local"></a>

## UI-01～UI-04 独立分支本地回归（2026-09-06，未发布）

基于 `f699c805763f57607a8da329b3b00089664f6d0c`，工作树 `Kazumi-tv-focus`，
分支 `codex/tv-focus-ui01-ui04`。原默认分支保持不变，没有推送、创建 Release、修改线上附件或操作实体电视。
UI-05～UI-12、验证码、手机接力和台标不在本批范围，播放内核 / 音频 / 硬解 / 弹幕凭据未修改。

### 修改和对照

- **UI-01：**主页首列 LEFT 显式发往侧栏；RIGHT 恢复原内容焦点。第一行 UP 回当前分类，分类 DOWN 进入首卡，
  分类 UP 回侧栏；分类异步完成时不抢走已离开的焦点。详情路由返回恢复原卡片，而非强制跳搜索。
- **UI-02：**分类选中使用填充和文字；选集播放状态保留原动态图标与主色文字，只有当前焦点使用粗框。
  `EpisodeTile` 从原播放器中抽出复用，未重写播放逻辑；紧凑网格和搜索条不向视口外放大，以免裁掉边框。
- **UI-03：**TV 使用可聚焦的只读入口，OK 才打开独立编辑层；保留图片搜索、最近十条搜索记录重用 / 删除。
  搜索提交不再假定 SearchController 已挂在 SearchAnchor。手机版仍用原 SearchAnchor 分支。
- **UI-04：**卡片 OK 仍调用原 HistoryPlaybackService；RIGHT 到独立“更多”，可继续观看、看详情、追番、删除。
  删除需确认且默认取消，取消 / 返回恢复入口；删除后聚焦下一条，空列表有返回出口。
  TV 历史按可用宽度选择列数，保留文字空间；长列表 DOWN 先实现离屏目标，末尾循环只落到真实记录。
  新历史菜单 / 删除按钮显式描边，避免 Android 自动触摸高亮策略隐藏当前焦点。手机滑删和内部操作保持原分支。

`test/tv_focus_baseline_probe_test.dart` 在基线工作树使用旧 SearchPage / HistoryPage / HistoryCard 运行：
两项均按预期失败，分别是“搜索 OK 后未进入编辑”和“历史编辑 OK 没有删除确认”。
日志 `artifacts/baseline-probes.log`。同一对照在修改后通过；这不是在实体电视重新运行旧 APK 的证据。

### 自动测试

- 最终 `flutter test --no-pub`：**235 项通过**，含基线对照 2 项和新回归 17 项；日志 `artifacts/all-tests.log`。
- 修改的 11 个 Dart 生产文件、回归测试及 test/support 静态分析：**No issues found**；`git diff --check` 通过。
- 覆盖真实应用壳层 / 页面，不只验证网格索引：首列侧栏往返、详情返回、空分类、单项 / 31 项不齐行网格，
  搜索明确激活 / 取消 / 提交，真实追番菜单的 `Navigator.maybePop` 逐层返回，续播参数、删除取消 / 下一条 / 空列表，
  25 条历史越屏和末尾循环，854×480 / 1280×720 逻辑尺寸与 1.25 倍字体、原手机版搜索分支。
- 深浅主题的真实 EpisodeTile 验证：焦点移到第二集时只有一条焦点框，第一集的播放动态图标仍存在。
  这不等于真实视频背景、全部选集面板控件或所有主题对比度已验收。

### 独立模拟器操作

AVD `Kazumi_Focus_UI_API36`，Google TV API 36 / x86_64，1920×1080（逻辑 960×540）。
所有 ADB 操作显式指定 `-s emulator-5562`；没有操作另一台 AVD 或实体 MiTV。

离线入口在 `test/support/tv_focus_fixture_app.dart`，使用实际主页 / 搜索页 / 历史页 / 选集组件，
但番剧、历史与服务结果是合成数据，详情和续播终点是注明用途的占位路由，**没有真实视频播放**。
Hive 目录为独立 `focus-fixture-only`。这些入口不被 `lib/` 或正常 main 导入，正常 APK 不启用它们。

| 注入操作与观察 | 包 / 证据 |
| --- | --- |
| 主页第 7 卡 LEFT 回侧栏，RIGHT 恢复同卡和原滚动位置，热门分类未切换 | 203014；[恢复截图](screenshots/ui-focus-local/home-restored.png)；本地另有 `ui01-rail-203014.png` |
| 第 1 集标记在播，焦点 LEFT 循环到第 4 集，只有第 4 集粗框 | 203014；[真实选集组件状态样例](screenshots/ui-focus-local/episode-focus.png)，非真实 VideoPage 出画 |
| 搜索方向键到图片搜索再返回输入入口，无键盘；OK 打开系统键盘 | 203014 / 最终 203018；系统 `mInputShown=false → true`；[最终入口](screenshots/ui-focus-local/search-entry.png) |
| 原生 BACK 第一次收键盘且输入层仍在，第二次关闭输入层并恢复入口，第三次返回原壳层 | 203014 / 203018；本地 `ui03-back1-203014.png`、`ui03-ok-ime-203014.png` |
| 历史 RIGHT 到更多，OK 打开，方向键进入追番子菜单，BACK 逐层退出并恢复入口 | 203014；本地 `ui04-collection-203014.png`、`ui04-cancel-restored-203014.png`；最终 [更多焦点](screenshots/ui-focus-local/history-more.png) |
| 删除先落在取消；BACK 不删除，再确认后只删除样例 1，焦点落在样例 2 | 最终 203018；[确认框](screenshots/ui-focus-local/delete-confirm.png)；本地 `ui04-deleted-next-203018.png` |
| 最终历史页为两列，样例标题和来源可读；保留原配色与卡片结构 | 最终 203018；[历史页](screenshots/ui-focus-local/history.png) |
| 安装正常入口的 Release 构建，冷启动显示欢迎 / 声明页，没有出现模拟主页 | 203017；本地 `normal-203017-start.png`；未将初始化页当成网络源或播放验收 |

调试包曾在并行构建期间冷启动超过窗口输入等待，过早注入 OK 导致一次输入 ANR；该组按键和过渡截图作废。
重新等待应用窗口就绪后执行上述操作，最终正常包冷启动 `am start -W` 返回 ok（约 1.3 秒）。
不据此推断长期性能或已修复启动性能问题。203012 / 203014 / 203016 是中间验证包，最终样式包为 203018。

### 最终本地产物与重现

```sh
flutter test --no-pub
flutter build apk --debug --flavor tv --build-number 203018 --build-name 2.3.0-focus-fixture --target test/support/tv_focus_fixture_app.dart --no-pub
flutter build apk --release --flavor tv --build-number 203017 --build-name 2.3.0-focus-test --no-pub
```

| 本地 build/ 文件 | SHA-256（已与对应 AVD base.apk 核对一致） |
| --- | --- |
| `Kazumi-TV-2.3.0-focus-test-203017.apk`，正常入口 universal Release | `5138268518c8ee30f0d64d760f74428beaae487f93f19174b8856bea6f6a579b` |
| `Kazumi-TV-2.3.0-focus-fixture-203018-debug.apk`，仅离线验证用 | `9b7d3d5ae5e5f3e45597272536ff2b567bc09dea99dd22e915dc0790850b52cc` |

两者包名均为 `com.znbsf.kazumi.tv`，不能同时安装。验证后 AVD 留在正常 203017 包的欢迎页；
从可调试 fixture 覆盖回正常包时使用 `adb install -r -d`，没有清数据或接触用户电视。
构建保留原工具链；Gradle / AGP / Kotlin 后续支持和 Java 8 过时警告未在本批升级。

**仍未关闭的验收门：**实体遥控器 / 小米电视复测，在线搜索与网络历史续播出画，真实播放器面板集成，
搜索记录删除后的长列表焦点细节、所有设置 / 图片搜索子页、长时间 A/V / 内存稳定性。
自动测试数与离线截图不替代这些项目；公开 Preview 2 的标签、APK 和发行说明未因本批修改而改变。
