# RainLock

An Android touch lock for riding a motorbike in the rain.

Rain on a phone mount taps things. Most touch-lock apps cover the screen with an overlay, but that
overlay sits under the status and navigation bars, so water on the bottom edge still goes home,
pulls the notification shade down, or opens Circle to Search. RainLock blocks those too.

No ads, no internet permission. Android 12 (API 31) or later.

## Using it

- **Lock** from the Quick Settings tile, from **Lock now** in the app, or automatically when a
  chosen Bluetooth device (a helmet intercom, say) connects.
- **Unlock** by pressing volume down three times within 2 seconds, sliding the lock icon across
  the screen, or double-tapping it. Only taps on the icon count, so scattered drops can't unlock
  it. Bluetooth locks can also unlock when the device disconnects.
- The app underneath (Maps, Waze) keeps running and drawing; it just doesn't get touches.

## Modes

Android doesn't let an ordinary app cover the system bars, so how much RainLock can block depends
on what it's allowed to do. The app picks the best mode that's set up, and walks you through the
others.

| | Basic | Better | Best |
|---|---|---|---|
| Needs | Display over other apps | The accessibility service, switched on by hand | `WRITE_SECURE_SETTINGS`, granted once over adb or with Shizuku |
| Lock window | Ordinary overlay, under the bars, so it hides them | Accessibility overlay, above the bars | Same as Better |
| Accessibility service | Never on | On all the time, which some banking apps flag | On only while locked |
| After a swipe brings a bar back | Rain on the bar gets through: it can pull the shade down or open Circle to Search | Circle to Search can open | Blocked |

Best Mode's permission can be granted from a computer:

    adb shell pm grant com.aldo.rainlock android.permission.WRITE_SECURE_SETTINGS

or without one, through [Shizuku](https://shizuku.rikka.app/) started with Wireless debugging.
**Use Shizuku** in the app handles that. The grant survives updates and reboots and is lost on
uninstall.

## How the lock works

Better and Best Mode:

- **Touches:** a full-screen `TYPE_ACCESSIBILITY_OVERLAY`. Accessibility overlays sit above the
  status and navigation bars, where ordinary "draw over other apps" overlays can't reach.
- **Gesture navigation:** no window can intercept the system swipes, so the lock works around
  them. The overlay is focusable, which makes it the window that controls the system bars, and it
  hides the navigation bar in sticky immersive mode. A swipe up then only reveals the bar instead
  of going home. With the bar hidden sticky, Android also lifts the 200dp cap on
  `setSystemGestureExclusionRects`, so the overlay excludes both full edges from the back swipe.
- **Volume unlock:** the accessibility service filters key events
  (`flagRequestFilterKeyEvents`), so the presses don't change the volume.
- **Service only on while locked (Best Mode):** banking apps flag any accessibility service
  that's switched on, so RainLock keeps its own off between locks. Locking adds it to
  `secure/enabled_accessibility_services`, and it locks as soon as it connects (about 200 ms).
  Unlocking calls `disableSelf()`. If the service comes on any other way, such as a reboot or a
  crash mid-lock, it restores Circle to Search and switches itself straight back off.

Basic Mode uses an ordinary overlay instead. It takes focus and hides the bars the same way, but
a bar that a swipe brings back sits above it.

While locked, the visible overlay is what keeps the app alive. Android treats an app with one as
perceptible, so there's no foreground service.

## Blocking Circle to Search

Hiding the navigation bar blocks Circle to Search, but a swipe up shows the bar for a few seconds,
and holding the handle then opens it. There's no public API to switch it off, so I found the
settings behind the toggle by snapshotting `adb shell settings list secure|global|system` every
second while flipping Settings → Display → Navigation bar → Circle to Search:

- `secure/touch_and_hold_to_search`
- `secure/search_all_entrypoints_enabled`

Neither exists until the toggle is first used, and a missing value means on. Setting either to `0`
blocks the gesture straight away, with no reboot. Writing them needs `WRITE_SECURE_SETTINGS`, which
is why it's Best Mode only. RainLock writes both on Samsung and only
`search_all_entrypoints_enabled` elsewhere (untested), and puts the old values back on unlock. If
the phone dies mid-lock, the next start of the service restores them.

To map another phone, run the same snapshot diff while flipping its toggle.

## Test results

Galaxy S22+, One UI 8, Android 16. Swipes injected with `adb shell input`; Circle to Search and
the volume button by hand.

Best Mode:

| While locked | Result |
|---|---|
| Back swipe from either edge, top / middle / bottom | Blocked |
| One swipe up from the bottom | Blocked (reveals the bar) |
| Two swipes up 4 s apart | Blocked |
| Two swipes up 2 s apart | **Goes home.** The first swipe shows the bar for a few seconds |
| Hold the bottom edge while the bar is hidden | Blocked |
| Swipe up once, then hold the handle while the bar shows | Blocked |

Basic Mode:

| While locked | Result |
|---|---|
| Tap an app icon underneath | Blocked |
| Back swipe from either edge | Blocked |
| One swipe up from the bottom | Blocked (reveals the bar) |
| Two swipes up quickly | **Goes home**, with the lock still on top |
| Two swipes down from the top 4 s apart | Blocked |
| Two swipes down 2 s apart | **Pulls the shade down** over the lock |
| Settings app in front | **Hides the lock.** Settings hides other apps' overlays |

## Dead ends

- Samsung's `global/navigation_bar_gesture_while_hidden` no longer switches navigation mode on
  One UI 8, and switching the navigation overlay needs shell access, so the app can't swap to
  3-button navigation while locked.
- `adb shell input keyevent` can't test the volume unlock: injected keys skip the accessibility
  key filter. It needs the real button.

## Building

Needs JDK 17 and the Android SDK, with `JAVA_HOME` and `ANDROID_HOME` set.

    ./gradlew assembleDebug

On Windows, `.\build-and-run.ps1` builds, installs over USB, grants the Best Mode permission and
opens the app.

### Release builds

Copy `keystore.properties.example` to `keystore.properties` and point it at your own key. Both
it and `keystore/` are ignored by git. Then run `.\build-release.ps1`, which signs the APK,
prints the certificate, and copies it to `dist/`. Every update has to be signed with the same
key to install over the last one.

### Testing from a computer

    # lock without touching the phone
    adb shell am start -n com.aldo.rainlock/.MainActivity -a com.aldo.rainlock.action.LOCK -f 0x10008000
    # lock from the tile; open the panel first, since One UI freezes the app between locks
    adb shell cmd statusbar expand-settings
    adb shell cmd statusbar click-tile com.aldo.rainlock/.LockTileService
    # unlock: switch the service off (also restores Circle to Search)
    adb shell settings delete secure enabled_accessibility_services
