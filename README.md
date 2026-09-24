# RainLock

Blocks touches while riding in the rain, including the Circle to Search gesture that water pooling
on the bottom edge keeps triggering. No ads, no internet permission.

## Setup

1. Phone on USB with USB debugging on. From this folder run `.\build-and-run.ps1`. It builds,
   installs, grants the Circle to Search permission (below) and opens the app. It needs JAVA_HOME (JDK 17)
   and ANDROID_HOME set.
2. In the app tap **Accessibility service** and turn RainLock on. If Android says "Restricted
   setting": Settings → Apps → RainLock → ⋮ → Allow restricted settings, then try again.
3. Pick unlock and lock methods in the app.

## How the lock works

- **Touches:** a full-screen `TYPE_ACCESSIBILITY_OVERLAY`. Accessibility overlays sit above the
  status and navigation bars; ordinary "draw over apps" overlays sit below them, which is why
  typical touch-lock apps leak there.
- **Volume unlock:** the accessibility service filters key events (`flagRequestFilterKeyEvents`).
- **System gestures:** no window can intercept the gesture-navigation swipes, so the lock works
  around them:
  - The overlay is focusable, which makes it the window that controls the system bars. It hides
    the navigation bar in sticky immersive mode, so a swipe up from the bottom only reveals the
    bar instead of going home.
  - Because the nav bar is hidden sticky, Android lifts the usual 200dp cap on
    `setSystemGestureExclusionRects`, so the overlay excludes both full edges from the back swipe.
  - The trade-off: while locked, the app underneath (Maps, Waze) loses input focus. It isn't
    paused.
- **Circle to Search:** switched off while locked by writing its settings (below).

Tested on the Galaxy S22+ (One UI 8, Android 16) with `adb shell input` swipes:

| While locked | Result |
|---|---|
| Taps, notification pull-down | Not yet tested from the PC |
| Back swipe from either edge, top / middle / bottom | Blocked |
| One swipe up from the bottom | Blocked (reveals the bar) |
| Two swipes up 4 s apart | Blocked |
| Two swipes up 2 s apart | **Goes home.** The first swipe shows the bar for a few seconds |
| Hold the handle (Circle to Search) | Blocked by the settings below |

## Circle to Search settings

Found by snapshotting `adb shell settings list secure|global|system` every second while flipping
Settings → Display → Navigation bar → Circle to Search:

- `secure/touch_and_hold_to_search`
- `secure/search_all_entrypoints_enabled`

Neither exists until the toggle is first used; missing means on. Setting either to `0` blocks the
gesture immediately, no reboot. The app writes both on Samsung, and only
`search_all_entrypoints_enabled` elsewhere (untested). Writing them needs `WRITE_SECURE_SETTINGS`,
which only adb can grant:

    adb shell pm grant com.aldo.rainlock android.permission.WRITE_SECURE_SETTINGS

The grant survives updates and reboots and is lost on uninstall. If the phone dies or the app
crashes while locked, the next start of the service puts the old values back.

To map another phone, run the same snapshot diff while flipping its Circle to Search toggle.

## Dead ends

- Samsung's `global/navigation_bar_gesture_while_hidden` no longer switches navigation mode on
  One UI 8, and switching the navigation overlay needs shell access, so the app can't swap to
  3-button navigation while locked.
- `adb shell input keyevent` can't test the volume unlock: injected keys skip the accessibility
  key filter. Use the real button.

## Testing from a PC

    # lock without touching the phone
    adb shell am start -n com.aldo.rainlock/.MainActivity -a com.aldo.rainlock.action.LOCK -f 0x10008000
    # unlock: switch the service off and back on (also restores Circle to Search)
    adb shell settings delete secure enabled_accessibility_services
    adb shell settings put secure enabled_accessibility_services com.aldo.rainlock/com.aldo.rainlock.RainLockService
