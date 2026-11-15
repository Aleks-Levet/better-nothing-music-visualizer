/*
 * main.js — optional progressive enhancement.
 *
 * The page is fully static. Scroll reveals and the phone flip are pure CSS
 * (scroll-driven animations). This file only adds a small fallback for
 * browsers that don't support them, using IntersectionObserver.
 */
(function () {
  'use strict';

  var supportsScrollTimeline = window.CSS && CSS.supports && CSS.supports('animation-timeline: view()');
  if (supportsScrollTimeline || !('IntersectionObserver' in window)) return;

  document.documentElement.classList.add('no-scroll-timeline');

  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-visible');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.15 });

  document.querySelectorAll('.reveal').forEach(function (el) { observer.observe(el); });
})();
