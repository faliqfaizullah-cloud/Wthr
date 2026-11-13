# Wthr
Weather app UI (Jetpack Compose): Weather, Welcome boarding pass, Upcoming Flights. Swipe between screens.
- Bouncing spring animations, each impact has a haptic thump that softens with the bounce.
- Select RAIN: falling rain animation plus random raindrop haptics and occasional thunder rumble.

## Publish from Termux
1. Copy this folder to Termux (`termux-setup-storage`, then `cp -r`/unzip into ~).
2. `cd Wthr && bash termux_publish.sh`
3. GitHub Actions builds the APK. Download it from Actions artifacts or Releases (tag v1.0.0).

## Widget and icon
- App icon: planet artwork as an adaptive launcher icon (`res/drawable-nodpi/ic_launcher_foreground.png`).
- Widget "Wthr 2×2": long-press home screen > Widgets > Wthr. 2×2 cells, 28dp corners, fixed size.
  Tap it to switch Sunny / Rain / Clear (with a haptic click). Values are the sample ones from the design (Orenburg 24° / 7° / 2°).

## Live weather and background
- Country/city come from the network location (ipwho.is); weather from Open-Meteo (no API key).
- WorkManager refreshes every 30 minutes in the background and updates the widget. Tap the widget to refresh now.
- The app asks once to be exempt from battery optimisation so refreshes are not delayed.

## Glass UI
`Glass.kt`: `Modifier.glass()` (translucent gradient, bright edge, soft shadow) and `FrostedBackground` (white haze with drifting pastel light). Used on the weather, welcome and flights screens.

## Widgets
- **Wthr 2×2** (doodle): Sunny / Rain / Clear / Cloudy cards.
- **Wthr Text 2×2** (black sentence): "24° now in New York feels 18° rain next 2 hrs", live for your location.
Both: 28dp corners, 2×2 only, tap to refresh, updated in the background every 30 minutes.

- **Wthr Photo 2×2**: big live temperature behind a church/tulip photo layer; blue morning sky by day, starry sky at night (uses the live sunrise/sunset). Fonts: Inter (SIL OFL).

## Widget shape
All widgets use `CardShape.kt`: a rounded rectangle 584 x 645 (taller than wide), corner radius 19.63% of the width (= 28dp on a standard 2x2 widget). Designs are scaled to the card height and cropped sideways.
