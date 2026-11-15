# BNMV site (split version)

Static, visual-only. No audio, no real-time simulation, no canvas.

```
index.html          markup only
css/
  base.css          page defaults (html/body, focus, root font)
  layout.css        sizing/spacing/typography per element (generated from the old inline styles)
  components.css    hover states, masks, gradients
  animations.css    every CSS animation, in one place
js/
  main.js           tiny optional fallback for scroll-reveal in older browsers
assets/
  icons/            logo variants, marquee mark, favicon, the 6 feature icons
  models/           logo-rings.svg (hero disc + rings), phone-back.svg (camera + glyph lights)
  charts/           waveform.svg, spectrum.svg (single-frame snapshots of the old live canvases)
```

Open `index.html` directly, or serve the folder with any static server.
