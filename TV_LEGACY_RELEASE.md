# KazumiTV Legacy

Flutter TV 路线的归档测试版，对应 codex/upstream-tv-complete。后续主力开发转向独立 Kotlin / Compose / Media3 版本，见仓库默认分支README。

本版基于2.3.1电视适配代码，增加KazumiTV Legacy启动器名称以区别新原生应用，使用构建时注入的弹幕应用凭证。凭证不存入Git，但公开APK中的凭证可能被提取，不属于不可逆加密。编译可通过 --dart-define-from-file 指定仓库外JSON，键为DANDANAPI_APPID和DANDANAPI_KEY。

包名com.znbsf.kazumi.tv，与原生版com.znbsf.kazumi.compose.tv可并存，数据不自动互通。旧Flutter功能和限制沿用本分支实际实现，不保证所有规则可用。保留GPL许可及依赖声明。测试版延续本机已有开发签名，升级要求签名一致，不覆盖上游官方包。
