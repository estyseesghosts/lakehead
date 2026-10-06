# Optional Shizuku Integration for a Kotlin/Compose Android App

## 1. Goal

Shizuku should be an **optional capability layer**.

The application must:

- work normally without Shizuku installed;
- work normally when Shizuku is installed but stopped;
- work normally when Shizuku permission is denied;
- expose additional functions when Shizuku becomes available;
- immediately fall back when the Shizuku Binder dies;
- never request Shizuku permission during normal startup.

The important distinction is:

```text
Application
    |
    +-- normal Android implementation ---- always available
    |
    +-- privileged implementation
            |
            +-- Shizuku available? -> enhanced functions
            |
            +-- otherwise -> unavailable/fallback
```

Shizuku is therefore not an application requirement. It is an optional backend.

---

# 2. What Shizuku provides

Normal Android applications communicate with system services through Binder using their application's UID and permissions.

Shizuku runs a separate service with either:

- ADB/shell identity, normally UID `2000`; or
- root identity, UID `0`.

It can proxy Binder calls or run an application's own `UserService` using this more privileged identity. ADB and root do **not** provide identical permissions. Android permissions and SELinux restrictions still apply.

Conceptually:

```text
Normal:

App
  -> Android Binder
       -> system service
            sees: application UID


With Shizuku:

App
  -> Shizuku Binder
       -> Shizuku service
            identity: shell/root
                -> Android system service
```

This does not make the main application process root.

---

# 3. Dependency

The latest published Maven Central API remains `13.1.5`.

```kotlin
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
```

The `api` dependency contains the application API.

The `provider` dependency supplies Shizuku's provider-side integration.

Including these libraries does **not** mean that the user must have Shizuku installed or running.

---

# 4. Manifest integration

The documented provider is:

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

The permission on the provider protects it from ordinary application access.

Do not add privileged Android permissions to your application manifest simply because Shizuku can use them. The normal application still does not hold those permissions.

---

# 5. Recommended architecture

Do not spread calls such as:

```kotlin
Shizuku.checkSelfPermission()
Shizuku.getUid()
Shizuku.bindUserService(...)
```

through UI code.

Create a boundary.

For example:

```text
app/
feature/
privilege/
    PrivilegeState.kt
    PrivilegedCapabilities.kt
    PrivilegedOperations.kt

privilege/shizuku/
    ShizukuController.kt
    ShizukuOperations.kt
    ShizukuUserService.kt
```

For a larger application, this can become separate Gradle modules:

```text
:privilege-api
:privilege-shizuku
```

`:privilege-api` contains no Shizuku dependency.

`:privilege-shizuku` contains the optional implementation.

This keeps the rest of the application independent from Shizuku.

---

# 6. Model availability as state

Do not use one Boolean such as:

```kotlin
hasShizuku = true
```

There are several materially different states.

```kotlin
sealed interface PrivilegeState {

    data object Unavailable : PrivilegeState

    data object PermissionRequired : PrivilegeState

    data object PermissionDenied : PrivilegeState

    data class Ready(
        val backend: Backend
    ) : PrivilegeState

    data class Error(
        val cause: Throwable
    ) : PrivilegeState
}

enum class Backend {
    ADB,
    ROOT
}
```

A more detailed product may distinguish:

```text
Unavailable
InstalledButStopped
PermissionRequired
PermissionDenied
ReadyADB
ReadyRoot
Error
```

But Shizuku availability should ultimately be based on its Binder, not only on whether the Shizuku APK appears installed. Sui can also provide the API. Shizuku explicitly provides Binder-received and Binder-death listeners for this purpose.

---

# 7. Application-scoped Shizuku controller

The Shizuku Binder can appear and disappear while the application is running.

A controller should convert these callbacks into a `StateFlow`.

```kotlin
class ShizukuController {

    private val _state =
        MutableStateFlow<PrivilegeState>(PrivilegeState.Unavailable)

    val state: StateFlow<PrivilegeState> =
        _state.asStateFlow()

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            refresh()
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            _state.value = PrivilegeState.Unavailable
        }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) {
                refresh()
            } else {
                _state.value = PrivilegeState.PermissionDenied
            }
        }

    init {
        Shizuku.addBinderReceivedListenerSticky(
            binderReceivedListener
        )

        Shizuku.addBinderDeadListener(
            binderDeadListener
        )

        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )
    }

    private fun refresh() {
        if (!Shizuku.pingBinder()) {
            _state.value = PrivilegeState.Unavailable
            return
        }

        if (
            Shizuku.checkSelfPermission() !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = PrivilegeState.PermissionRequired
            return
        }

        _state.value = PrivilegeState.Ready(
            backend =
                if (Shizuku.getUid() == 0)
                    Backend.ROOT
                else
                    Backend.ADB
        )
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) return

        if (
            Shizuku.checkSelfPermission() ==
            PackageManager.PERMISSION_GRANTED
        ) {
            refresh()
            return
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            _state.value = PrivilegeState.PermissionDenied
            return
        }

        Shizuku.requestPermission(PERMISSION_REQUEST)
    }

    companion object {
        private const val PERMISSION_REQUEST = 1001
    }
}
```

Shizuku's API specifically states that its methods should be used only after its Binder has been received; otherwise methods can throw `IllegalStateException`. The sticky Binder listener handles the case where the Binder arrived before the controller registered its listener.

---

# 8. Do not request access automatically

The application should **not** do this:

```text
App launches
    ↓
SHIZUKU PERMISSION DIALOG
```

Shizuku is an enhancement, so permission should be requested after an explicit user action.

For example:

```text
Settings
 └── Enhanced system integration
       Shizuku
       [Enable]
```

Or when the user first attempts a Shizuku-only operation:

```text
Freeze application
Requires enhanced system access.

[Enable Shizuku integration]
```

The normal application experience should never be blocked by this.

---

# 9. Compose integration

Compose should observe ordinary application state. It should not interact directly with Binder.

For example:

```kotlin
class SettingsViewModel(
    private val shizuku: ShizukuController
) : ViewModel() {

    val privilegeState =
        shizuku.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PrivilegeState.Unavailable
        )

    fun enableShizuku() {
        shizuku.requestPermission()
    }
}
```

Then:

```kotlin
@Composable
fun EnhancedAccessSetting(
    viewModel: SettingsViewModel
) {
    val state by viewModel.privilegeState
        .collectAsStateWithLifecycle()

    when (state) {
        PrivilegeState.Unavailable -> {
            EnhancedAccessDisabled()
        }

        PrivilegeState.PermissionRequired -> {
            EnableEnhancedAccessButton(
                onClick = viewModel::enableShizuku
            )
        }

        PrivilegeState.PermissionDenied -> {
            PermissionDeniedMessage()
        }

        is PrivilegeState.Ready -> {
            EnhancedAccessEnabled(
                backend = state.backend
            )
        }

        is PrivilegeState.Error -> {
            EnhancedAccessError()
        }
    }
}
```

Compose remains a consumer of state rather than becoming part of the privilege implementation.

---

# 10. Separate availability from individual capabilities

`Shizuku is running` does not mean:

```text
every privileged operation will work
```

ADB/shell permissions differ between Android versions and OEM builds. Root-backed Shizuku has substantially more authority. Shizuku provides `checkRemotePermission()` for checking permissions available to its remote identity.

Therefore define actual capabilities.

For example:

```kotlin
enum class PrivilegedCapability {
    PACKAGE_CONTROL,
    APP_OPS,
    CROSS_USER_PACKAGE_QUERY,
    SECURE_SETTINGS
}
```

Then:

```kotlin
interface PrivilegedCapabilities {

    suspend fun supports(
        capability: PrivilegedCapability
    ): Boolean
}
```

Your UI can consequently represent:

```text
Shizuku: enabled

Package control: available
Cross-user access: available
Secure settings: unavailable
```

rather than assuming everything works.

---

# 11. Keep normal and privileged implementations separate

Suppose the application normally performs:

```kotlin
interface PackageOperations {
    suspend fun getPackages(): List<AppInfo>
}
```

You may have:

```text
StandardPackageOperations
ShizukuPackageOperations
```

Then select the appropriate implementation:

```kotlin
class PackageOperationsRouter(
    private val standard: StandardPackageOperations,
    private val shizuku: ShizukuPackageOperations,
    private val controller: ShizukuController
) {

    suspend fun getPackages(): List<AppInfo> {
        return if (
            controller.state.value is PrivilegeState.Ready
        ) {
            shizuku.getPackages()
        } else {
            standard.getPackages()
        }
    }
}
```

For functions that have no normal equivalent:

```kotlin
sealed interface OperationAvailability {
    data object Available : OperationAvailability
    data class Unavailable(
        val reason: String
    ) : OperationAvailability
}
```

Do not make the entire repository dependent on Shizuku simply because one operation benefits from it.

---

# 12. Three ways to use Shizuku

Shizuku provides several implementation approaches.

## A. Normal Android API

Prefer this whenever possible.

```text
Application
    ↓
public Android API
```

No Shizuku.

---

## B. Remote Binder call

Shizuku provides `ShizukuBinderWrapper`.

This forwards calls to Android system services using Shizuku's identity. The Shizuku documentation recommends the wrapper rather than manually implementing remote Binder transactions.

Conceptually:

```text
App
 ↓
IPackageManager
 ↓
ShizukuBinderWrapper
 ↓
Shizuku
 ↓
PackageManagerService
```

This is suitable for relatively simple Binder operations.

The documentation describes direct remote Binder calls as the simpler approach, but notes that it is limited to Binder operations.

---

## C. Shizuku UserService

For more complicated privileged functionality, use a `UserService`.

```text
normal application process
        |
        | AIDL
        ↓
Shizuku UserService
        |
        | running as shell/root
        ↓
Android/Linux operations
```

Shizuku describes UserService as similar to an Android bound service, except that it runs under Shizuku's shell/root identity.

This is generally the better architecture when:

- several privileged calls belong together;
- privileged logic has significant complexity;
- JNI/native code is required;
- you need to isolate privileged implementation;
- you would otherwise execute many shell commands.

---

# 13. UserService architecture

Define an AIDL interface.

```text
app/src/main/aidl/com/example/privilege/IPrivilegeService.aidl
```

For example:

```java
interface IPrivilegeService {
    boolean setPackageEnabled(
        String packageName,
        int userId,
        boolean enabled
    );

    int getCapabilityFlags();
}
```

Implementation:

```kotlin
class PrivilegeUserService :
    IPrivilegeService.Stub() {

    override fun setPackageEnabled(
        packageName: String,
        userId: Int,
        enabled: Boolean
    ): Boolean {
        // privileged implementation
        return true
    }

    override fun getCapabilityFlags(): Int {
        return 0
    }
}
```

Bind through:

```kotlin
val args =
    Shizuku.UserServiceArgs(
        ComponentName(
            context,
            PrivilegeUserService::class.java
        )
    )
        .daemon(false)
        .processNameSuffix("privileged")
        .tag("privilege-service")
        .version(1)

Shizuku.bindUserService(
    args,
    serviceConnection
)
```

Setting a stable `tag` is useful because Shizuku notes that relying on the class name is unsafe after R8/ProGuard renaming. The `version` can be changed when an incompatible service implementation is deployed.

---

# 14. Do not build a generic shell executor

Avoid an API such as:

```kotlin
interface PrivilegedService {
    fun run(command: String): String
}
```

That makes every application component capable of executing arbitrary shell commands.

Prefer:

```kotlin
interface PrivilegedPackageService {

    suspend fun enablePackage(
        packageName: String,
        userId: Int
    ): Result<Unit>

    suspend fun disablePackage(
        packageName: String,
        userId: Int
    ): Result<Unit>
}
```

This gives you:

- known operations;
- controlled parameters;
- easier validation;
- easier tests;
- smaller security surface;
- less command injection risk;
- easier migration away from shell commands.

Privileged execution should be treated as an internal implementation detail.

---

# 15. Binder death is normal

Do not treat Shizuku disappearance as an exceptional application crash.

It can stop because:

- the device rebooted;
- the user stopped Shizuku;
- wireless debugging stopped;
- Android killed/restarted something;
- Shizuku was updated;
- the Binder died.

The API provides `addBinderDeadListener()` specifically for this case.

Your state transition should be:

```text
ReadyADB
   |
   | binder death
   ↓
Unavailable
```

Any active privileged service reference must also be discarded.

The application continues using its normal functionality.

---

# 16. Do not cache permission as persistent configuration

Do not store:

```kotlin
preferences["shizuku_enabled"] = true
```

and use that as proof of current access.

The authoritative state is the current Binder and current permission.

You can separately store:

```text
user wants Shizuku integration = true
```

but that is only a preference.

Runtime authorization still needs to be re-evaluated.

---

# 17. ADB versus root

Determine the backend after permission has been granted:

```kotlin
val backend =
    when (Shizuku.getUid()) {
        0 -> Backend.ROOT
        2000 -> Backend.ADB
        else -> Backend.UNKNOWN
    }
```

The official API documents UID `0` for root and UID `2000` for ADB/shell.

Do not implement:

```text
Shizuku ready = root
```

They are different modes.

Instead:

```text
Shizuku
 ├── ADB backend
 │     └── shell-level capabilities
 │
 └── root/Sui backend
       └── root-level capabilities
```

Individual operations should still check what they need.

---

# 18. Hidden Android APIs

Some Shizuku integrations call hidden Android framework interfaces such as internal package-manager Binder APIs.

Android 9+ restricts hidden API access inside normal application processes. Shizuku's documentation specifically warns about this.

A `UserService` does not have the same non-SDK API restrictions, although its process is also **not a normal Android application process**. Consequently some ordinary `Context` functionality does not work there.

This gives another reason to isolate complicated framework interaction behind a UserService rather than filling the normal app process with reflection and hidden API access.

---

# 19. Suggested dependency flow

For a clean architecture:

```text
Compose UI
    ↓
ViewModel
    ↓
Domain use-case
    ↓
Capability-aware repository
    ↓
┌──────────────────────┬──────────────────────┐
│ Standard backend     │ Shizuku backend      │
│ Android SDK          │ Binder/UserService   │
└──────────────────────┴──────────────────────┘
```

The dependency direction should be:

```text
UI
 ↓
domain interface
 ↑
 ├── standard implementation
 └── Shizuku implementation
```

The domain layer should not know that a class named `Shizuku` exists.

---

# 20. Example feature routing

Suppose there is an enhanced operation:

```kotlin
interface AppControl {

    suspend fun launch(
        packageName: String
    )

    suspend fun privilegedAction(
        packageName: String
    ): PrivilegedResult
}
```

Normal launch:

```kotlin
override suspend fun launch(
    packageName: String
) {
    normalLauncher.launch(packageName)
}
```

Enhanced action:

```kotlin
override suspend fun privilegedAction(
    packageName: String
): PrivilegedResult {

    if (
        shizuku.state.value !is
        PrivilegeState.Ready
    ) {
        return PrivilegedResult.Unavailable
    }

    return shizukuOperations.perform(
        packageName
    )
}
```

This is the key design principle:

```text
Shizuku extends capabilities.
Shizuku does not replace the application.
```

---

# 21. UI behaviour

A Shizuku-capable feature can use three patterns.

### Enhancement

Normal function exists:

```text
Launch app
```

With Shizuku:

```text
Launch app
Launch with enhanced handling
```

### Conditional action

Function exists only with Shizuku:

```text
Advanced controls
    Freeze app
    Change privileged setting
```

Without Shizuku:

```text
Advanced controls
    Enable enhanced system access
```

### Transparent acceleration

If Shizuku only improves implementation rather than changing semantics:

```text
if Shizuku usable:
    privileged implementation
else:
    standard implementation
```

The user does not need to see the distinction.

---

# 22. Failure handling

Every privileged operation should expect:

```kotlin
SecurityException
IllegalStateException
DeadObjectException
RemoteException
unsupported Android/OEM behaviour
```

Translate these into domain-level errors.

For example:

```kotlin
sealed interface PrivilegedResult {

    data object Success : PrivilegedResult

    data object ShizukuUnavailable :
        PrivilegedResult

    data object PermissionDenied :
        PrivilegedResult

    data object Unsupported :
        PrivilegedResult

    data class Failed(
        val cause: Throwable
    ) : PrivilegedResult
}
```

Do not leak Binder implementation errors into Compose.

---

# 23. Testing matrix

At minimum test:

| Situation | Expected result |
|---|---|
| Shizuku never installed | app works normally |
| Shizuku installed, stopped | app works normally |
| Shizuku running, no permission | optional enable action appears |
| permission denied | app continues normally |
| permission granted | enhanced capabilities appear |
| ADB backend | only supported ADB functions available |
| root backend | root capabilities detected separately |
| Shizuku stopped while app open | state returns to unavailable |
| Shizuku restarted | Binder reconnects |
| process recreation | capability state reconstructed |
| device reboot | no stale ready state |
| unsupported OEM operation | clean unsupported/failure result |

Also test actual operations on multiple Android versions. Shell permissions can change between Android releases.

---

# 24. Recommended implementation sequence

1. Add the Shizuku API/provider dependencies.
2. Add or verify `ShizukuProvider`.
3. Create `PrivilegeState`.
4. Implement one application-scoped `ShizukuController`.
5. Convert Binder/permission events into `StateFlow`.
6. Add the domain capability interfaces.
7. Keep existing Android implementations unchanged.
8. Add Shizuku implementations behind those interfaces.
9. Add explicit permission UI.
10. Add capability checks for individual privileged operations.
11. Add Binder-death handling.
12. Add one small privileged feature first.
13. Test all non-Shizuku paths.
14. Only then add additional Shizuku functions.

---

# 25. Recommended final structure

```text
app/
    MainActivity.kt
    App.kt

core/
    model/
    domain/

privilege/
    api/
        PrivilegeState.kt
        PrivilegedCapability.kt
        PackageControl.kt

    standard/
        StandardPackageControl.kt

    shizuku/
        ShizukuController.kt
        ShizukuCapabilities.kt
        ShizukuPackageControl.kt

        service/
            PrivilegeUserService.kt
            IPrivilegeService.aidl

feature/
    settings/
        EnhancedAccessViewModel.kt
        EnhancedAccessScreen.kt
```

The most important architectural rule is that `feature/*` and most of `core/*` should not import:

```kotlin
rikka.shizuku.*
```

Only the Shizuku integration layer should.

---

## Conclusion

For a Compose/Kotlin application, Shizuku should be implemented as a **hot-pluggable privileged backend**.

```text
No Shizuku
    -> full baseline app

Shizuku stopped
    -> full baseline app

Shizuku permission denied
    -> full baseline app

Shizuku + permission
    -> baseline app + enhanced capabilities

Shizuku disappears
    -> immediate fallback to baseline
```

Use one application-scoped controller to manage Binder and permission state. Expose that state through `StateFlow`. Put privileged operations behind narrow domain interfaces. Prefer typed operations over arbitrary shell execution. Use direct Binder wrapping for simple system-service calls and a Shizuku `UserService` for larger privileged subsystems.

That structure keeps Shizuku useful without allowing it to become a hard dependency or contaminate the rest of the application architecture.
