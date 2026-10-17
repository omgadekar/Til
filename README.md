# TIL

Short for *until*.

One big number in the middle of a plain wallpaper: days remaining until a date you choose.
Repaints itself every day just after midnight.

## Build

No Gradle wrapper jar is included (it has to be downloaded), so:

**Android Studio**
1. File → Open → pick this folder.
2. It will offer to use the Gradle wrapper and download Gradle 8.7 + the AndroidX deps. Accept.
3. Run ▶ on a connected device, or Build → Build Bundle(s)/APK(s) → Build APK(s).
   Output lands in `app/build/outputs/apk/debug/app-debug.apk`.

**Command line** (needs a local Android SDK and Gradle 8.7+)
```
gradle wrapper          # once, to generate gradlew + the wrapper jar
./gradlew assembleDebug
```
If Gradle can't find the SDK, add a `local.properties` with `sdk.dir=/path/to/Android/sdk`.

For a release APK you'll need your own keystore; a debug APK installs fine for personal use.

## Settings

Everything is on the one screen, and the preview above the controls is the real painter at
540x1170, so what you see is what gets set.

- **Counting down to** — tap for a date picker. Any future date; the count is fixed to it and
  sticks at 0 once the day arrives rather than going negative. *Use New Year* in the picker
  goes back to the default, which is 1 January of next year recalculated every day, so it
  rolls over on its own.
- **Typeface** — fifteen options (see below). The list previews each one as the live day
  count, on the backdrop and in the ink already chosen, because a font name tells you nothing
  about what its numerals look like.
- **Text above the number** — a static line drawn above the number, same on every repaint.
  40 characters, tracked out, sized from the number so it stays subordinate. Leave it empty
  for the original number-only wallpaper.
- **Number colour** / **Background** — ten quick picks each, plus *Custom* for a hue/saturation
  picker with a hex field. A warning appears under the swatches if the two colours land within
  a WCAG contrast ratio of 3.0, the AA floor for large text, which is all this ever shows.

All of it is stored in SharedPreferences and read in one go as `WallpaperSettings`, so a
repaint can never catch a half-changed set of values.

## The screen itself

Always dark, never DayNight: the window is a frame around a preview of the wallpaper, so the
page borrowing the wallpaper's own palette is the point. That also drags `AlertDialog` dark
with it through `alertDialogTheme`, instead of flashing a white sheet over a black app;
`DatePickerDialog` is a framework dialog so it takes a framework theme of its own
(`Theme.Til.DatePicker`).

The design tokens are `res/values/colors.xml`. The page stays exactly
`Palette.DEFAULT_BACKDROP` (`#0A0A0A`), because the page is meant to read as the wallpaper;
everything above it is warm — surface `#171613`, hairline `#272420` — so the greys agree with
the warm ink and the muted gold accent `#C8A961` lifted out of the ink swatches. A dead-neutral
surface under warm text reads as a different material sitting on top of it.

Rows, buttons and the preview bezel are plain `<ripple>` and `<shape>` drawables rather than a
UI library: one less dependency, and a stock component set would have fought the typographic
look rather than carried it. Each one carries a `state_focused` layer as well as the ripple,
since a keyboard or D-pad user never triggers a ripple and would otherwise get no feedback at
all. The primary and ghost buttons also take `@animator/press`, a 2% scale down on touch.

No row has a trailing chevron. A settings row that shows its current value and lights up under
the finger is how Android says "this opens something"; a chevron there is an iOS habit, and
three of them is three pieces of chrome earning nothing.

The two colour rows sit directly under the preview, above the date and typeface rows, because
they are the only controls with no preview of their own and the only ones anybody scrubs back
and forth through — ten swatches judged against each other. Further down the page, as they
first were, the preview is scrolled off the top at the exact moment it is being used. The
other three are set once, each shows its value in the row, and the typeface dialog previews
every font itself.

Type is the wallpaper pairing one size down: `Display` is 34sp `sans-serif-light` with negative
tracking, `SectionLabel` is 11sp tracked caps at medium weight — 400 goes spindly once tracked
that far — and anything showing the count is set `tnum, lnum` so the line does not shuffle as
digits change. The four bundled serifs are digits-only, so none of the chrome can be set in
them; weight and tracking have to carry the register instead.

The preview sits on a dithered radial bloom (`preview_glow`). The page is otherwise one flat
tone, which left the device sitting on nothing. The fade spans about ten levels of an almost
black page, so without `android:dither` the steps land roughly 18px apart and read as
concentric rings on an OLED in a dark room — which is exactly the room this app is for.

Each settings row is a `LinearLayout`, so left alone a screen reader reads its children as
loose fragments — a value with no idea what it sets. `MainActivity.describe()` gives every row
one description naming its section and its value, and the headings above those three rows are
marked `importantForAccessibility="no"` so nothing gets said twice. The quick-pick chips are
48dp views with the colour drawn inset inside them: the targets stay contiguous while the
swatches stay small and close together, and `isSelected` is what makes the gold ring something
a screen reader reports rather than only draws.

Nothing about the preview is animated. It is a live mirror of the controls, so it repaints in
the same frame as the tap that caused it: a crossfade there would put a fifth of a second
between the haptic and the colour being judged, and would make a transition out of what is
meant to be continuous feedback. It is also painted into one bitmap for the life of the
screen (`WallpaperPainter.render(context, bitmap, days, settings)`, SRC rather than SrcOver so
a reused buffer starts clean) instead of allocating 2.5 MB per swatch tap.

The one thing that does move — the page reflowing as the contrast warning appears — is gated
on `ValueAnimator.areAnimatorsEnabled()`, which goes false when the user turns animations off
under Accessibility. The warning still appears; it just appears rather than slides.

## How it works

- `WallpaperPainter` does the date math and draws the bitmap. Digit size is pinned to a constant
  ink height (`TARGET_HEIGHT`) and only shrinks if a three-digit number exceeds `MAX_WIDTH`, so
  9 and 365 look like the same design.
- With a caption set, caption + gap + number are centred as one block rather than the number
  staying dead centre, which would leave the composition top-heavy.
- `WallpaperWorker` sets the bitmap on home + lock screen, then enqueues itself for 00:01 the
  next day. Self-rescheduling one-shot work, not a 24h periodic request, so it doesn't drift.
- `SystemEventReceiver` re-arms after reboot, app update, timezone change or date change.
- `ColorPickerView` fills its saturation/value square in two passes (white→hue horizontally,
  then transparent→black vertically) instead of using a `ComposeShader`. Hardware acceleration
  won't compose two gradient shaders below API 28 and this app runs from API 26.
- The manifest declares `SET_WALLPAPER` and `RECEIVE_BOOT_COMPLETED`; WorkManager merges in
  `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` and its own dynamic-receiver
  permission. All of them are normal permissions, so there is no runtime prompt.

## Typefaces

Four display serifs are bundled in `app/src/main/res/font/`, subset to Basic Latin, Latin-1
and a few typographic marks, which costs 26-53 KB each (about 150 KB in all). All SIL Open
Font License; the licence texts are in `licenses/`:

- **Bodoni Moda** (default) - optical size 96 instance, hairline contrast, ball terminal on the 7
- **Cinzel** - Roman inscriptional, carved-in-stone feel
- **Cormorant Garamond** Light - most delicate, note its figures are semi-oldstyle so heights vary
- **Playfair Display** - editorial, slightly sturdier than Bodoni

The caption is drawn in whichever face the number uses, these four included. They were
digits-only at first, which is why the caption used to fall back to a light sans whatever you
picked; they were rebuilt from the same upstream versions (Bodoni Moda 2.005, Cinzel 2.000,
Cormorant Garamond 4.001, Playfair Display 1.203), pinned to the same instances and subset
wider. Every digit outline and advance width is unchanged, so the wallpaper numerals are
exactly what they were.

Cinzel has no true lowercase — it is an inscriptional Roman face, so lowercase comes out as
small caps. That is the typeface, not a bug.

The fallback is still there, but it now asks rather than assumes: `WallpaperPainter.canDraw`
tests the caption against the face with `Paint.hasGlyph`, and only a character the face really
cannot draw sends the line to `sans-serif-light`. This matters because a `Typeface` built from
a single file has no fallback chain behind it, so without the check a Cyrillic or emoji caption
would land on the wallpaper as a row of .notdef boxes. The caption dialog carries the same test
and shows a note the moment you type something the face cannot set.

The other eleven resolve system families by name through `Typeface.create`, so they cost nothing
to ship and do have letters: Sans Thin / Light / Regular / Medium / Bold, Condensed Light /
Regular / Bold, System Serif, System Serif Bold, Monospace. An OEM skin missing one of these
falls back to the system default rather than failing, so the worker can't crash on it.

## Colours

Defaults are `Palette.DEFAULT_INK` (#F2EDE4 warm off-white) and `Palette.DEFAULT_BACKDROP`
(#0A0A0A). Pure #FFFFFF on #000000 clips the hairlines on OLED and reads cheaper; set them back
if you disagree.

The quick picks live in `Palette.INKS` and `Palette.BACKDROPS` — edit those lists to change what
the swatch rows offer. Nothing in them is at full chroma on purpose: saturated colour at 18% of
the screen height reads as a toy. Two light backdrops (Cream, Paper) are in there for anyone who
wants the inverse, paired with Graphite or Ink black for the number.

## Things you may want to change

- Number size: `TARGET_HEIGHT` and `MAX_WIDTH` in `WallpaperPainter`.
- Caption proportions: `CAPTION_RATIO` (size, relative to the number's ink height), `CAPTION_GAP`
  (the space between them) and `CAPTION_TRACKING`.
- Caption length cap: `Prefs.CAPTION_MAX_LENGTH`, currently 40.
- Caption colour is the same ink as the number. Dimming it to ~80% alpha looks more considered;
  it was left at full strength so that the colour you pick is the colour you get.
- Contrast warning threshold: the `3.0` in `MainActivity.refresh()`.
- Accent colour: `accent` in `colors.xml`. It drives the button, the checkmark in the typeface
  list, the switch, the ring on a selected swatch and every focus ring. `accent_hi` is the one
  step up used for the focused state of the filled button.
- Glow strength: the alpha on the three stops in `preview_glow`. Keep `android:dither` on if you
  raise the radius, or the banding comes back.
- Preview resolution: `PREVIEW_WIDTH` / `PREVIEW_HEIGHT` in `MainActivity`. The bitmap is
  allocated once at that size, so changing it costs nothing per repaint.
- Typeface sample size in the picker: `FontAdapter.sampleSize`, which steps down as the count
  gains digits so a three-digit sample still fits its chip.
- If the OEM kills background work (Xiaomi, Oppo, Vivo, Samsung are the usual offenders),
  whitelist the app under battery optimisation, or switch to a live wallpaper — see below.

## Alternative worth considering

A `WallpaperService` live wallpaper draws the number on demand instead of writing a bitmap
once a day. No scheduling, no battery-optimisation problem, always correct. Downside: the user
has to pick it from the wallpaper picker, and some launchers treat live wallpapers oddly.
If the daily job turns out flaky on your phone, that's the fix.
