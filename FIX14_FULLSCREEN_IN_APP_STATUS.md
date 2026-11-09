# ALAD FIX14 – In-app fullscreen development

## Baseline
This build starts from the user's requested FIX14 source archive
(`ALAD-Mobile-FIX14-Ordered-Voice-Queue-Source.zip`).

## Changes in this development revision
- Added an in-app `XEM TOÀN MÀN HÌNH` control to `BrowserActivity`.
- Fullscreen hides the address bar and delay/settings panel and enables immersive
  fullscreen/navigation-bar hiding.
- The same WebView instance remains attached. The page, HTML5 video element,
  WebAudio graph, frame-capture canvas and selected delay are not recreated.
- Android Back exits fullscreen first; a second Back follows the browser history.
- The existing delay control remains available outside fullscreen. Default remains
  5 seconds; existing 3–10 second presets and automatic/manual sync behavior remain.

## Important limits
- This is the in-app WebView route. It does not take over or render a separate
  external Chrome window. User should share the page URL to ALAD to open it in
  BrowserActivity.
- Delayed frame capture uses HTML canvas `drawImage(video, ...)`. Sites that block
  frame access, DRM/protected surfaces, cross-origin restrictions, or hardware
  overlays can prevent delayed frames. The existing script falls back rather than
  guaranteeing a delayed image.
- WebAudio capture may be blocked by the page or browser. The existing code falls
  back to original playback when it detects silence; synchronized delay is then
  unavailable.
- This change does not mux H.264/AAC or provide sample-accurate A/V synchronization.
- Source only: no APK build or physical vivo/Android 16 verification was completed.

## Test steps
1. Install a debug APK built from this source.
2. Start ALAD dubbing and share a supported video URL from Chrome to ALAD.
3. Start playback and verify the existing 5-second delayed image/audio behavior.
4. Tap `XEM TOÀN MÀN HÌNH`; confirm the WebView expands to the full display and
   playback continues without a page reload.
5. Press Android Back once; confirm normal browser controls return and playback
   continues.
6. Test a second website. If it uses DRM/protected video, record that separately
   as unsupported rather than assuming the fullscreen change is broken.
