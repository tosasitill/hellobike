# 轻骑（RideCompact）

一个独立的 Android 普通单车联网控制原型。源码、构建和离线测试已完成；**尚未在真机或实体车辆上验证，不能当作已验证可替代官方 App 的成品。**

## 安装文件

- 安装包：`app/build/outputs/apk/release/app-release.apk`
- 归档包：`releases/<版本>/ride-compact-<版本>.apk`（二进制归档不入库，需要分发时挂到 GitHub Releases）
- 包名：`dev.local.ridecompact`，与官方 App 可共存，不覆盖官方 App。
- 系统要求：Android 9（API 28）以上。
- 签名：本机构建调试密钥签名的 release 变体，`debuggable=false`，不是商店发行签名。
- 账号导入文件：`private/account.json`，已由本次 HAR 提取，权限为 `0600`；`private/` 已被 Git 忽略，不会进入仓库。
- 也可以在 App 内直接选择原 HAR，账号解析只在设备本地进行。

账号文件不是 APK 资源，不会随安装包打包。导入后通过 Android Keystore + AES-GCM 存储，会话明文只在请求构建时存在于进程内存。现在支持手机号验证码登录；未实现自动刷新登录会话。

## 操作顺序

1. 安装 APK，打开“轻骑”。默认不连接业务服务器。
2. 选择“导入账号 / HAR”，导入 `private/account.json` 或本次原 HAR。不要把账号文件当作普通项目文件分享。
3. 登录页的“城市编码 / 行政区编码”会在进入页面时按当前位置自动填写（GCJ02 坐标反查官方 LBS `hello.lbs.code.regeo`）；定位不可用或权限被拒时仍可手动输入。跨城市、跨行政区使用尚未覆盖。
4. 打开“连接真实账号”，确认后授予精确定位权限。此开关每次重新创建页面默认关闭。
5. 先“刷新订单状态”。已存在订单时，以服务端状态为准。
6. 扫描车辆二维码或手输车号，点“预校验 / 计费规则”。显示服务端返回的规则后，再点“确认开锁”。
7. 开锁提交后只轮询状态，不因超时自动再次创建订单。
8. 还车先校验位置，再弹出“确认还车”。确认后只提交一次关锁操作，再查订单和结束页。
9. 未知状态、联网失败、额外认证、停车异常或 BLE 要求，请点“打开官方 App”处理。不要重复提交开关锁。

没有在开发过程中使用账号向业务接口发出任何请求。导入成功不代表令牌仍然有效，也不代表服务端接受这个第三方客户端。

## 已实现

- ZXing 相机二维码识别与手动车号输入；扫码页改为自定义竖屏、连续对焦和手电筒。
- 官方 `c3x.me` 域名与 `n` 查询参数的本地解析子集；拒绝重复参数、其他主机、混合车型参数及非数字车号。
- HAR / 账号 JSON 导入、账号字段白名单、Keystore 加密、关闭备份和设备迁移。
- 手机号验证码登录：`user.account.sendCodeV3`、官方滑块 `captchaType=1`、`user.account.login` 认证网关。
- 本地 Keystore 失效时支持重新导入或重新登录并生成新密钥，不再反复解密旧密文。
- `user.tw.ride.check` 返回 `code=301` 时按“无骑行中订单”处理，不再阻断预校验。
- 确认开锁采用官方确认阶段的 `force=1`、`rideLicenseforce=1`。
- 当前设备定位，拒绝超过 30 秒、误差超过 80 米或 mock-provider 的位置。
- 登录页按当前位置自动填写城市编码与行政区编码：请求 `hello.lbs.code.regeo`，校验返回的 3-6 位城市编码与 6 位行政区编码，失败时回落到手动输入并保留上次成功值。
- WGS84 到 GCJ02 的常见近似转换，包含离线参考值测试。尚未与本设备定位 provider 或高德 SDK 做实测比较。
- `user.ride.pre.ride`、开锁页价格读取、`user.ride.create`、骑行查询、`ride.hub.pre.close`、结束页读取。
- 按订单 ID 校验状态，不将“请求成功受理”当作开锁成功。
- 预校验有效期 60 秒、还车位置确认有效期 30 秒、操作时禁用重复点击。
- 持久化待确认操作，重启后先确认服务器状态；CLOSING 状态不因暂时仍显示骑行中而重新开放关锁按钮。
- UI 不显示完整用户号、令牌、订单号或蓝牙数据；不记录请求体。

## 官方代码依据

上一个分析目录 `../hellobike-flow/jadx-src/sources/`：

- `com/hellobike/platform/scan/kernal/code/CodeAnalysisKt.java`：URI 主机匹配，然后读取已登记查询参数。
- `com/hellobike/evehicle/scanservice/model/entity/ScanParam.java`：`SCAN_DOMAIN_NAME_1 = c3x.me`。
- `com/hellobike/platform/scan/kernal/code/SupportCodeTypeHolder.java`：登记 n/m/e/x/u/s/c/p。
- `com/alipay/alipaysecuritysdk/common/model/DynamicModel.java:20`：被 JADX 替换为常量引用的参数实际为 `n`，不是 `d`。
- `com/hellobike/bike/core/scan/scanservice/BikeScanExecute.java:14`：普通单车侧登记 `n`、`u`，扩展还有 `e`。本原型只接受 `n`，不声称覆盖全部官方二维码。
- `com/hellobike/userbundle/business/login/swipecaptcha/SwipeCaptchaManager.java`：短信发送、滑块图片和偏移提交。
- `com/hellobike/userbundle/business/login/presenter/VerificationCodePresenterImpl.java`：验证码登录 action 为 `user.account.login`。
- `com/hellobike/gateway/enviroment/FinallyApiUrlKt.java`：认证网关为 `https://api.hellobike.com/auth`，平台短信接口为 `https://api.hellobike.com/api`。
- `com/hellobike/bike/business/openlock/ridecreate/BikeRideCreateAction.java`：确认开锁时 `force=1`、`rideLicenseforce=1`。
- `com/hellobike/bike/business/riding/rideinfo/model/api/ManhattanP10PreCloseApiModel.java`：`operateType=2` 是确认还车。
- `com/hellobike/rm/ride_business_operatelock/networkoperate/datatransform/RideNetworkActionTransformMap.java`：普通单车网络开锁与关锁 action 对应。

重新实现的是观察到的接口和扫码规则，并没有将官方闭源类、品牌素材、原生签名库或 BLE 指令复制进新 APK。官方流程包含网络动态配置和 H5，单份 HAR 不能恢复全部逻辑。

## 与官方流程的差异及限制

- 只实现网络控制，没有 BLE GATT、蓝牙指令生成/写入、BLE 上报和离线开锁。忽略且不保存响应中的蓝牙材料。
- 没有复刻 WebSocket 订阅和原生签名/风控逻辑，以串行状态查询确认成功；约 1.8 秒一次，单次等待窗口约 20 秒。网络超时可能使总墙钟时间更长。
- 原生 `bike.main.getInfo` 的加密 body 未复刻；扫码后以普通单车预校验和基础信息确认车型。
- `force`、`rideLicenseforce` 在确认开锁阶段跟随官方代码使用 1；预校验阶段仍为 0。
- 官方短信登录可能要求滑块或其他身份复核；原型支持普通滑块图片/偏移流程，复杂风控仍会停止并提示。
- 自定义 User-Agent，无官方设备指纹、请求签名或会话环境伪装。服务端拒绝时停止，不做绕过。
- 还车只接受本次确认过的 `status=3,causeType=1103,penaltyFree=1,skipConfirm=true` 路径；罚款、额外确认、调度费等未覆盖。
- 系统位置 provider 的坐标口径与行政区匹配只在模拟器上取样核对过，车锁联网能力仍需实车验证。不会复用 HAR 的旧经纬度。
- 反查接口的 `signature` 字段使用固定占位值（探测显示服务端不校验其内容）。若服务端开始校验，该调用会失败并自动回落到手动输入，不影响登录本身。
- 不包含支付、骑行卡、实名认证、验证码、异常订单处理和重新登录。
- 进程被杀、超时或服务端返回未知结构时，本地可能停在待确认状态。这是为防止重复操作，需在官方 App 确认处理；没有“强制成功”按钮。
- 已在 Pixel 6a 模拟器（Android 17）验证 UI 启动与排版；相机、定位自动填写的真机复核、Keystore 与车辆端到端测试仍未完成。

## 本地构建与测试

工具链存放在本项目 `toolchain/`（已被 Git 忽略），没有修改原 APK。当前使用 Gradle 9.3.1、AGP 9.1.0、JDK 17、compileSdk 37 与 buildToolsVersion 36.0.0；`ANDROID_HOME` 需自备 `platforms/android-37`。

仓库不包含 Gradle wrapper 和 `toolchain/`；克隆后请自备 JDK 17 与 Gradle 9.3.1，并通过 `ANDROID_HOME` 或 `local.properties` 指向本地 Android SDK。

```bash
bash work/ride-compact/build.sh
```

该命令生成 release APK、运行 41 项离线/仿真测试以及 Lint。测试读取父工作目录的原 HAR 验证账号提取，但不打印任何令牌。

```bash
node work/ride-compact/export-account.cjs
```

以上命令可重新生成本地账号导入文件。

验证报告：

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/test-results/testDebugUnitTest/TEST-dev.local.ridecompact.CoreTest.xml`

测试覆盖二维码白名单/重复参数、预校验超时、301 空订单、密钥失效恢复、账号缓存、验证码接口字段、短信滑块分支、登录响应映射、不可重复创建、关锁中不可重发、未知状态阻断、竖屏扫码 Activity/手电筒控件、坐标参考值、定位反查载荷与城市/行政区编码解析、真实 HAR 离线导入。未在真实车辆上进行在线开关锁验证。

已知问题：在 JDK 17 下运行 `:app:testDebugUnitTest` 会有 1 项失败（`AndroidRegressionTest.mainAndLoginActivitiesStartWithoutAccountsOrNetwork`），原因是 Miuix 0.9.0 发布的是 Java 21 字节码，JVM 侧抛 `UnsupportedClassVersionError`。其余 40 项通过；改用 JDK 21 运行测试任务即可全部通过。

## 当前交付状态

- APK 构建成功，41 项测试中 40 项通过（见已知问题），Lint 无 error（存在目标 SDK、依赖更新、中文字符串本地化等 warning）。
- APK v2 签名验证通过。
- 已扫描 APK DEX/assets，未发现本次账号 token、ticket 或 userGuid。
- 尚未满足实体车辆干净基线端到端验证，因此本次交付明确是可安装的开发原型，而非保证可用的官方替代客户端。
