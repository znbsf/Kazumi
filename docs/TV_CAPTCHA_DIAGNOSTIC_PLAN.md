# TV 验证码：来源对照与诊断待办

2026-09-05。由并行用户任务交接，纳入 TV 阶段一；**尚未修复或验收**，不属于详情页布局测试包的完成声明。

## 已有证据

交接目标为 MiTV-ASTP0 / mulan、Android 9、系统 WebView 66.0.3359.158，安装包为 203004。
本任务仅复核本地交接文件，未在实体电视安装新包、切换来源或提交验证码。

| 来源 | 观察到的故障 | 不能据此得出的结论 |
| --- | --- | --- |
| mutefun | 17:32:26 `ERR_CONNECTION_REFUSED`，图片持续加载；回调没有失败 URL。交接的网页由输入框 focus 触发动态插入验证码图片 | 不能确定是图片、依赖脚本还是其他请求失败；桌面图片接口返回 200 不等于电视成功 |
| dalvdm | 交接截图显示图片、输入框和键盘。17:40:28 与 17:40:57 均出现 `Input element not found` 和 `Button element not found` | 没有证据证明用户输入错误；须核对实际安装规则与页面/frame，图片出现不等于提交成功 |
| mgnacg | 17:43:46 `Input filled` / `Button clicked`，随后导航；约 8 秒后走取消收尾，再检索显示 `no results` | 成功检测遗漏是候选原因，不能把无结果或 Cookie 已保存当作服务端验证通过 |

本地证据在未提交的 `artifacts/captcha-debug-20260905-172833/`。
其中原始日志含会话 Cookie，**不得提交、复制到公开 issue 或发布包**。
本任务确认 PID 13516 是交接的本机 adb logcat 子进程后结束了该采集；没有停止 adb server 或改变电视状态。

截至本轮读取的上游规则使用位置相关 XPath：
[dalvdm](https://raw.githubusercontent.com/Predidit/KazumiRules/main/dalvdm.json)、
[mutefun](https://raw.githubusercontent.com/Predidit/KazumiRules/main/mutefun.json)、
[mgnacg](https://raw.githubusercontent.com/Predidit/KazumiRules/main/mgnacg.json)。
这不证明与电视已安装规则完全相同。桌面读取 dalvdm 页面被 Cloudflare 拒绝，未获得可用于修正规则的当前 DOM；不绕过站点拦截，也不猜测选择器。

## 待实现与验证

- [ ] 删除验证码流程中 Cookie 明文与完整图片 base64 日志；禁止记录答案、鉴权参数。旧本地日志不自动删除，避免丢失诊断证据。
- [ ] `onReceivedError` 记录脱敏 URL、主框架标志、错误类型/描述；补 HTTP 错误、脱敏 JS console 错误，区分主文档、脚本和图片请求。
- [ ] 记录图片/Input/Button XPath 命中、frame/readyState、脱敏 src、complete/naturalWidth、load/error、输入框 focus 是否成功；不记录输入框内容。
- [ ] 加载失败或超时结束无限转圈，给出原因、取消与重试；找不到输入/按钮时直接报告选择器失败，不继续假装提交并等待 8 秒。
- [ ] 检查旧 WebView 的注入时机：脚本到达时 DOMContentLoaded 是否已发生；导航后重新检测与验证收尾的顺序。不能只靠发生导航就判定验证通过。
- [ ] 用本地合成页面测试延迟插图、图片失败、错误 XPath、iframe、提交后页面导航、错误答案仍有验证码、超时/重试及取消竞争；测试页不进入发布代码。
- [ ] 根据带失败 URL 和 DOM 状态的诊断证据，再做对应规则/页面交互修复。真实验证码由用户输入，不读取图片求解，不自动提交猜测答案。
- [ ] 按用户后续设备安排验收；当前仅允许 TV 模拟器部署，不因交接扩大到实体电视。
