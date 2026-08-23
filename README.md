<img src="/Docs/Banners/Banner4.png" alt="Better Nothing Music Visualizer"/>

🌐 Read this in other languages:  🇮🇳 [हिन्दी](Docs/README-hi.md), 🇮🇳 [Marathi](Docs/README-mr.md), 🇹🇷 [Türkçe](Docs/README_tr.md), 🇸🇦 [العربية](Docs/README-ar.md)

### An (android) app that not only brings better music visualization on the Glyph interface/matrix of Nothing Phones, but also lets you feel your music through the haptic motor ***AND*** use the flashlight of your phone as a music visualizer!

# **Here's the fucking download button, use it!**
> (if you don't want to read the rest of the readme)

[<img widtht="60%" alt="Get it on GitHub" src="./.github/assets/big-ass-fucking-download-button.png" />](https://github.com/Aleks-Levet/better-nothing-music-visualizer/releases/download/V4.o/Better-Nothing-Music-Vizualizer-V4.o.apk)
<img 
  src="https://img.shields.io/github/downloads/Aleks-Levet/better-nothing-music-visualizer/total?style=for-the-badge&logo=github&label=Devices%20made%20better:&color=ff0000&labelColor=000000"
  style="height:40px;">

This android app grabs the live audio stream from your device using sources such as **Media Projection**, your **microphone**, the classic *Android Visualizer audio stream (not recommened)*, or another device running the same app, and processes it directly into the glyphs, the haptic motor, or your flashlight. This means you can visualize music from **Spotify, YouTube Music**, and basically any other app! It can even work on games, and also you can use the microphone to vizualise music that others play, especially at a party!

   
## 💬 Join Our Discord Server

Click the banner below to join our Discord server and connect with the community:

<a href="https://discord.gg/h7DYNttc8K">
  <img src="https://discord.com/api/guilds/1509496060094054531/widget.png?style=banner3" 
       alt="Discord Server" 
       style="border-radius: 12px; box-shadow: 0 0 15px rgba(88, 101, 242, 0.4); max-width: 100%; height: auto;">
</a>

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Thinking%20Face.png" alt="Thinking Face" width="25" height="25" /> Why does this exist?
For a lot of people (including me), the *stock Glyph Music Visualiastion provided by Nothing* feels random.  
Even if it technically isn’t, the visual response to music just isn’t very obvious. On top of that, the feature isn’t really using the full potential of the Glyph Interface. So that’s why I made my own music visualizer.

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2696_fe0f/512.gif" alt="⚖" width="32" height="32"> Stock vs Better Music Visualizer
| Feature | Nothing Stock | **Better Music Visualizer** |
| :--- | :--- | :--- |
| **Light levels** | ~2-bit depth (3 light levels) | **12-bit depth (4096 light levels)** |
| **Frame Rate** | 20 FPS *(limited by the android vizualiser api)*| **60 FPS** |
| **Precision** | Feels random, it's hard to acually see how it's synced | **Uses FFT analysis to precisely determine the intensity of each light** |
| **Zones** | Standard, full physical glyphs are used | **Each glyph segment and sub-zone is used and controlled independently** |
| **Visualisation method** | Real-time only | **Realtime with down to 20ms latency, or pre-processed audio files** |

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ac/512.gif" alt="🎬" width="40" height=""> [Video demos and examples](https://github.com/Aleks-Levet/better-nothing-music-visualizer/blob/main/Docs/Demo-video-examples.md)

### See the difference in action! [**Click here to easily browse our video demos!**](https://github.com/Aleks-Levet/better-nothing-music-visualizer/blob/main/Docs/Demo-video-examples.md)

## 📲 Supported Nothing Phone Models for Glyph Visualisation
**Currently these models are supported:**
- Nothing phone (1)
- Nothing phone (2)
- Nothing phone (2a)
- Nothing phone (2a plus)
- Nothing phone (3a)
- Nothing phone (3a pro)
- Nothing phone (3) 
- Nothing Phone (4a)
- Nothing Phone (4b)
- Nothing Phone (4a pro) *(not really ready yet but almost)*

**Keep in mind that every android phone is compatible with the haptic and the flashlight visualization.**


### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2699_fe0f/512.gif" alt="⚙" width="25" height="25"> How it works (technically)
- **Deterministic**: BNMV is fully **deterministic**. Unlike the stock implementation *(or the copycat ai slop third party apps that copy our thing)* that can feel semi-random, our visualizer uses direct mathematical analysis of the audio stream to ensure every light corresponds exactly to a frequency range. This means that *if you keep the same settings*, **the same song will result in the same light pattern.**
- A high quality audio stream is captured through the source you select
- **FFT (Fast Fourier Transform)** is used to analyze frequencies in a **20 ms window** for each **16.666 ms frame** (60 FPS), making the visualization more accurate
- For the Glyph Visualization:
  - **Frequency ranges** for each glyph zone are defined in `zones.config` and are fully customizable.
  - The **brightness** of each glyph is defined by the **peak magnitude** found in its assigned frequency range  
  This measures how loud different frequency “zones” are
  - **Downward-only smoothing** is applied to make the animation smoother while preserving responsiveness (this is the secret sauce)
  - Then it's ready to be displayed on the glyphs!
- For the haptic and the flashlight "visualisation":
  - Either the amplitude of the bass frequency is used to determine the brightness of the flashlight or the amplitude of the vibration motor
  - Or the derivative of the amplitude of the bass frequancies is used to detect beats, which trigger a pre-processed pattern on either the haptic motor or the flashlight.

## 🛠️ Presets (for glyph visualization)
The visualizer's behavior, from frequency ranges to animation smoothing, is entirely controlled by the `zones.config` file. Whether you want to tweak existing presets or add support for a new phone model, you can find everything you need in our configuration guide.
### 📖 [**Detailed zones.config Documentation**](Docs/ZONES_CONFIG.md)

## 📖 How to use the App?
1. **Download the latest APK** from the releases.
2. **Grant Permissions**: The app needs Screen Capture (Media Projection) and Notification access.
3. **Start Visualizing**: Hit the "Start" button and play music from any app!
4. **Adjust Latency**: If the lights aren't perfectly synced with your Bluetooth speaker or headphones, use the **Audio** tab to add or remove delay.
5. **Have fun!**: Explore the app and its different settings to take full advantage of it!

## 📖 How to use the python script? (old way to sync music to the Glyphs of Nothing Phones)
We made a detailed wiki page which explains the installation, usage, configuration files in detail and a troubleshooting section. You can also find out how to make new presets(not yet tho). [Just click here to see how to use **musicViz.py** as a python script](https://github.com/Aleks-Levet/better-nothing-music-visualizer/wiki/). You know what's cool? You can convert an unlimited number of files in bulk without any trouble!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Hand%20gestures/Handshake.png" alt="Handshake" width="25" height="25" /> Join our community
You want to talk or discuss? *Bugs, feature requests?* 
* [**Feel free to jump in and join us in the official discord thread in the Nothing server!**](https://discord.com/channels/930878214237200394/1434923843239280743)
* Or join our discord server! *(Click below)*

[![Discord Server](https://discord.com/api/guilds/1509496060094054531/widget.png?style=banner3)](https://discord.gg/h7DYNttc8K)


## 🏗️ Contributing
Come and help us! Contributions are very welcome!

You can:
* Open issues
* Submit pull requests
* Suggest improvements
* Experiment with new visualization ideas
* Create new presets
* Disscuss with the developpers

##  <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f512/512.gif" alt="🔒" width="25" height="25"> Privacy & Security
- **Privacy**: The app only captures the audio stream to power the visualizer. No audio content or personal media is ever stored. The screen is not recorded. It can only be transmitted if you enable the broadcast mode that syncs multiple phones toghether.
- **Analytics**: This app uses Google Analytics (Firebase) to collect anonymous usage statistics and crash reports. This data helps us understand how the app is used and fix any issues that occur, ultimately improving the experience for everyone.

**The link to the VirusTotal scan can be found here:**  
https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b?nocache=1

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Symbols/Copyright.png" alt="Copyright" width="25" height="25" /> Credits:
#### Here are the people involved in this project:
<table>
  <tr>
    <td>
      <a href="https://github.com/Aleks-Levet">
        <img src="https://github.com/Aleks-Levet.png?size=100&mask=circle" alt="aleks-levet-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Aleks Levet</b></sub>
      </a>
    </td>
    <td>
      <strong>Founder, Coordinator & Developer</strong><br/>
      Main idea and owner of the project. Main developer.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/cookiedcdev">
        <img src="https://github.com/cookiedcdev.png?size=100&mask=circle" alt="aleks-levet-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Cookie</b></sub>
      </a>
    </td>
    <td>
      <strong>Phone 3 compatibility</strong><br/>
      Made nice visualizer presets for the Phone (3)'s Glyph Matrix! Thank you!
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Nicouschulas">
        <img src="https://github.com/Nicouschulas.png?size=100&mask=circle" alt="Nicouschulas-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Nicouschulas</b></sub>
      </a>
    </td>
    <td>
      <strong>Wiki & Documentation</strong><br/>
      Readme & Wiki enhancements.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/SebiAi">
        <img src="https://github.com/SebiAi.png?size=100&mask=circle" alt="sebiai-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>SebiAi</b></sub>
      </a>
    </td>
    <td>
      <strong>Glyph Specialist</strong><br/>
      Glyphmodder and glyph related help.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/rKyzen">
        <img src="https://github.com/rKyzen.png?size=100&mask=circle" alt="rkyzen-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>rKyzen</b></sub><br/>
        <i>(Shivank Dan)</i>
      </a>
    </td>
    <td>
      <strong>Developer of the base funtionality</strong><br/>
      Implemented the real-time music streaming and began the first versions of the android app.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Earendel-lab">
        <img src="https://github.com/Earendel-lab.png?size=100&mask=circle" alt="earnendel-lab-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Earendel</b></sub>
      </a>
    </td>
    <td>
      <strong>Documentation</strong><br/>
      Readme enhancements.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Interlastic">
        <img src="https://github.com/Interlastic.png?size=100&mask=circle" alt="interlastic-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Interlastic</b></sub>
      </a>
    </td>
    <td>
      <strong>Tools</strong><br/>
      Discord Bot to try the script easily (deprecated).
    </td>
  </tr>
    <tr>
    <td>
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" alt="oliver-lebaigue-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td>
      <strong>Previous Contributor</strong>
      Enhancing the app + various nice additions.
    </td>
  </tr>
</table>

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />Star History
<a href="https://www.star-history.com/?repos=Aleks-Levet%2Fbetter-nothing-music-visualizer&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&theme=dark&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
 </picture>
</a>

# Have fun using the app!
If you read the whole readme, congrats, it takes time to write these things! If you found typos or other problems, feel free to tell us!
