# Kazumi TV 2.3.0 — Preview 3

非官方开源 TV fork 的第三个公开测试版，**不是稳定版**。本次发布 UI-01～UI-04 焦点修复、
选集长标题布局和电视桌面兼容入口，保留 Kazumi 原有主题及播放内核。手机与 TV 接力仍未实现。

## 下载与升级

- 下载 [Kazumi-TV-2.3.0-tv-preview.3-universal.apk](https://github.com/znbsf/Kazumi/releases/download/v2.3.0-tv-preview.3/Kazumi-TV-2.3.0-tv-preview.3-universal.apk)。
- 包名 `com.znbsf.kazumi.tv`；versionCode **203026**；Android 7.0 / API 24 起，包含 armv7、arm64、x86_64。
- 沿用此前预览测试签名，可覆盖升级并保留应用数据；升级前仍建议备份重要设置和历史。
- **这是已经安装到 MiTV-ASTP0 并核对哈希的同一 APK，仅重命名发行文件，没有重新构建或签名。**
  因此 App 内的 versionName 仍为 `2.3.0-tv-device-test`；已装同哈希 203026 的测试者无需重装。
- APK 大小 **70,148,559 字节**（约 66.9 MiB）。下载后可用发行附件 `SHA256SUMS.txt` 校验。
- Preview 2 的标签和附件保留不变；不要从上游手机版或 F-Droid 入口寻找这个 fork 的 TV 更新。

APK SHA-256：

```text
e26e329196a6e485683c5ce78118c5f9b3cb0e66f23b739ddaaa378d5202b690
```

## 本次改动

- **首页导航：**首列左键显式回侧栏，右键恢复内容焦点与滚动位置；补齐分类区域出口及详情返回恢复。
- **选集焦点：**当前遥控焦点独占粗描边，在播集用播放图标和文字表示；两行长标题按文字缩放增加行高，避免底部溢出。
- **搜索输入：**TV 使用明确的输入入口，OK 打开编辑和键盘；原生返回先收键盘，再关闭输入层并恢复入口焦点。
- **历史操作：**增加可聚焦的“更多”，提供详情、追番与删除确认；取消默认聚焦，删除后恢复到相邻条目或空状态。
  新输入会取消旧滚动 / 焦点请求，避免快速转向后旧请求抢焦点。
- **电视桌面：**同一 TV Activity 同时注册普通 LAUNCHER 与 LEANBACK_LAUNCHER，保留 TV banner、独立包名和横屏；
  小米“我的应用”已能发现 **Kazumi TV**，并从桌面用方向键 / OK 启动。手机版 manifest 未更改。

## 弹幕变更，请先看

**Preview 3 不包含 Preview 2 的合成演示弹幕，也未注入在线弹幕 AppID / Key。**
当前公开包不能作为在线弹幕端到端通过的证据；没有弹幕不代表服务故障。
演示文件不作为本版附件发布，本地录屏样片、测试入口和样例历史也未进入正常 App 入口。
仓库保留显式启用的测试工具；后续在线功能需使用获授权的完整凭证单独验证。

## 验证范围

- 发布前重新运行 **250 项 Flutter 测试，全部通过**；修改的 Dart 文件静态分析及差异格式检查通过。
  早期测试轮次曾出现 AsyncRateLimiter 计时偶发失败，未修改该限流器，不宣称消除此风险。
- **Google TV API 36 AVD：**开发构建验证搜索编辑 / 原生返回、模拟历史操作及焦点恢复，
  本地媒体实际播放器验证选集布局与导航。不是 203026 的完整 AVD 重测；在线检索超时仍有记录。
- **MiTV-ASTP0 / Android 9 / armeabi-v7a：**203026 从 203010 保留数据覆盖安装，安装后 APK 哈希一致；
  桌面发现与启动、内容回侧栏、历史首卡真实续播出画、浅色透明选集的焦点 / 在播区分、BACK 后 OK 唤醒均做了短程检查。
- 真机使用 ADB 注入键码，不等于实体遥控器全键 / 长按通过；没有全机型、所有来源、长期音画同步、4K/HDR 或内存稳定性结论。
- 构建日志报告 Built、产物可安装且已运行；外层 Flutter / PowerShell 调用返回 exit 1，未将其描述为零退出构建。
  本次不是 GitHub CI 重建的包。

详细证据：[203026 真机记录](https://github.com/znbsf/Kazumi/blob/v2.3.0-tv-preview.3/docs/TV_DEVICE_203026.md)、
[本地播放器集成](https://github.com/znbsf/Kazumi/blob/v2.3.0-tv-preview.3/docs/TV_PLAYER_LOCAL_INTEGRATION.md)、
[历史焦点回归](https://github.com/znbsf/Kazumi/blob/v2.3.0-tv-preview.3/docs/TV_FOCUS_REGRESSION.md)。

## 已知限制 / 下一步

1. **UI-13：暂停 seek 后退出可能丢失新进度。**已在本地实际播放器复现，尚未修复；正常续播成功不代表保存生命周期验收完成。
2. **UI-10：搜索连接失败会显示为空结果。**错误反馈和重试入口仍待改进。
3. **UI-12：首次启动仍有上游更新渠道文案。**本 fork 以当前 GitHub Releases 为下载入口。
4. 部分来源验证码兼容、实体遥控器特殊键 / 长返回、在线弹幕仍待验证或修复。
5. 侧栏文字、续播提示、播放器常用 / 更多操作、选源双栏等仍在
   [README UI 计划](https://github.com/znbsf/Kazumi/blob/v2.3.0-tv-preview.3/README.md#ui-plan)，未冒充本版功能。

反馈请到 [本 fork Issues](https://github.com/znbsf/Kazumi/issues)，附电视型号、系统、versionCode、按键步骤及预期 / 实际结果。
不要上传 Cookie、鉴权视频链接、API Key 或未脱敏日志。源码继续遵循 GPL-3.0，对应源码见本发行标签。
