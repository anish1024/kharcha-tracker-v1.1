/* Inline SVG charts — no external dependencies.
   Consumes ready-made series from app.js; renders responsive SVG strings. */
"use strict";

const Charts = (() => {
  const PALETTE = [
    "#4f46e5", "#0ea5e9", "#14b8a6", "#f59e0b", "#f43f5e",
    "#8b5cf6", "#84cc16", "#ec4899", "#64748b", "#0891b2"
  ];

  function color(index) {
    return PALETTE[index % PALETTE.length];
  }

  /* E4: stable label -> palette colour (deterministic hash of the label).
     The same category therefore gets the same colour in the pie and the
     stacked bar, regardless of ordering. */
  function colorFor(label) {
    const key = String(label == null ? "" : label).trim().toLowerCase();
    let h = 0;
    for (let i = 0; i < key.length; i++) {
      h = (h * 31 + key.charCodeAt(i)) >>> 0;
    }
    return PALETTE[h % PALETTE.length];
  }

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
  }

  function niceMax(value) {
    if (!(value > 0)) return 1;
    const mag = Math.pow(10, Math.floor(Math.log10(value)));
    const norm = value / mag;
    const step = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10;
    return step * mag;
  }

  /* series: [{label, value}] — vertical bars, one colour, value on hover. */
  function barChart(series) {
    const W = 640, H = 210;
    const padL = 10, padR = 10, padT = 18, padB = 30;
    const innerW = W - padL - padR;
    const innerH = H - padT - padB;
    const n = series.length || 1;
    const slot = innerW / n;
    const barW = Math.max(6, Math.min(slot * 0.66, 46));
    const max = niceMax(Math.max.apply(null, series.map((s) => s.value).concat([0])));

    let out = '<svg class="chart-svg" viewBox="0 0 ' + W + " " + H + '" ' +
      'preserveAspectRatio="xMidYMid meet" role="img" aria-label="Bar chart">';

    const gridLines = 3;
    for (let g = 1; g <= gridLines; g++) {
      const y = padT + innerH - (innerH * g) / gridLines;
      out += '<line x1="' + padL + '" y1="' + y + '" x2="' + (W - padR) + '" y2="' + y +
        '" stroke="#e5e7f0" stroke-width="1"/>';
    }
    out += '<text x="' + padL + '" y="' + (padT - 6) +
      '" font-size="10" fill="#9498b3">' + esc(String(max)) + "</text>";

    series.forEach((s, i) => {
      const x = padL + slot * i + (slot - barW) / 2;
      const h = max > 0 ? (s.value / max) * innerH : 0;
      const y = padT + innerH - h;
      if (h > 0) {
        out += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) +
          '" width="' + barW.toFixed(1) + '" height="' + h.toFixed(1) +
          '" rx="3" fill="#4f46e5"><title>' + esc(s.label) + ": " + esc(s.valueText) +
          "</title></rect>";
      }
      out += '<text x="' + (x + barW / 2).toFixed(1) + '" y="' + (H - padB + 15) +
        '" font-size="10" fill="#5f6480" text-anchor="middle">' + esc(s.label) + "</text>";
    });

    out += "</svg>";
    return out;
  }

  /* series: [{label, value, valueText, parts: [{label, value, valueText,
     color}]}] — stacked vertical bars; each part keeps its own colour
     (usually Charts.colorFor(category)). Gridlines + y-max from the bucket
     totals, x labels per bucket, per-part hover titles. */
  function stackedBarChart(series) {
    const W = 640, H = 210;
    const padL = 10, padR = 10, padT = 18, padB = 30;
    const innerW = W - padL - padR;
    const innerH = H - padT - padB;
    const n = series.length || 1;
    const slot = innerW / n;
    const barW = Math.max(6, Math.min(slot * 0.66, 46));
    const max = niceMax(
      Math.max.apply(null, series.map((s) => s.value).concat([0]))
    );

    let out = '<svg class="chart-svg" viewBox="0 0 ' + W + " " + H + '" ' +
      'preserveAspectRatio="xMidYMid meet" role="img" aria-label="Stacked bar chart">';

    const gridLines = 3;
    for (let g = 1; g <= gridLines; g++) {
      const y = padT + innerH - (innerH * g) / gridLines;
      out += '<line x1="' + padL + '" y1="' + y + '" x2="' + (W - padR) + '" y2="' + y +
        '" stroke="#e5e7f0" stroke-width="1"/>';
    }
    out += '<text x="' + padL + '" y="' + (padT - 6) +
      '" font-size="10" fill="#9498b3">' + esc(String(max)) + "</text>";

    series.forEach((s, i) => {
      const x = padL + slot * i + (slot - barW) / 2;
      // Stack parts bottom-up: the first part sits on the axis.
      let cursorY = padT + innerH;
      (s.parts || []).forEach((p) => {
        const h = max > 0 ? (p.value / max) * innerH : 0;
        if (h <= 0) return;
        cursorY -= h;
        out += '<rect x="' + x.toFixed(1) + '" y="' + cursorY.toFixed(1) +
          '" width="' + barW.toFixed(1) + '" height="' + h.toFixed(1) +
          '" fill="' + esc(p.color || "#4f46e5") + '"><title>' +
          esc(s.label) + " — " + esc(p.label) + ": " + esc(p.valueText) +
          "</title></rect>";
      });
      out += '<text x="' + (x + barW / 2).toFixed(1) + '" y="' + (H - padB + 15) +
        '" font-size="10" fill="#5f6480" text-anchor="middle">' + esc(s.label) + "</text>";
    });

    out += "</svg>";
    return out;
  }

  /* slices: [{label, value, valueText}] — solid pie + built-in legend rows.
     Slice colours come from colorFor(label) so a category keeps one stable
     colour across charts (E4). */
  function pieChart(slices) {
    const total = slices.reduce((sum, s) => sum + s.value, 0);
    const W = 200, H = 200, cx = 100, cy = 100, r = 88;
    let out = '<svg class="chart-svg chart-svg--pie" viewBox="0 0 ' + W + " " + H + '" ' +
      'preserveAspectRatio="xMidYMid meet" role="img" aria-label="Category pie chart">';

    if (total <= 0) {
      out += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="#e5e7f0"/>';
      out += "</svg>";
      return out;
    }

    if (slices.length === 1) {
      out += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="' +
        colorFor(slices[0].label) + '"><title>' + esc(slices[0].label) + ": " +
        esc(slices[0].valueText || "") + "</title></circle></svg>";
      return out;
    }

    let angle = -Math.PI / 2;
    slices.forEach((s) => {
      const frac = s.value / total;
      const sweep = frac * Math.PI * 2;
      if (frac >= 0.9999) {
        out += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="' +
          colorFor(s.label) + '"><title>' + esc(s.label) + ": " + esc(s.valueText || "") +
          "</title></circle>";
      } else {
        const x1 = cx + r * Math.cos(angle);
        const y1 = cy + r * Math.sin(angle);
        const a2 = angle + sweep;
        const x2 = cx + r * Math.cos(a2);
        const y2 = cy + r * Math.sin(a2);
        const large = sweep > Math.PI ? 1 : 0;
        out += '<path d="M ' + cx + " " + cy + " L " + x1.toFixed(2) + " " + y1.toFixed(2) +
          " A " + r + " " + r + " 0 " + large + " 1 " + x2.toFixed(2) + " " + y2.toFixed(2) +
          ' Z" fill="' + colorFor(s.label) + '"><title>' + esc(s.label) + ": " +
          esc(s.valueText || "") + "</title></path>";
      }
      angle += sweep;
    });

    out += "</svg>";
    return out;
  }

  return {
    barChart: barChart,
    stackedBarChart: stackedBarChart,
    pieChart: pieChart,
    color: color,
    colorFor: colorFor
  };
})();
