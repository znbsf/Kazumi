# A 线第一阶段交付事实（2026-09-12）

## 2026-09-13 真机反馈追加

用户授权安装小米电视并继续修复后，开发分支新增如下改动（下文冻结 SHA 与纯补丁仍是前次交付快照）：

- TV 分类栏使用独立底色，普通分类与导航底色分开；非懒加载横向容器确保离屏分类可获取焦点，
  焦点变化双向居中滚动。真机向右移动后回到“悬疑”，标签和焦点框完整可见。
- 数字角标采用 45% 不透明度的深色底板与白字，可透出海报。
- TV 与手机统一保留低内存三选项界面，仅 TV 默认“始终开启”。尊重用户保存的所有模式，
  包括“跟随网络”；此前误改为开关的 UI 和强制映射已撤回。并非新增降低清晰度功能。
- 全量 322 项测试通过；静态分析 0 error / 0 warning / 5 既有 info。
  新回归覆盖分类双向遍历、首尾循环和 TV/手机低内存策略差异。
- `artifacts/phase1-hardening/physical-tv-a-ui-test.apk` 为 ARMv7 真机包，
  `2.3.1-a-ui-test / 203041`，覆盖安装成功、保留数据。
  截图 `tv-ui-fixed-home.png`、`tv-ui-fixed-right-far.png`、`tv-ui-fixed-left-suspense.png`。
  此次未重新执行完整播放验收；旧纯 TV 导出补丁尚未包含这次追加。

随后纠正低内存 UI 并修复设置双栏焦点：TV 保留与手机相同的三选项，默认值不同；
设置内容左键返回当前分类，分类右键进入内容。新增嵌套路由组件回归通过，
相关分析无问题；低内存及首页16项回归通过。真机按键已从播放设置切换到弹幕设置，
再经内容/分类往返进入操作设置。截图 `settings-memory-three.png`、
`settings-fixed-category.png`、`settings-fixed-keyboard.png`。
当前电视包为 `physical-tv-a-ui-test3.apk`（`2.3.1-a-ui-test3 / 203061`），
覆盖安装并确认界面；保留导航底色、分类滚动和半透明角标修复。

再追加最小分类缓存：`PopularController` 保留最近8个分类，每类最多240条，10分钟有效，
仅内存、镜像/原站分键，图片仍独立缓存。复用列表和分页，空/失败结果不缓存，
切换分类使旧请求失效。顶部分类名称本来就是本地常量。本轮新增3项缓存测试通过，
首页16项回归通过，相关分析无问题。真机“恋爱→悬疑→恋爱”只新增悬疑请求，
证据 `cache-requests-before.txt` / `cache-requests-after.txt`。
最新安装包 `physical-tv-a-cache-test.apk`（`2.3.1-a-cache-test / 203071`），
取代上一测试包；未添加闪存持久化，控制器销毁后缓存消失。

首页两行布局追加：TV 按屏幕高度扣除固定分类栏、间距后等分两行，卡片按高度收窄，
海报仍保持0.65宽高比，标题最多两行省略；手机布局保留。TV 首页隐藏会遮挡右下卡片的
回顶部浮动按钮。全量327项测试通过，随后隐藏按钮的定向检查通过，相关分析无问题。
真机 `two-rows-tv-final.png` 确认两行12张封面与标题区域完整显示、无浮动按钮遮挡。
最新包 `physical-tv-a-rows-test2.apk`（`2.3.1-a-rows-test2 / 203091`）已覆盖安装。

代码冻结于 `25440c29307f7a09b06e6b93ea688ba3ebd21f91`，私有分支
`codex/tv-phase1-hardening`。已 rebase 到执行时及交付前重新查询均为
`2624e0c0f4a0be2be658f833a510090cde8b5cf5` 的 upstream/main。
旧完整提交链保存在 `codex/tv-phase1-pre-rebase-20260912`（`ca5006c`）。
本轮未提交 PR、发布或操作真机；不含 B 线 P2 实现。

## 修复和保留的行为

- 冷启动 OK 可进入搜索；历史页 RIGHT 遇到空 FocusScope 时回退到可操作控件。
  历史页首个控件是管理按钮，不能描述为直接聚焦续播卡。
- 搜索区分请求失败、成功空结果及分页失败；保留新上游搜索参数、筛选与手机 SearchBar。
- UI-13 从所属原生播放器会话读取位置、时长和播放状态，暂停 seek 后也可保存；
  销毁前捕获快照，串行写入、成功去重，未真正播放或未知时长不写历史。
  近结尾清零及隐私策略仍由原有业务规则决定。
- 保留上游历史分组/筛选、详情页结构、抽取后的 EpisodeSelectionPanel、
  新设置卡片及 LowMemoryMode 三态。TV 选集使用四列网格与独立焦点/滚动逻辑；
  删除的 playing.gif 引用替换为图标。播放器菜单阻挡条件限定 TV，保留手机行为。

## 自动验证与构建

原始纯第一阶段基线 264 项测试通过；最终候选 320 项通过
（`final-tests.log`，`ae183bcc`，随后 `25440c29` 仅增加 lint 要求的花括号）。
最终代码分析 0 error、0 warning、5 条既有 avoid_print info
（`final-analyze-verified.log`）。手机 390×844 / 字体 1.3 搜索提交、
99 集实际选集面板的末行换行和选择、冷历史焦点及原生历史录制器均有组件/单元用例。

以下路径均相对本工作树 `artifacts/phase1-hardening/`，完整 SHA 和构建入口记录在同名 JSON。
正常入口为 `lib/main.dart`，release、android-x64；不能据此声称 ARM 真机安装或播放通过。

| 产物 | 源码 | 验证 |
| --- | --- | --- |
| `final-tv-25440c29.apk` | 25440c29 | TV release 成功，SHA256 `fe919d5862487dee46c9490d52f1bf40ff9456c0d25e04fe96324d4ea3dd7ec1` |
| `final-mobile-25440c29.apk` | 25440c29 | mobile release 成功，SHA256 `90582cb00f1dc9446cd2c9c9de98896ed5607bb34bb93734ab298578f3b3c15c` |
| `final-local-history.apk` | ae183bcc | 原生本地样片验证入口；不是发行包 |
| `rebase-ui-377abfde.apk` | 377abfde | 合成 API 响应的正式页面验证入口；不是在线服务验收 |

## 本轮 AVD 二次回归

仅使用独立 `Kazumi_Phase1_A_API36 / emulator-5562`，Google TV API36 x86_64，
SwiftShader、无音频。下面列的是实际观察，不把 fixture、按键注入或乐观位置当播放验收。

| 场景 | 证据文件 | 观察与边界 |
| --- | --- | --- |
| 正常入口冷启动 OK | `rebase-normal-cold-ok.png` | 搜索入口有焦点，IME 未显示 |
| 正常网络请求失败 | `rebase-normal-search-failure.png` | 实际超时显示请求失败与重试；查询使用触摸选历史词 |
| 离线首页/详情 | `rebase-ui-detail55.png`、`rebase-ui-detail-tabs.png`、`rebase-ui-detail-collection.png` | LCN 55、详情动作/页签/收藏菜单可达；无真实源播放证明 |
| 长列表双向移动 | `rebase-ui-long-down.png`、`rebase-ui-long-up.png` | 焦点 67 向上回到 61，卡片完整可见 |
| 720p / 字体1.3 / 浅色 | `rebase-ui-timeline-720-large.png`、`rebase-ui-collect-720-large.png` | 时间表和31条追番布局可读；不等于全部 D-pad/IME 流程通过 |
| 初始化步骤 | `rebase-ui-onboard-updates.png`、`rebase-ui-onboard-mirrors.png` | OK 推进到2/4、3/4，固定动作可见 |
| 冷历史 RIGHT→OK | `final-local-history-edit.png` | 进入“完成”管理态，证实空 scope 修复；不宣称首焦点是续播 |
| 原生续播与暂停 seek | `rebase-local-resume58.png`、`rebase-local-paused-seek.log` | 首次从58秒持续输出真实帧；暂停 seek 到45.7秒，原生状态和历史均45.7秒 |
| 二次进入 | `rebase-local-reopen-seek.png` 及原生日志 | 恢复取整45秒，但 goldfish 解码停在黑屏/45秒；持续播放未通过，原因未定 |

没有音频听测、A/V 同步、真机遥控器或长时间稳定性验收。诊断页按键在 AVD 上未取得
可靠界面证据。历史管理态 BACK 返回首页的上游嵌套路由行为未在本轮修复。
UI-05～UI-09、UI-11 和其余 UI-12 体验建议不自动视为完成。
独立 AVD 的显示/密度覆盖及字体已恢复，随后正常关闭并保留数据。

## 纯 TV 补丁和小 PR 拆分事实

独立导出的 `pure-tv/pure-tv.patch` 基于 upstream `2624e0c0`，不是当前 fork 分支的全量 diff。
树 `7bc27f9b8d34fe185efac6f55568335162fe0b1f`；SHA256
`71d73b2f494666d11c22180456a700b8cd5b80a79e62f69c8bf5b4ca1ed21706`。
已验证 `git apply --cached --check`、实际应用后树一致；提取源码全量 311 项测试通过。
源文件清单、排除项及适配记录见 `pure-tv/manifest.json`；分析同样为 0 error、0 warning、
5 条既有 info，见 `pure-tv/analyze-final.log`。
该提取树没有单独构建 APK；上表 APK 属于 fork 候选树。

| 可拆评审单元 | 主要事实/依赖 |
| --- | --- |
| 通用搜索缺陷 | 搜索控制器失败/空结果/分页边界及对应测试；可先抽离 TV 外观 |
| 通用历史录制 | recorder、可选初始化回调、原生生命周期快照及测试；需要一起审查播放器所有权 |
| TV 平台基础 | flavor、manifest、原生按键通道、TvMode；上游需决定包名与 CI flavor 矩阵 |
| 逐页 D-pad | menu、历史网格、搜索、详情、时间表/追番等；依赖 TV 基础，可按页面拆分 |
| TV 播放控制 | 实际选集面板、远程按键、诊断入口；依赖 TV 基础和上游新播放器结构 |

纯补丁排除 fork README/文档/截图/个人发行 workflow、品牌包 ID、预览弹幕、
凭据校验特性和开发代理；保留通用弹幕时间轴修复。TV 包使用 `.tv` suffix，
阻止手机 APK 更新发现，文案不指向 fork 下载站。CI flavor wiring 是另一个待评审项。
这些是拆分边界，尚未制作或提交独立 PR；最终 PR 文字由用户撰写并披露确切 AI 使用。
此前读取 Issue #2500 未见维护者批准，不能把本地验证当作维护者接受。

## B 线兼容约定

`PlaybackProgressWriter = Future<void> Function(Duration position, Duration duration)`；
`PlaybackInitParams.onHistoryProgress` 可选，默认 null；`OnlineVideoPlaybackArgs` 不变。
回调绑定会话身份，原生状态确认前不得记录成功。B 的显式 seek/回执逻辑需在此基础上集成。
新上游 `ScaffoldMenu` 需要 location，详情入口保留 inputBangumiItem，选集已抽为组件。
正式代码冻结 SHA 已发送 B；后续本交付记录提交只改文档。
