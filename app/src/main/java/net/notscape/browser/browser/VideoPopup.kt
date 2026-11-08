package net.notscape.browser.browser

import net.notscape.browser.data.AppStore

/**
 * Beta.58 (5): the video pop-up — a small DOM button on
 * the PLAYING video, mirroring the old app's in-page
 * "mask". The watcher is a document-start script: it
 * finds the largest playing <video>, parks one 40px
 * button at its top-right corner, and re-positions it
 * on scroll/resize/fullscreen changes. Injection is
 * best-effort and silent on failure — a locked-down
 * page simply never shows the button, and the toolbar
 * media button stays as the permanent fallback.
 *
 * Anti-spoof (non-negotiable): a tap is honoured only
 * when the URL matches a media entry the app's own
 * detectors saw ([allowCheck]) — the page can never
 * make the app open an arbitrary URL.
 */
object VideoPopup {

    /** Per-site opt-out setting key for [isOff]. */
    fun offKey(host: String): String = "videoPopupOff:$host"

    fun isOff(store: AppStore, host: String): Boolean =
        host.isNotBlank() &&
            store.setting(offKey(host), "false") == "true"

    /**
     * The anti-spoof gate: the tapped URL must be a video
     * the detectors saw — never a page/embed kind, never
     * audio, never blob:, never an unknown URL.
     */
    fun allowCheck(
        url: String,
        known: List<DetectedMedia>
    ): DetectedMedia? {
        if (url.startsWith("blob:")) return null
        if (!url.startsWith("http://") &&
            !url.startsWith("https://")
        ) return null
        return known.firstOrNull {
            it.url == url && it.kind == "video"
        }
    }

    /**
     * Document-start watcher. Top frame only; one button
     * at a time; hidden when the video pauses, leaves the
     * viewport, or goes fullscreen; never on blob:-only
     * video (no URL to hand off); never on tiny loops
     * (160x90 floor). The native side sets
     * window.__nsVideoPopupOff on navigation for the
     * per-site opt-out; the watcher re-reads it on every
     * placement pass. Never pauses, mutes, or seeks the
     * page video; never intercepts taps outside the
     * button's own box.
     */
    const val WATCHER_JS = """
(function() {
  try {
    if (window.top !== window) return;
    if (window.__nsVideoPopupInstalled) return;
    window.__nsVideoPopupInstalled = true;
    var btn = null;
    var curUrl = null;
    function pick() {
      if (window.__nsVideoPopupOff) return null;
      if (document.fullscreenElement) return null;
      var vids = document.querySelectorAll('video');
      var best = null;
      var bestArea = 0;
      for (var i = 0; i < vids.length; i++) {
        var v = vids[i];
        try {
          if (v.paused || v.currentTime <= 0 || v.readyState < 2) continue;
          var src = v.currentSrc || v.src || '';
          if (!src || src.indexOf('blob:') === 0) continue;
          var r = v.getBoundingClientRect();
          if (r.width < 160 || r.height < 90) continue;
          if (r.bottom <= 0 || r.top >= window.innerHeight ||
              r.right <= 0 || r.left >= window.innerWidth) continue;
          var area = r.width * r.height;
          if (area > bestArea) { bestArea = area; best = {r: r, src: src}; }
        } catch (e) {}
      }
      return best;
    }
    function place() {
      try {
        var found = pick();
        if (!found) { if (btn) btn.style.display = 'none'; return; }
        if (!btn) {
          btn = document.createElement('div');
          btn.id = 'notscape-video-popup';
          btn.textContent = '\u25B6';
          btn.style.cssText = 'position:fixed;z-index:2147483647;width:40px;height:40px;' +
            'border-radius:20px;background:rgba(0,0,0,0.65);color:#fff;' +
            'font-size:18px;line-height:40px;text-align:center;' +
            'pointer-events:auto;cursor:pointer;user-select:none;' +
            '-webkit-user-select:none;touch-action:manipulation;display:none;';
          btn.addEventListener('click', function(e) {
            e.stopPropagation(); e.preventDefault();
            try { window.notscapeVideoPopup.onVideoTap(curUrl); } catch (err) {}
          });
          btn.addEventListener('contextmenu', function(e) {
            e.stopPropagation(); e.preventDefault();
            try { window.notscapeVideoPopup.onVideoLongPress(curUrl); } catch (err) {}
          });
          document.documentElement.appendChild(btn);
        }
        curUrl = found.src;
        var top = Math.max(8, found.r.top + 8);
        var left = Math.min(window.innerWidth - 48, found.r.right - 48);
        btn.style.top = top + 'px';
        btn.style.left = left + 'px';
        btn.style.display = 'block';
      } catch (e) {}
    }
    document.addEventListener('play', place, true);
    document.addEventListener('playing', place, true);
    document.addEventListener('pause', place, true);
    document.addEventListener('ended', place, true);
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    document.addEventListener('fullscreenchange', place);
    setInterval(place, 1000);
    place();
  } catch (e) {}
})()
"""
}
