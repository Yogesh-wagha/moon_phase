# Moon Phase

An Android app and home-screen widget that draw the Moon's illuminated disc for
today or any date you pick, with the illuminated percentage shown as a number.

- **App** — a large rendered disc, the percentage, phase name, and a readout of
  age, distance, apparent diameter, elongation, and the next new and full moons.
  Underneath is a month calendar where **every day is drawn as its own small
  moon**, so a whole lunation reads at a glance. Tap any day to jump to it.
- **Widget** — resizable, updates every 30 minutes, shows tonight's disc plus
  the percentage and phase name. Tapping it opens the app.

## Accuracy

Positions come from Meeus, *Astronomical Algorithms* (2nd ed.):

| Quantity | Method | Checked against |
|---|---|---|
| Moon longitude, latitude, distance | truncated ELP-2000/82 (ch. 47) | Example 47.a |
| Sun longitude and radius vector | low-accuracy series (ch. 25) | — |
| Illuminated fraction | ch. 48, `k = (1 + cos i) / 2` | Example 48.a → 0.6784 vs 0.6786 |
| New / quarter / full instants | ch. 49 with the A1–A14 terms | Examples 49.a and 49.b, both within 5 s |

Illumination is good to a few parts in 10⁴ — well past what a rendered disc can
show. Phase instants are accurate to a few seconds, converted from Dynamical
Time to UT with the Espenak–Meeus ΔT polynomial.

The disc geometry is exact rather than a stock crescent image: the terminator
projects as an ellipse of semi-minor axis `r·|2k − 1|`, bowing towards the bright
limb for a crescent and away from it for a gibbous phase. Orientation is
northern-hemisphere (waxing lit on the right); to flip it for the southern
hemisphere, invert the `waxing` flag passed into `MoonGraphics.draw`.

Dates other than today are evaluated at **local noon**, which is the fair
representative instant for a whole calendar day. Today uses the live clock.

## Build

### Android Studio
Open the project folder, let it sync, then Run. If it asks about the Gradle
wrapper, accept — the wrapper JAR is not in this archive (binaries don't
survive the trip) and Studio regenerates it.

### Command line
```bash
gradle wrapper --gradle-version 8.10.2   # once, to create ./gradlew
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17 and the Android SDK (`ANDROID_HOME` set, platform 35 installed).

### No toolchain? Let GitHub build it
`.github/workflows/build.yml` is already set up. Push the folder to a repo and
the Actions run produces `moonphase-debug-apk` as a downloadable artifact.
Download it on your phone and install — you'll need to allow installs from
unknown sources.

## Adding the widget
Long-press the home screen → Widgets → Moon Phase. Drag it out; it resizes from
2×2 upwards.

## Where things live

```
MoonCalc.kt           astronomy — illumination, phase names, phase instants
MoonGraphics.kt       the disc, drawn on a plain Canvas so the app and the
                      widget produce identical pixels
MainActivity.kt       Compose UI — hero disc, readout, calendar of mini moons
MoonWidgetProvider.kt RemoteViews widget, renders to a bitmap
```

## Things you might want to change

| Want | Where |
|---|---|
| Southern-hemisphere orientation | invert `waxing` at each `MoonGraphics.draw` call site |
| Widget refresh rate | `updatePeriodMillis` in `res/xml/moon_widget_info.xml` (30 min is the OS floor) |
| Colours | the token block at the top of `MainActivity.kt`, plus `MoonGraphics.DARK_SIDE` |
| Surface detail | the `features` array in `MoonGraphics.kt` — x, y, size in units of the disc radius |
| Week starting Sunday | `lead` in `MonthGrid` — drop the `+ 6) % 7` shift |
| Moonrise / moonset times | not included; you'd need observer lat/long and Meeus ch. 15 |
