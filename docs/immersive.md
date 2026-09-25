Yes. For your launcher, use Android's **immersive mode** on the launcher activity.

This gives you the full display area:

```text
┌─────────────────────────┐
│                         │
│                         │
│      launcher UI        │
│                         │
│                         │
└─────────────────────────┘
```

instead of:

```text
┌─────────────────────────┐
│      status bar         │
├─────────────────────────┤
│                         │
│      launcher UI        │
│                         │
├─────────────────────────┤
│     navigation bar      │
└─────────────────────────┘
```

Android's current API for this is `WindowInsetsControllerCompat`. :chatgpt-content-reference{index="0"}

## Recommended implementation

Put the behavior at the `Activity` level, not inside individual Compose screens.

```kotlin
private fun Activity.enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    WindowCompat.getInsetsController(
        window,
        window.decorView
    ).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        hide(WindowInsetsCompat.Type.systemBars())
    }
}
```

Then apply it to your launcher activity:

```kotlin
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterImmersiveMode()

        setContent {
            LauncherApp()
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enterImmersiveMode()
        }
    }
}
```

You need:

```kotlin
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
```

## Why `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`

Use:

```kotlin
BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
```

This is effectively modern **immersive sticky** behavior.

If the user swipes from a system edge, Android can temporarily overlay the system bars:

```text
normal
────────────────────
launcher
launcher
launcher
────────────────────

edge swipe
────────────────────
STATUS BAR     ← temporary overlay
launcher
launcher
NAVIGATION     ← temporary overlay
────────────────────
```

They disappear again automatically. :chatgpt-content-reference{index="1"}

That is much better for your launcher than permanently resizing the layout every time the bars appear.

## Your grid should use the entire display

This also changes how I would define the launcher grid from the previous specification.

Your logical six-column grid should be calculated from the **full launcher window**:

```kotlin
usableWidth = windowWidth
usableHeight = windowHeight
```

not:

```kotlin
windowHeight -
    statusBarHeight -
    navigationBarHeight
```

The launcher background/wallpaper and scrolling grid should extend underneath any temporarily revealed system UI.

However, Android recommends that important interactive controls avoid mandatory system-gesture areas. :chatgpt-content-reference{index="2"}

For your floating navigation pill, I would therefore distinguish:

```text
visual drawing bounds
        ≠
safe interactive bounds
```

The wallpaper/grid can extend literally edge-to-edge.

Your floating navigation pill should retain a configurable bottom safety inset so its buttons do not fight Android's back/home gestures.

For example:

```kotlin
val gestureInsets = WindowInsets.systemGestures
```

and use that information when calculating the pill's minimum bottom position.

## Do not use the old flags

Avoid old code like:

```kotlin
SYSTEM_UI_FLAG_FULLSCREEN
SYSTEM_UI_FLAG_HIDE_NAVIGATION
SYSTEM_UI_FLAG_IMMERSIVE_STICKY
```

Those are the old API. Current Android guidance uses `WindowInsetsController` / `WindowInsetsControllerCompat`. :chatgpt-content-reference{index="3"}

## One important limitation: you cannot truly force it

A normal third-party launcher cannot permanently disable Android's system UI.

Android explicitly permits the user to reveal system bars. Google states that applications on normal personal devices cannot permanently hide them. :chatgpt-content-reference{index="4"}

So the realistic behavior is:

```text
Launcher becomes active
    ↓
hide status bar
hide navigation bar
    ↓
full-screen launcher

User performs system gesture
    ↓
system bars temporarily appear
    ↓
Android hides them again

User leaves and returns
    ↓
launcher requests immersive mode again
```

That is probably what you want.

### If you really mean *force*

Permanent suppression requires a managed/dedicated-device setup, such as **Device Owner + lock task mode**. That is intended for kiosks and enterprise devices, not a normal installable launcher. Lock task mode can disable much more of the normal Android system UI. :chatgpt-content-reference{index="5"}

For this project I would specify:

> **The launcher MUST use immersive mode whenever its main activity is foregrounded. Both status and navigation bars are hidden using `WindowInsetsControllerCompat.Type.systemBars()`. The launcher MUST use transient-bar-by-swipe behavior, allowing Android system gestures to temporarily reveal system UI. The launcher layout itself remains edge-to-edge and MUST NOT reserve permanent space for either system bar. Immersive state is restored when the launcher resumes or regains window focus. Do not use deprecated `SYSTEM_UI_FLAG_*` APIs.**
