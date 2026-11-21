(() => {
  'use strict';

  let edgesCache = null;

  function edges() {
    if (edgesCache) return edgesCache;

    const bands = [[30, 250, 8], [250, 4000, 12], [4000, 16000, 16]];
    const out = [];
    for (const [low, high, count] of bands) {
      const ratio = high / low;
      for (let i = 0; i < count; i++) {
        out.push([
          low * Math.pow(ratio, i / count),
          low * Math.pow(ratio, (i + 1) / count)
        ]);
      }
    }
    edgesCache = out;
    return out;
  }

  function readAnalyser(audio, peak, raw) {
    audio.analyser.getByteFrequencyData(audio.data);
    const binHz = audio.ctx.sampleRate / audio.analyser.fftSize;
    const zones = edges();

    for (let z = 0; z < 36; z++) {
      const lo = Math.floor(zones[z][0] / binHz);
      let hi = Math.ceil(zones[z][1] / binHz);
      if (hi <= lo) hi = lo + 1;

      let magnitude = 0;
      for (let i = lo; i < hi && i < audio.data.length; i++) {
        if (audio.data[i] > magnitude) magnitude = audio.data[i];
      }

      let value = magnitude / 255;
      let previousPeak = peak[z] * 0.9985;
      if (previousPeak < 0.3) previousPeak = 0.3;
      if (value > previousPeak) previousPeak = value;
      peak[z] = previousPeak;

      value = (value / previousPeak - 0.45) / 0.55;
      raw[z] = value < 0 ? 0 : (value > 1 ? 1 : value);
    }
  }

  function synth(raw, time) {
    const sixteenth = 0.15;
    if (!(time > 0)) time = 0;

    const position = (time / sixteenth) % 16;
    const bar = Math.floor(time / 2.4) % 4;
    const hit = (steps, decay) => {
      let maximum = 0;
      for (const step of steps) {
        let age = position - step;
        if (age < 0) age += 16;
        const envelope = Math.exp(-(age * sixteenth) / decay);
        if (envelope > maximum) maximum = envelope;
      }
      return maximum;
    };

    const kick = hit([0, 6, 10], 0.14);
    const bass = hit([0, 6, 10], 0.3);
    const snare = hit([4, 12], 0.12);
    const hat = hit([0, 2, 4, 6, 8, 10, 12, 14], 0.045);
    const open = hit([14], 0.14);
    const sequence = [0, 2, 1, 3, 4, 3, 1, 2];
    const arpStep = Math.floor(position / 2) % 8;
    const arpEnvelope = Math.exp(-((position - arpStep * 2) * sixteenth) / 0.16);
    const arpZone = 12 + ((sequence[arpStep] * 2 + bar * 3) % 8);
    const bassZone = 3 + (bar % 3);
    const spark = Math.floor(time / (sixteenth * 2));

    for (let z = 0; z < 36; z++) {
      let value = 0;
      if (z < 8) {
        const kickDistance = (z - 1.5) / 2.2;
        value = kick * Math.exp(-kickDistance * kickDistance);
        const bassDistance = (z - bassZone) / 1.3;
        value = Math.max(value, 0.85 * bass * Math.exp(-bassDistance * bassDistance));
      } else if (z < 20) {
        const snareDistance = (z - 13) / 3;
        value = 0.9 * snare * Math.exp(-snareDistance * snareDistance);
        const arpDistance = (z - arpZone) / 0.9;
        value = Math.max(value, 0.95 * arpEnvelope * Math.exp(-arpDistance * arpDistance));
        value = Math.max(value, 0.2 + 0.1 * Math.sin(time * 1.3 + z));
      } else {
        const variation = 0.5 + 0.5 * Math.sin(z * 12.9898 + spark * 4.1);
        value = hat * (0.4 + 0.45 * variation);
        value = Math.max(value, open * (0.5 + 0.35 * variation));
        const noiseDistance = (z - 22) / 4;
        value = Math.max(value, 0.6 * snare * Math.exp(-noiseDistance * noiseDistance));
      }
      raw[z] = value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    return {
      kick,
      bass,
      snare,
      hat: Math.max(hat, open),
      arp: arpEnvelope
    };
  }

  window.BNMVAudioProcessing = { edges, readAnalyser, synth };
})();
