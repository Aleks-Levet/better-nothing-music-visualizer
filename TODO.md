# Better Nothing Music Visualizer - TODO list!

## To Do Fuckery

* Make some gifs for the demos, not videos!

* Check if auto update works

## Aleks' fucking todo
* Auto update mechanism without scary permissions

* parametric spectrum range preset for all phones

---

---

---

---

---

---

---

---

---

---

---

---

---

---

## Done!
* Remove haptic tab AND DISABLE ALL CODE RELATED TO IT if device doesn't have a haptic motor
* same for flashlight
* Remove notification detection
* Add disclaimer that this app uses Google Analytics
* Say that BNMV is DETERMINISTIC (add to readme)
* *(Low priority)* nothing styled widget with:
  * 3 buttons for source,
  * 3 for the Viz outputs,
  * 1 for start stop
  * 2 by 2 nothing style
* Make the auto latency toggle be in the latency card
* tweak tab switching speed arc
* collapse app theme setting section just like the experimental features
* put the typography selector under that app them section
* make the collapsed sections a bit bigger
* make the default theme have the nothing light theme when the device theme is light
* MAKE THE DEBUG APK BE IN GITHUB ACTIONS ARTIFACTS
* In the Material 3 expressive split button selector, when there are multiple rows, make the corner rounding of the 
edges only at the corner of the actual box of buttons. Multiple rows still look like it's something unified. And 
multiply the weight of the selected button by the UI shift variable.
* when the frequency range is large; they are less reactive for some reason. You don't need to divide the sum of the 
bins by the frequency range, the size of the frequency range. One of the causes is that the wide frequency ranges are 
less reactive than the narrow ones, which are usually the bass ones, so we can't really see the trebles.
* Collapse the live audio spectrum, and when it's collapsed, don't process it.
* Check if background is 0x000000 not if theme = default for the margin thing
* **RE ENABLE EDGE TO EDGE and FIX THE CLIPPING IN THE SIDE PADDINGS!**
  * THEY SHOULD BE APPLIED ON THE TAB LEVEL, NOT ON THE HORIZONTALPAGER LAVEL!
* Add m3e split row selectors
  * Typography menu
  * Idle breathing
  * Haptic mode
* Idle breathing setting isn't kept in memory
* move code away from mainactivity. now mainactivity is a mess.
* use the system navbar padding (fix it again)
* **Make the haptic amplitude mode resubmit a oneshot haptic all the time, even if it doesn't change!!!** currently it doesn't resubmit when the vibration amplitude doesn't change!
* make the oneshot duration slightly longer
* Remove idle breathing if device isn't a nothing phone
* Hide glyph tab if device is not a Nothing Phone AND DISABLE ALL CODE RELATED TO THE GLYPH UI
* Fix microphone latency
* decrease the decay for the android built in vizualiser (make the decay 60 fps even if the incoming data is 20fps)
* Add a disclaimer popup on first use of the media projection source that we do not record the screen, even if it looks like it.
* Track preset usage time instead
* Implement leaderboard and usage statistics
* --- 3.2.1
* Refine beat detection engine haptics
* Fix the amplitude haptics (Aleks)
* fix the app crash
* Remove richtap haptics and 
ALL CODE RELATED TO IT, ALSO DEPS
* Add one-shot spam haptics engine (from Oliver)
* Add 3.0 gamma to the UI shift vizualisation and only have downward smoothing
* **Change np1 preset with wider spectrum range (Aleks)**
* Remove the debug disclaimer in readme one we release
* Dynamic UI Theming: Use Palette API to match app colors with album art
* Dynamic Gain Normalization: Auto-Gain for quiet audio (Experimental)
* Navigation Bar Overlay: Graphical bar visualizer on top of nav bar
* Dynamic Peak Normalization: Auto-Gain for quiet audio (Experimental)
* Battery Saver Threshold: Dims visualization when low battery (Experimental)
* Dynamic UI Theming: Use Palette API to match app colors with album art
* Use rotating rounded polygon for the haptic viz visual (boost rotate and change shape when beat detected)
* Change that app theme carrousel thing cuz it sucks
* Global "UI shift" variable (float -0.3 to 1.0, reactive to 70-130 Hz)
* Add Shizuku audio source (Oliver) - Added structure & UI toggle
* Add shitty visualizer audio source WITH DISCLAIMER THAT IT SUCKS
* Alternating strobe mode blinking of the glyphs (10ms)
* Make toggle card white accent color when on
* Make toggle card thinner
* Add author name in generated description of community presets
* Make the "save to community" button more obvious
* Add time count for the vizualizer
* Add time count for the visualizer
* Change title "About and other" to "App info and updates"
* remove latency compensation from microphone input
* Make categories for this Todo list.
* Ask Oliver if i can replace these checkmarks by some easier bullet points?
* Add flashlight visualizer (same UI as Haptics, add it at the bottom of the haptics screen for now)
* Collapse experimental features in settings tab for lighter UI
* Rename OLED black theme to "Default theme"
* Auto dark light theme for Nothing and Material You theme
* Collapse experimental settings in toggleable buttons
* Change "check for updates" text to "Update" when available
* Make the local file button smaller
* Remove latency compensations for microphone
* Add microphone audio source
* **ADDED PHONE (1) COMPATIBILITY WITHOUT DEBUG MODE!**
* Fix status bar padding preventing true edge-to-edge
* Implement haptic visualization
* Add "disable glyph session when no sound" toggle
* Fix package name situation
* Fix haptics implementation
* Remove smoothing from glyph preview
* Quick Settings tile fix
* Merge contributions from rkyzen
* Add "enable glyph debug mode" warning for Phone (1) users
* Fix range slider not blocking tab swipes
* Fetch `zones.config` from GitHub repo
* Fix Phone (2) screen off issues (battery optimization fix)
* Resolve Quick Settings tile functional issues
* Added audio latency wizard utilizing the microphone to measure delay.
* *And many things we did before moving the todo list here!*
