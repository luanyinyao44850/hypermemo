# 轻记 HyperMemo

面向 Android 14+（含小米澎湃 OS）的轻量备忘录。Kotlin、Jetpack Compose Material3、MVVM、StateFlow、Repository、Room。日程提醒只使用系统 Calendar Provider，不使用 Service、AlarmManager、精确闹钟权限或应用自身通知。

## 网页端 3 步生成 APK

1. **创建仓库并上传代码。** 登录 GitHub，新建仓库，勾选添加 README，确认默认分支是 `main`。解压项目 ZIP，通过 **Add file → Upload files** 把本目录内的 `app` 文件夹、`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties` 和文档上传到仓库根目录，保留子目录结构，然后提交到 `main`。不要上传 ZIP 本身，也不要把整个项目再套一层 `HyperMemo/`。公开仓库的标准 Actions runner 构建免费；私有仓库受账户免费额度限制。
2. **加入构建脚本并启动云端编译。** `.github` 是隐藏目录，网页拖拽可能漏传。若仓库中没有它，选择 **Add file → Create new file**，文件名填写 `.github/workflows/build_apk.yml`，复制交付的同名文件完整内容，提交到 `main`。仓库根目录必须同时能看到 `app/` 和 `settings.gradle.kts`。进入 **Actions → Build APK** 查看自动启动的任务；若提示启用 Actions，先启用，再点 **Run workflow → main**。
3. **下载并安装。** 等该次任务显示绿色成功，打开运行详情页下方 **Artifacts → HyperMemo-debug-运行编号**，下载并解压，得到 `app-debug.apk`。发送到手机，允许当前浏览器或文件管理器“安装未知应用”后安装。下载 Artifact 需要登录 GitHub。首次使用提醒时授权日历读写，并到系统日历确认事件和通知设置。

不需要安装 Android Studio、JDK、SDK 或 Gradle，也不需要 Git 命令。构建脚本安装固定版本 Gradle，所以此项目有意不包含 `gradlew` / `gradle-wrapper.jar`。CI 中执行的是 `gradle`，不存在缺少 Wrapper JAR 或执行权限的问题。

## 完整目录树（仓库根目录）

```text
.
├── .github/
│   └── workflows/
│       └── build_apk.yml
├── .gitignore
├── README.md
├── TESTING.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/hypermemo/
        │   │   ├── MainActivity.kt
        │   │   ├── MemoApplication.kt
        │   │   ├── calendar/
        │   │   │   ├── CalendarGateway.kt
        │   │   │   ├── CalendarHelper.kt
        │   │   │   └── ReminderPolicy.kt
        │   │   ├── data/
        │   │   │   ├── NoteDao.kt
        │   │   │   ├── NoteDatabase.kt
        │   │   │   ├── NoteEntity.kt
        │   │   │   └── NoteRepository.kt
        │   │   └── ui/
        │   │       ├── MemoApp.kt
        │   │       ├── MemoTheme.kt
        │   │       ├── NoteViewModel.kt
        │   │       └── ReminderPicker.kt
        │   └── res/
        │       ├── drawable/ic_memo.xml
        │       └── values/
        │           ├── strings.xml
        │           └── themes.xml
        └── test/java/com/example/hypermemo/
            ├── NoteRepositoryTest.kt
            └── ReminderPolicyTest.kt
```

构建后 `app/schemas/` 会自动生成 Room v1 schema，并随构建报告上传；将来升级数据库时应提交该 schema，增加数据库版本并编写 Migration，禁止用破坏性迁移清空用户笔记。

## 固定构建版本

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle / Android Gradle Plugin | 8.11.1 / 8.9.2 |
| Kotlin / Compose compiler plugin | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 34 |
| SDK Build Tools | 35.0.0 |
| Compose BOM | 2025.04.01 |
| Material3（由 BOM 管理） | 1.3.2 |
| Room | 2.7.1 |
| Lifecycle | 2.9.0 |
| Activity Compose / Core KTX | 1.10.1 / 1.16.0 |
| Coroutines | 1.10.2 |

为减少首次搭建的不确定性，使用固定的稳定版本组合，不自动追随最新版。`DatePickerDialog` 使用 Material3 组件；该 Material3 版本的时间弹窗按 Android 官方文档模式，用 Compose `Dialog + Surface + TimePicker/TimeInput` 封装为 `TimePickerDialog`，无需第三方日期库，也不是 Android View 版时间选择器。

## 代码职责与行为

- `NoteEntity` 含要求的 `id/title/content/createdAt/calendarEventId` 五个字段，另加 `reminderAt/reminderMinutes/calendarSyncPending/calendarKey`，记录离线提醒意图、同步状态及稳定关联标识。
- `NoteDao` 用 Flow 实时观察列表，按 `createdAt DESC, id DESC` 排序；更新保留原始创建时间。
- `NoteViewModel` 用 StateFlow 管理编辑状态及保存状态，使用协程调用 Repository，避免重复点击。ViewModel 保留旋转后的草稿；尚未保存的草稿不承诺在进程被系统终止后恢复。
- `MemoApp` 使用原生 `OutlinedTextField`，支持系统文字选择、复制粘贴及输入法；Material3 主题支持深色模式。退出脏编辑器和删除均有确认。
- `CalendarHelper` 检查并封装日历权限申请；实际 Launcher 注册在 Compose 的生命周期内，工具类仅持有 application Context。所有 Provider I/O 在 `Dispatchers.IO` 执行，并捕获权限撤销、账户不可用、Provider 失败等异常。
- 查询可见、至少具有 `CAL_ACCESS_CONTRIBUTOR` 权限、允许 `METHOD_ALERT` 且可容纳提醒的日历；按 `IS_PRIMARY DESC` 优先主日历，找不到主日历则选择其他有效日历。已有事件保持原日历，不擅自迁移账户。
- 日程标题和描述对应笔记标题和正文；日程持续 10 分钟。默认 0 分钟（准点），可选提前 5/10/15/30/60 分钟。实际提醒时刻 = 开始时间减去提前分钟数，新建或调整提醒时必须仍在未来。
- 事件和提醒用 `ContentResolver.applyBatch` 一起提交；更新替换该事件的提醒规则，避免叠加重复提醒；删除事件由 Calendar Provider 级联清理提醒。
- 保存先写 Room，再同步日历；失败时保留正文及 `calendarSyncPending`，界面明确显示“提醒待同步”。修复权限或账户后重新打开笔记并保存即可重试。没有后台自动重试，因此没有提醒权限也能使用普通备忘录。
- 删除先清理日历事件再删 Room。日历清理失败时保留笔记及关联 ID，防止留下难以追踪的提醒。已在日历中删除的事件会被容错处理；再次保存未来提醒可重新创建。
- 稳定 UUID 写入 `Events.CUSTOM_APP_URI`，配合包名验证事件归属和找回未回写的 ID；只有确认是本应用事件才会修改或删除。Room 与系统 Provider 无法组成单一事务；pending 标志与稳定 key 用于重试恢复。某些厂商或同步服务若抹除自定义字段，无法保证找回，应在真机验证。

## 澎湃 OS 的边界与隐私

Calendar Provider 是 Android 共享系统日历数据接口，**没有公开 API 可以保证指定“小米日历应用”或全设备唯一默认账户**。本项目写入系统 Provider 中可用的主日历；小米系统日历需要显示并使用该日历账户。存在多个主日历时，以 ID 排序选第一个。无可用账户时不会擅自创建伪造的本地账户，而是保存笔记并引导先打开系统日历启用/添加账户。

事件写入成功不等于厂商一定响铃。通知、声音、悬浮弹窗、锁屏通知、日历内部提醒设置及勿扰模式由系统日历和澎湃 OS 管理。请先用几分钟后的提醒验证，再用于重要事项。本应用不需要通知权限或后台自启动权限；发通知的是系统日历。系统版本升级后的兼容性仍需真机验证。

若选中的是云同步日历账户，标题和全文可能随该账户同步到云端。想留在本机，请在系统日历中配置可写的本地日历并检查实际写入位置。应用本身不声明 INTERNET 权限。

数据库关闭 Android 备份，避免将本机日历 ID 恢复到其他设备造成错误关联。清除应用数据、卸载应用不会自动删除系统日历事件；需要先在应用里删除关联笔记，或在系统日历手动清理。

## APK、签名与 Actions

产物是自动签名、可直接安装的 **debug APK**，不是 unsigned release。工作流仅授予 `contents: read`，推送 `main` 自动构建，也可手动运行；执行单元测试、Android Lint、APK 打包，失败即停止。APK 与测试报告保留 7 天，过期后可重新运行。

调试签名通过 Actions cache 尽量复用，便于覆盖安装；**缓存被清理/过期或换仓库后，签名可能变化**。这时新 APK 无法覆盖旧应用，不要直接卸载导致重要笔记丢失。长期自用或正式分发应另配固定的 release keystore，以 GitHub Secrets 保存并配置 release 签名，不能把正式私钥放入公开缓存。本项目无需配置 Secrets 即可首次构建。

构建流程直接调用固定版本的 Gradle，不需要 Wrapper。若自行在其他环境执行，命令为：

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 常见问题

- **Actions 没有触发**：确认脚本在根目录 `.github/workflows/build_apk.yml`，分支是 `main`，Actions 未禁用。不要把脚本上传成 `.txt`。
- **提示找不到 settings 或 app 模块**：上传时多套了一层目录，将 `app/` 和 Gradle 文件移到仓库根目录。
- **依赖下载失败**：查看失败日志；Google Maven、Maven Central 或 runner 网络临时错误可点 Re-run failed jobs。
- **找不到可写日历**：先打开澎湃 OS 系统日历并初始化账户，确认日历可见、可写，支持弹窗提醒，再保存重试。
- **无法删除未同步笔记**：如果可能已写入日历，必须先恢复权限清理关联。不会冒险仅删除本地记录留下提醒。
- **修改失败但仍收到旧提醒**：待同步只表示新意图尚未生效，旧事件可能仍然存在。恢复权限后保存，或在系统日历手动处理。

## 验证与参考

交付时完成目录、XML/YAML 和源码静态检查；当前环境没有可用 Android 构建工具链，未实际运行 Gradle、Actions 或澎湃 OS 真机测试。测试源码及自动化检查已放入项目，首个 Actions 绿色构建才代表编译与这些检查通过。真机验收见 `TESTING.md`。

- [AGP 8.9 的 Gradle/JDK/API 兼容要求](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Compose April 2025 BOM](https://android-developers.googleblog.com/2025/04/whats-new-in-jetpack-compose-april-25.html)
- [KSP 对应版本](https://github.com/google/ksp/releases/tag/2.1.20-1.0.32)
- [Room 发布说明](https://developer.android.com/jetpack/androidx/releases/room)
- [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider)
- [主日历与提醒能力字段](https://developer.android.com/reference/android/provider/CalendarContract.CalendarColumns)
- [Compose 时间弹窗](https://developer.android.com/develop/ui/compose/components/time-pickers-dialogs)
- [GitHub 标准 runner 计费说明](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [下载 Actions 产物](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)
