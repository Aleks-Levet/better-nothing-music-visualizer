# Changelog

## V4.1 [2026-10-24]
### I'm really proud that the apk is still just 4MB
- As you can see, added a way to see the changlog everytime you update the app in a non-intrusive way!
- **Audio processing and capture**
  - Added a new microphone mode and fixed microphone latency.
  - Improved high-quality audio analysis by a lot, enable it in Experimental Settings (treble and details are more noticeabe now for example)
  - Improved network audio synchronization and latency handling.
  - Added Linux and Windows PC streaming tools so computer audio can be sent to BNMV over the local network (Check them in the github repo).
- **Glyphs and device compatibility**
  - Added an **idle-breathing-only** mode and a static animation mode for a dim glyph flashlight.
  - Improved breathing animations, reduced their energy use, and fixed breathing stopping over time.
  - Fixed Phone (4b) detection.
  - Improved glyph configuration downloading and version updates.
  - Raised the configurable maximum glyph brightness to 150%.
  - Added a **workaround for Glyphify** stopping the service and fixed cases where the service stayed active unnecessarily.
  - Updated the interface for devices without a glyph interface and fixed horizontal scrolling on Waydroid.
- **Overlays**
  - Overlays aren't experimental anymore, yay!!
  - Fixed camera overlay rotation and reduced its battery use.
  - Mirrored the frequency display in the navigation bar visualizer.
  - Made the max corner rounding for the edge overlay higher.
  - Fixed clipping in on-screen overlay visualizers (they should look better now)
- **Controls and integrations**
  - Added a way to input precise values by long pressing any slider
  - Added public intents for automation apps and a Quick Settings tile for idle-breathing-only mode.
  - Continued tuning haptic modes and streamlined haptic and flashlight controls.
- **Settings and interface**
  - ***Improved default settings for new users!!***
  - Fixed settings not saving sometimes.
  - Improved light-theme , glyph and glyph matrix previews, the navigation bar, and card/slider animations.
  - Refined settings UI and navigation.
  - Changed the default font to the rounded Google Sans Flex.
- **Statistics and updates**
  - Included idle-breathing-only sessions in usage statistics.
  - Expanded the stats screen with overlay and broadcasting times, beat-reactive visuals, and other refinements.
  - Added and improved the in-app update flow, including configuration updates.
  - Encrypted stats exports are now possible (if you need to switch devices) and made performance optimizations.
- **Maintenance and localization**
  - Updated dependencies, the Glyph SDK, Gradle, and translations, including adding Hungarian and Slovak one.

## V4.o [2026-08-23]

- **General**
  - Changed the app name to "BNMV" so the name fits on your homescreen.
  - Fixed the bug where the thing did the other thing.
  - Neutralized almost every crash and bug to make the app more stable.
  - Reduced the APK size to 3.5 MB.
  - Rewrote the core engine for centralized processing, better performance, and new feature support.
  - Removed features that were not working, such as community presets and announcements.
  - Cancelled Shizuku mode because it did not work.
  - Simplified parts of the app and removed most of the popups shown when opening it.
  - Added an experimental setting for higher-quality audio analysis.
  - Added new translations.
  - Enhanced audio processing for all sources.
  - Enhanced Spotify compatibility.
  - Added easter eggs.
  - The release also notes 300+ other changes.
- **Controlling BNMV**
  - Added two home-screen widgets to control the app.
  - Fixed the Quick Settings tiles.
  - Added public intents so other apps can control BNMV.
  - Added an integration interface for developers to visualize music from their apps without screen sharing.
- **UI**
  - Reorganized the app's home page and redesigned the settings screen.
  - Made substantial UI enhancements, added new animations and improved various UI elements.
  - Added a statistics page showing app usage and sources.
  - Added the Google Sans Flex font, tunable to the user's liking.
  - Made the UI react to the beat.
  - Fixed color schemes.
  - Added predictive back animations.
  - Added a tablet UI for large screens.
  - Added help buttons, new slider animations, and split buttons.
- **Glyph**
  - Fixed the glyph preview for all phones.
  - Added friendly names to glyph visualization presets.
  - Added Phone (4b) compatibility.
  - Fixed Phone (4a) Pro compatibility.
  - Fixed an issue where Phone (3) sometimes did not work.
  - Fixed and improved idle breathing animations to use less battery.
  - Added a matrix rain animation for glyph matrices.
  - Added an experimental alternate visualizer engine that filters out steady frequencies.
  - Added more granular controls, including a threshold and speed.
  - Combined the gamma slider and additional controls with the brightness slider.
- **Haptics**
  - Enhanced beat detection.
  - Added compatibility for inexpensive on/off rotary haptic motors.
  - Enhanced the haptic monitor.
- **Flashlight**
  - Enhanced beat detection.
  - Added support for flashlights with multiple intensity levels.
  - Simplified flashlight settings.
- **On-screen visualizers**
  - Added navigation bar, edge lighting, and front camera visualizers, enabled through experimental settings.
  - Added a Glow blur style.
  - Added color selection.
  - Added HDR simulation for OLED screens by dimming the rest of the display.
  - Improved rendering over the navigation bar.
  - Added the option to extend the camera visualizer into a pill.
- **Multi-device sync**
  - Added the ability to sync multiple devices over Wi-Fi UDP.
  - Improved sync stability; further testing is still needed.
- The release notes mention that some changes may have been missed.
- Added first-time contributors @nyxalune and @AbdullahMRiad.

## V3.2.1 [2026-06-09]

- Fixed a crash.
- Hid developer mode from general users.
- Fixed a malformed server string being able to crash the app for everyone.
- Enhanced haptics and fixed bugs when switching between haptic modes.
- Improved the haptic visualizer controls.
- Removed the Richtap haptic mode because it was not working.
- Fixed the UI shift variable so the UI reacts better to the beat.
- Fixed off-screen switches and other small issues.
- Reduced the app size from 5.4 MB to 5.2 MB.

## V3.2 [2026-06-07]

- **Core features and enhancements**
  - Made major UI changes.
  - Removed the timeline feature.
  - Added a Visualizer source that does not require MediaProjection.
  - Added an on-screen visualizer overlay with graphics on Android navigation bars.
  - Added dynamic gain control in experimental settings.
  - Added a capture session timer.
  - Added a flashlight visualizer (work in progress).
- Enhanced haptic visualization.
- **UI/UX improvements**
  - Enhanced option tile design.
  - Improved the source and theme selectors.
  - Redesigned the About screen and thanks dialog.
  - Updated the toggle card UI.
  - Optimized palette generation.
  - Added author information display in presets.
  - Moved visualizer configuration to glyph settings.
  - Added a new app icon.
- **Settings and localization**
  - Implemented a global announcement system.
  - Added in-app update installation.
  - Enhanced localization across multiple screens.
  - Added translations that the release notes invite the community to improve.
- **Community and documentation**
  - Added a localized license screen.
  - Updated the license version to 1.1.
  - Added Discord and GitHub links.
  - Redesigned settings links with a new LinkCard component.
  - Added usage-based thanks messages and community-sharing feedback.
  - Updated the README with Discord server information.
- Added spectrum support in `zones.config` by @cookiedcdev.
- Updated the README with app details and credits by @MystiKitsu.
- Added first-time contributor @cookiedcdev.

## V3.1 [2026-05-17]

- Added a visual preset editor for custom glyph layouts and configurations.
- Added a spectrum graph to the audio setup screen and other UI visuals.
- Added experimental RichTap support, including its library, engine, assets, and selectable mode.
- Added a new beat-detection haptic engine.
- Added Quick Settings tiles for the visualizer and haptic visualization.
- Added haptic-frequency visual feedback in the UI.
- Added a brightness slider and a toggle to disable glyph visualization while using haptics.
- Added auto-collapsing UI with animated toggle cards.
- Added an option to disable glyphs automatically when audio is silent.
- Added `ZONES_CONFIG.md` documentation.
- Added `np3a-percussion` and `np3a-percussion-pro` device configurations.
- Moved audio analysis from peak-based to magnitude-based processing.
- Switched visualization to RMS and added EQ work to bring out more high-frequency detail.
- Worked to fix glyph matrices.
- Refined spectrum rendering and tuned beat detection.
- Changed custom preset sliders to logarithmic scaling.
- Updated idle breathing so it ignores gamma and dimmer settings for more consistent output.
- Polished the Settings and About UI.
- Improved update and latest-configuration checking.
- Fixed Quick Settings tiles.
- Fixed glyph previews to show only addressable glyphs.
- Fixed Phone (3) and Phone (4a) Pro matrix preview and editor rendering.
- Fixed `np4ap` / Phone (4a) Pro naming issues.
- Fixed `zones.config` issues, wording, UI padding, and other small UI bugs.
- Fixed Turkish app-string typos and refreshed translations.
- Removed the broken shake gesture.
- Refreshed the README and Turkish README, credits and developers sections, and badges.
- Updated the license.
- Added a Crowdin configuration.
- Updated dependencies and reorganized internal folders.
- Cleaned up the build and workflows, added Gradle caching and beta APK automation, and automated `zones.config` versioning.

## V3.0 [2026-05-04]

- Added haptic-driven visualization with improved responsiveness and quality.
- Added a real-time glyph preview.
- Added an idle glyph pulse when there is no sound.
- Added a button to update `zones.config` without updating the app.
- Added swipe navigation across the app.
- Added a true edge-to-edge UI.
- Cleaned up the UI and fixed layout issues.
- Added Material You Light and Nothing-inspired Light themes.
- Added Material You Dark, Nothing-inspired Dark, and Liquorice Black themes.
- Added initial support for Phone (3) and Phone (4a Pro); the release notes state that support was not working yet.
- Added and refined device presets.
- Added Glyph Developer Mode for device spoofing.
- Reduced haptic response latency.
- Smoothed visualization updates.
- Made audio processing more efficient and reduced unnecessary rendering and UI overhead.
- Improved the audio capture and processing pipeline.
- Cleaned up and modularized the codebase.
- Updated the README and documentation structure.
- Fixed the gamma slider, crashes and stability issues, UI overlap and spacing, `zones.config` JSON syntax, and string formatting and localization issues.
- Added a Turkish translation by @bugycugy.
- Added `README_MR.md` by @mysty-y and updated README content and language options.
- Added first-time contributor @mysty-y.

## V2.8 [2026-04-24]

- Removed legacy hardcoded preset fallback logic and old vocal/bass leftovers from the active runtime path.
- Refactored `AudioCaptureService` so capture and preset processing are driven by `zones.config` without duplicated setup code.
- Improved foreground/background service behavior, lifecycle handling, and Quick Settings tile refreshes.
- Added live audio route monitoring with `AudioDeviceCallback` for Bluetooth, wired, and speaker hot swapping.
- Fixed automatic device memorization so the selected output updates without an app restart.
- Fixed latency persistence so each audio route/device can save and restore its own latency value.
- Added a Haptics tab with a vibration icon and placeholder page.
- Cleaned up old settings and resource leftovers and restored the adaptive launcher background color resource.
- Added fine controls to latency compensation.
- Fixed colors.
- Added the NType font.
- Added bouncier and snappier animations.
- Added haptics in additional places.

## V2.7 [2026-04-20]

- Reduced the APK size to 2.4 MB.
- Fixed the latency slider.
- Added quick-access latency presets and bouncy animations for them.
- Added optimizations.
- Updated the Android target to Android 16.
- Reduced padding for the AMOLED black interface.
- Made tab-switching animations smoother.
- Added haptics to the latency slider.
- Removed the default `np2` fallback.
- Added automatic device memorization and latency changes based on the active device.
- Added a Settings tab for future features.

## V2.6 [2026-04-18]

- Reduced the APK size from about 50 MB to 3 MB through optimizations.
- Added Phone (4a) support; the release notes state that it was not working yet.
- Updated `zones.config` with new presets for different phone models.
- Optimized the app and code.
- Polished the UI.
- Improved smoothness and stability, with the app reported to run at 120 FPS.
- Added the Ndot font for headers.
- Improved layout and spacing.
- Refined font sizes.
- Removed the gray status bar color.
- Made the pure black theme fully black.
- Removed unused code from the previous version.
- Listed known issues: Phone (4a) compatibility, the latency UI, excess text on the home page, the unfinished About screen, and broken notifications.
- Asked users to report any additional issues.

## Android-app [2026-04-17]

- Collaborated with ItzNotHj on the UI.
- Reworked the UI toward Material 3 Expressive.
- Added presets.
- Improved the user experience.
- Migrated to Jetpack Compose.
- Added optimizations.
- The release notes said that version 2.6 was coming.

## V2_release_redesign [2026-04-11]


- There is an app now!
- Introduced a complete design overhaul to make the app more intuitive, easier to use, and more responsive.
- Refined the interface to provide more control and a smoother experience.
- The release notes state that version 1 was omitted because it was for testing.

## New-release-yay! [2026-01-28]

- Published the first release of the working script.
- Noted that it worked well but could be slow on phones with many zones; the release notes said this would be partly addressed in the next version.
- Identified the script version as 1.0.0 and the configuration version as 2026.1.0, using a date-based numbering scheme.
- Directed users to the README for usage instructions and invited them to report issues.
