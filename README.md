# RainLock

Blocks touches while riding in the rain, including the Circle to Search gesture that water pooling
on the bottom edge keeps triggering. No ads, no internet permission.

## Setup

1. Phone on USB with USB debugging on. From this folder run `.\build-and-run.ps1`. It builds,
   installs, grants the adb permission (below) and opens the app. It needs JAVA_HOME (JDK 17)
   and ANDROID_HOME set.
2. Pick unlock and lock methods in the app. There's no accessibility switch to turn on: with the
   adb permission, RainLock switches its accessibility service on when you lock and off when you
   unlock.
3. Without the adb permission, the app shows a **Setup** section with two options:
   - **Basic: accessibility service** needs no computer: turn the service on by hand. If Android
     says "Restricted setting": Settings → Apps → RainLock → ⋮ → Allow restricted settings, then
     try again. The catches: the service stays on all the time, which banking apps flag, and
     Circle to Search is only blocked while the navigation bar is hidden (see How the lock works).
   - **Better: one-time permission** fixes both, but needs adb. **Use Shizuku** grants it from the
     phone (install Shizuku, start it with Wireless debugging, tap again), and **Copy command**
     gives the adb command for a computer.

   With the basic option on, Setup becomes **Optional upgrade** and keeps only the permission.
   Granting it switches the hand-enabled service off until the next lock.

## Release builds

Copy `keystore.properties.example` to `keystore.properties` and point it at your own key; git
ignores it. `.\build-release.ps1` then builds a signed APK into `dist\`. Every update has to be
signed with the same key to install over the last one.

## How the lock works

- **Touches:** a full-screen `TYPE_ACCESSIBILITY_OVERLAY`. Accessibility overlays sit above the
  status and navigation bars; ordinary "draw over apps" overlays sit below them, which is why
  typical touch-lock apps leak there.
- **Volume unlock:** the accessibility service filters key events (`flagRequestFilterKeyEvents`).
- **Service only on while locked:** banking apps flag any accessibility service that's switched on,
  so RainLock keeps its own off between locks. A lock adds it to
  `secure/enabled_accessibility_services` and it locks as soon as it connects (about 200 ms on
  the S22+). Unlocking calls `disableSelf()`. If the service comes on for any other reason, such
  as a reboot or crash mid-lock, it restores Circle to Search and switches straight back off. The
  Bluetooth trigger is a manifest receiver so it works with the service off.
- **System gestures:** no window can intercept the gesture-navigation swipes, so the lock works
  around them:
  - The overlay is focusable, which makes it the window that controls the system bars. It hides
    the navigation bar in sticky immersive mode, so a swipe up from the bottom only reveals the
    bar instead of going home.
  - Because the nav bar is hidden sticky, Android lifts the usual 200dp cap on
    `setSystemGestureExclusionRects`, so the overlay excludes both full edges from the back swipe.
  - The trade-off: while locked, the app underneath (Maps, Waze) loses input focus. It isn't
    paused.
- **Circle to Search:** hiding the navigation bar blocks it, but a swipe up shows the bar for a
  few seconds, and holding the handle then opens it. With the adb permission the lock closes that
  gap by switching it off through its settings (below). **Block Circle to Search** in the app
  turns those writes off, leaving only the hidden bar.

Tested on the Galaxy S22+ (One UI 8, Android 16) with `adb shell input` swipes, and the Circle to
Search rows by hand:

| While locked | Result |
|---|---|
| Taps, notification pull-down | Not yet tested from the PC |
| Back swipe from either edge, top / middle / bottom | Blocked |
| One swipe up from the bottom | Blocked (reveals the bar) |
| Two swipes up 4 s apart | Blocked |
| Two swipes up 2 s apart | **Goes home.** The first swipe shows the bar for a few seconds |
| Hold the bottom edge while the bar is hidden | Blocked, even without the settings |
| Swipe up once, then hold the handle while the bar shows | Blocked by the settings below. **Opens Circle to Search** without them |

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

The same permission lets the app switch its accessibility service on and off. The grant survives
updates and reboots and is lost on uninstall. If the phone dies or the app crashes while locked,
the next start of the service puts the old values back.

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
    # lock from the tile; open the panel first, since One UI freezes the app between locks
    adb shell cmd statusbar expand-settings
    adb shell cmd statusbar click-tile com.aldo.rainlock/.LockTileService
    # unlock: switch the service off (also restores Circle to Search; the next lock turns it back on)
    adb shell settings delete secure enabled_accessibility_services
