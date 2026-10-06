Yes. With Shizuku available, you can make Shelter integration feel almost native to the launcher.

### Important detail: use Shelter itself for unfreeze-and-launch

Shelter does not actually uninstall frozen apps. It calls `DevicePolicyManager.setApplicationHidden(..., true)` inside the work profile. Android then treats the package as unavailable, so normal launchers stop seeing it. :chatgpt-content-reference{index="0"}

More importantly, Shelter already exposes a public operation specifically for this:

```text
net.typeblog.shelter.action.PUBLIC_UNFREEZE_AND_LAUNCH
```

Its `DummyActivity` is exported, and this action is explicitly permitted without Shelter's internal authentication. :chatgpt-content-reference{index="1"}

So your launcher can essentially replace Shelter's home-screen widget/shortcut.

```kotlin
fun unfreezeAndLaunchWithShelter(
    context: Context,
    packageName: String,
) {
    val intent = Intent(
        "net.typeblog.shelter.action.PUBLIC_UNFREEZE_AND_LAUNCH"
    ).apply {
        component = ComponentName(
            "net.typeblog.shelter",
            "net.typeblog.shelter.ui.DummyActivity"
        )

        putExtra("packageName", packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
}
```

Shelter then:

1. Receives the request in the personal profile.
2. Forwards it into its work profile.
3. Calls `setApplicationHidden(..., false)`.
4. Finds the app's launch activity.
5. Launches it.
6. If Shelter's auto-freeze setting applies to that app, registers it to be frozen again later. :chatgpt-content-reference{index="2"}

That last point is why I would **not** normally have Shizuku perform the unfreeze itself.

---

# Where Shizuku becomes useful

The hard problem is not launching the frozen app. It is **remembering and displaying it after Android stops telling your launcher that it exists**.

I would change your launcher model from:

```text
LauncherApps says app exists
        ↓
show app

LauncherApps says app disappeared
        ↓
delete app
```

to:

```text
persistent AppRecord
    packageName
    user/profile
    launchComponent
    label
    icon/symbol
    placement
    state
```

For example:

```kotlin
enum class AppAvailability {
    AVAILABLE,
    SHELTER_FROZEN,
    PROFILE_PAUSED,
    UNINSTALLED
}

data class AppRecord(
    val packageName: String,
    val userSerial: Long,
    val launchComponent: ComponentName?,
    val label: String,
    val state: AppAvailability,
)
```

The critical identity should be approximately:

```text
(packageName, user/profile)
```

not just the package name, because:

```text
com.instagram.android / personal
com.instagram.android / Shelter
```

are two separate launcher applications.

## When an app disappears

Do **not** immediately erase its launcher record.

Instead:

```text
Launcher app disappears
        │
        ▼
Was it previously in the Shelter work profile?
        │
        ├── no → normal uninstall handling
        │
        └── yes
             │
             ▼
        ask privileged package state
             │
             ├── package still installed + hidden
             │       → SHELTER_FROZEN
             │
             └── package no longer installed
                     → UNINSTALLED
```

With Shizuku's shell privileges, you can inspect the package manager state for the specific work-profile user. Shizuku explicitly exists to let ordinary apps call system APIs with ADB/root privileges, although ADB-mode permissions can vary by Android version. :chatgpt-content-reference{index="3"}

AOSP's package manager provides both:

```text
getApplicationHiddenSettingAsUser(packageName, userId)
setApplicationHiddenSettingAsUser(packageName, hidden, userId)
```

and the shell exposes the same functionality as:

```bash
pm hide --user USER_ID PACKAGE
pm unhide --user USER_ID PACKAGE
``` :chatgpt-content-reference{index="4"}


So your Shizuku integration layer could conceptually expose:

```kotlin
interface PrivilegedPackageService {
    suspend fun isInstalled(
        packageName: String,
        userId: Int
    ): Boolean

    suspend fun isHidden(
        packageName: String,
        userId: Int
    ): Boolean

    suspend fun listPackages(
        userId: Int,
        includeHidden: Boolean
    ): List<PrivilegedPackageInfo>
}
```

Keep all Shizuku-specific code behind this boundary. Do not contaminate the normal launcher model with shell/Binder details.

---

# This fixes an important problem in your launcher

Suppose your home layout contains:

```text
┌─────────┐ ┌─────────┐
│ Signal  │ │ Discord │
└─────────┘ └─────────┘
       ┌───────────────────┐
       │       Firefox     │
       └───────────────────┘
```

and Discord is a frozen Shelter app.

A conventional launcher sees Discord disappear and may remove its item.

**Your launcher should not.**

It should become:

```text
Discord
state = SHELTER_FROZEN
placement = unchanged
```

The tile remains exactly where the user put it.

Tap it:

```text
Frozen Discord tile
       ↓
PUBLIC_UNFREEZE_AND_LAUNCH
       ↓
Shelter
       ↓
unhide work-profile Discord
       ↓
launch
```

When Shelter freezes Discord again:

```text
LauncherApps reports it unavailable
       ↓
Shizuku confirms hidden=true
       ↓
tile becomes SHELTER_FROZEN again
```

The user never has to know that Android removed the underlying launcher activity temporarily.

---

## App list behaviour

I would treat frozen Shelter applications almost exactly like ordinary applications.

For example:

```text
Apps

Firefox
Instagram
Mastodon
Signal
Telegram
YouTube
```

not:

```text
Installed
    Firefox
    Signal

Frozen
    Instagram
    Telegram
```

You can give frozen apps a small state indication if desired:

```text
Telegram   ❄
```

or reduce opacity slightly.

But structurally they should remain first-class `AppRecord`s. Search, filters, folders, and your custom grid should continue to find them.

This matters particularly for your launcher because your layouts are explicitly persistent and freely placed. A temporary Shelter freeze must **never cause Tetris/layout compaction or destroy placement information**.

---

# Cold-start recovery

There is one additional case.

Imagine:

1. User has Shelter.
2. Instagram is already frozen.
3. User installs your launcher.
4. Your launcher has never seen Instagram while unfrozen.

Normal `LauncherApps` discovery cannot necessarily give you that app because it is hidden.

This is where Shizuku is especially useful.

On launcher initialization:

```text
discover Android profiles
        ↓
identify managed/work profile
        ↓
normal LauncherApps enumeration
        +
privileged package enumeration
        ↓
find packages installed for work user
but hidden from LauncherApps
        ↓
create SHELTER_FROZEN AppRecords
```

You can obtain/cache their:

- package name;
- application label;
- APK/resources;
- icon;
- known launcher component where available;
- user ID/profile serial.

Thus you do not depend entirely on having seen the application before it froze.

---

# Launch strategy I would implement

Use three tiers:

```text
App clicked
│
├─ AVAILABLE
│    └─ LauncherApps.startMainActivity(...)
│
├─ SHELTER_FROZEN
│    ├─ Shelter installed?
│    │    └─ PUBLIC_UNFREEZE_AND_LAUNCH
│    │
│    └─ otherwise
│         └─ Shizuku fallback
│              ├─ unhide package
│              ├─ wait for PackageManager/LauncherApps update
│              └─ startMainActivity(...)
│
└─ PROFILE_PAUSED
     └─ work-profile-paused handling
```

Android provides `LauncherApps.startMainActivity(ComponentName, UserHandle, ...)` specifically to launch an activity belonging to another accessible profile. :chatgpt-content-reference{index="5"}

### Why make Shizuku the fallback?

If you simply do:

```bash
pm unhide --user 10 com.example.foo
```

you can make the package launchable again, but you have bypassed Shelter's `PUBLIC_UNFREEZE_AND_LAUNCH` flow.

Shelter's flow also checks whether that app belongs to its auto-freeze list and registers it with its freeze service. :chatgpt-content-reference{index="6"}

So:

**Preferred**

```text
Launcher → Shelter → Android
```

**Fallback**

```text
Launcher → Shizuku → Android
```

---

# You could expose more Shelter controls

Once you have the Shizuku package-state abstraction, long-pressing an app could potentially expose:

```text
Open

Work profile
────────────
Freeze now
Unfreeze
App info
```

`pm hide --user ...` can implement **Freeze now** directly. AOSP still exposes `hide`/`unhide` through PackageManager's shell command. :chatgpt-content-reference{index="7"}

I would distinguish these operations internally:

```kotlin
sealed interface AppLaunchAction {
    data class Normal(
        val component: ComponentName,
        val user: UserHandle
    ) : AppLaunchAction

    data class ShelterUnfreeze(
        val packageName: String
    ) : AppLaunchAction

    data class ShizukuUnfreeze(
        val packageName: String,
        val userId: Int
    ) : AppLaunchAction
}
```

That keeps Shelter compatibility out of your UI code.

---

## Best architecture

I would add something roughly like:

```text
launcher-appmodel/
    AppRecord
    AppAvailability
    AppIdentity

launcher-profiles/
    AndroidProfileRepository
    WorkProfileRepository

launcher-package-monitor/
    LauncherAppsMonitor
    PackageStateResolver

launcher-integration-shelter/
    ShelterDetector
    ShelterLauncher
    ShelterPackageResolver

launcher-privileged/
    PrivilegedPackageService
    ShizukuPackageService
```

Then your home grid and app list know only:

```text
AppRecord
    identity
    presentation
    availability
```

They should not know what Shelter, `DevicePolicyManager`, Shizuku, or `pm unhide` are.

### Result

From the user's perspective, Shelter apps stop behaving like disappearing software:

```text
freeze
   ↓
icon stays where it was
   ↓
tap icon
   ↓
app opens normally
   ↓
lock phone
   ↓
Shelter freezes it again
   ↓
icon still stays where it was
```

That is the integration I would implement. It works *with* Shelter's existing public unfreeze mechanism, while Shizuku solves the launcher-side visibility/state problem instead of unnecessarily replacing Shelter's policy management.
