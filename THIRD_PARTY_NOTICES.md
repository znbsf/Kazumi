# 来源、依赖与资源

KazumiTV保留Kazumi迁移工作的GPL-3.0许可与来源署名。上游项目：https://github.com/Predidit/Kazumi 。本分支不代表上游官方TV版本。

当前原生APK主要运行时组件：

| 组件 | 许可 | 来源 |
| --- | --- | --- |
| Kotlin / kotlinx.coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin / https://github.com/Kotlin/kotlinx.coroutines |
| AndroidX Activity / Lifecycle / Compose / TV / Media3 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Coil | Apache-2.0 | https://github.com/coil-kt/coil |
| OkHttp / Okio | Apache-2.0 | https://github.com/square/okhttp / https://github.com/square/okio |
| jsoup | MIT | https://github.com/jhy/jsoup |

完整Apache-2.0与jsoup MIT文本位于LICENSES目录，项目GPL全文位于根目录LICENSE。准确版本见app/build.gradle.kts及Gradle依赖图。此表为主要依赖索引，不能替代各依赖自带的完整许可/NOTICE；发行前持续核对传递依赖。Junit与测试JSON仅用于本地/仪器测试，不作为应用功能发布。

应用标志及电视横幅为本项目独立矢量设计；使用设备系统字体。海报为运行时在线内容，不打包到应用；README截图用于说明应用界面，不声明拥有节目海报版权。自制多轨道测试视频及派生HLS/DASH的生成说明见app/src/androidTest/assets/offline-fixtures.md。

旧Flutter版依赖和资源以对应发布标签下的原工程、许可文件与Flutter许可登记为准，不与原生APK依赖混为一谈。
