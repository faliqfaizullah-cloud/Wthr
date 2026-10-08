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
