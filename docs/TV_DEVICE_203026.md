# 小米电视安装与桌面入口验证：203026

2026-09-06，用户明确要求覆盖安装到自己的电视，并确认电视桌面能发现应用。
本轮范围与此前仅 AVD 的记录分开：实际目标为 MiTV-ASTP0 / mulan，Android 9 / API 28，
1920×1080，应用运行 ABI 为 armeabi-v7a。没有操作手机、其他 AVD 或修改网络设置。

## 安装与桌面入口

- 旧版 203010 只注册 LEANBACK_LAUNCHER：电视的普通 LAUNCHER 查询返回 No activities found。
- TV flavor 的同一个 MainActivity 增加 MAIN + LAUNCHER，原 LEANBACK_LAUNCHER、TV 图标与 banner 保留。
  没有复制活动、改包名或更改手机版 manifest。
- 新包同时通过普通启动入口及 Leanback 入口检查；小米“我的应用”实测显示 **Kazumi TV**，位于应用区第一项。
- 从小米桌面顶栏“我的应用”按 OK 进入应用列表，方向键聚焦 Kazumi TV，再按 OK 成功进入 App。
  这次实际启动来自桌面按键，没有用 `am start` 代替桌面启动验证。
- `adb install -r` 从 203010 覆盖到 203026，没有卸载 / 清数据；旧 APK 已备份。
  运行后仍可读取原有 13 条规则，以及原观看历史和浅色主题。

## 包身份

| 项目 | 结果 |
| --- | --- |
| 本地产物 | `build/Kazumi-TV-2.3.0-tv-device-test-203026.apk` |
| 版本 | `2.3.0-tv-device-test` / `203026` |
| 包名 | `com.znbsf.kazumi.tv` |
| 架构 | universal：armeabi-v7a / arm64-v8a / x86_64；电视实际使用 armeabi-v7a |
| APK SHA-256 | `e26e329196a6e485683c5ce78118c5f9b3cb0e66f23b739ddaaa378d5202b690` |
| 签名证书 SHA-256 | `24f6145444bd07c2db4d3d355692e4dae3dc02cd632a933a9e03a27b9e32aa31`，与旧包一致 |

电视安装后的 base.apk 与本地新包 SHA-256 相同。构建日志报告 Built，APK 已安装并运行；
外层 Flutter / PowerShell 构建调用仍返回 exit 1，不将其记作零退出构建。
原始安装包备份在忽略目录 `artifacts/tv-203026/previous-203010.apk`，不是完整应用数据备份。

## 真机短程操作

以下为向实体电视注入方向键、OK 和返回键的结果，不等于实体遥控器硬件全键覆盖：

| 路径 | 观察 |
| --- | --- |
| 从系统“我的应用”启动 | 图标和名称可见，方向键选中后 OK 打开正常 TV 首页 |
| 首页加载 | 原规则目录及在线推荐均 HTTP 200；这台电视的网络可用，不代表专用 AVD 的网络问题已解决 |
| 内容区左键返回侧栏 | 可回到侧栏，继续导航打开历史；未覆盖所有卡片和快速输入时序 |
| 历史首卡 OK | 恢复原 7sefun 来源的尼古喵喵第 9 集，解析后实际出画 |
| 第 9 集 UP → RIGHT | 焦点到第 6 集，仍播放第 9 集；只有焦点使用粗框，当前集保留播放图标与绿色文字 |
| 浅色选集面板 | 真实视频可透过面板看到，当前集与焦点可区分；本样本未见标题溢出 |
| BACK 关闭选集，再 OK | 控制栏出现，焦点落在播放 / 暂停键；节目仍为第 9 集 |

播放器截图显示约 1:16 / 23:42；稍后系统 MediaSession 报 playing、position 87420 ms、error=null。
仅证明本轮短程真实播放和状态推进；没有进行 PTS / wall 测量，不据此宣称长期流畅性或音画同步通过。
测试结束后不再注入按键，将正在播放的电视交还用户。

## 边界与本地证据

- 这是正常 `lib/main.dart` 入口的本地测试包，不包含上一轮录屏样片入口或样例历史。
- 没有启用 Preview 2 的合成弹幕构建开关，也没有注入在线弹幕凭证；日志明确提示缺凭证。
- 暂停 seek 后退出可能丢失新进度的 UI-13 仍未修复。本轮没有再次对用户历史执行该破坏性复现。
- 生产 Dart 代码沿用上一轮 250 项测试通过的内容；本轮新增改动是 TV manifest 的启动过滤器，
  已用 APK 检查、安装后查询和桌面实际启动验证，未将旧测试次数冒充本轮重跑。
- 初次系统桌面截图为 0 字节，原因未定位，不作为截图证据；进入“我的应用”后截图正常。
- 部分早期 App 截图早于页面更新，文件名不能代替判读；05 / 06 的图片不是历史页成功证据。
- 本地证据在忽略目录 `artifacts/tv-203026/`：
  `02-myapps-kazumi.png`、`03-launched-from-desktop.png`、`08-playing-panel-focus.png`、`09-ok-controls.png`，
  以及 `myapps-kazumi-focus.xml`、构建日志和旧 APK。

以上为发布前的安装验证记录；当时未提交 / 合并 / 推送。
随后该 APK 以 [Preview 3](TV_PREVIEW_3.md) 发布，只重命名为
`Kazumi-TV-2.3.0-tv-preview.3-universal.apk`，哈希、内部版本和签名不变。
发布前另行重跑 250 项测试全部通过。Preview 2 的标签、APK 和发行说明保留不变。
