# FIX14 – Full-phone fullscreen development (revision 2)

Base: user-selected FIX14 source, continued from the prior FIX14 in-app fullscreen branch.

## Implemented
- The ALAD fullscreen button hides the browser toolbar and delay/settings panel,
  then applies Android immersive fullscreen flags (status bar and navigation bar
  hidden). The same WebView instance remains attached; no reload is triggered.
- Added WebChromeClient `onShowCustomView` / `onHideCustomView` handling for sites
  that request native HTML5 video fullscreen. The custom video view is attached
  to a black, match-parent container and system bars are hidden.
- Android Back exits the site's custom video fullscreen first; otherwise it exits
  ALAD's app fullscreen; otherwise it navigates browser history.
- On window focus return, immersive flags are restored when either fullscreen mode
  remains active.

## Important playback/delay behavior
- ALAD's own `XEM TOÀN MÀN HÌNH` button expands the existing WebView to the full
  phone display. For sites compatible with the existing canvas/WebAudio delay
  pipeline, the existing selected delay (default 5 seconds) can continue.
- A website's own native HTML5 fullscreen request is handled separately by
  WebChromeClient. The existing page script deliberately disables its canvas
  frame-delay overlay when the HTML video enters native fullscreen, to avoid a
  black/incorrect frame and A/V mismatch. Therefore native site fullscreen is
  full-screen, but the 5-second delayed picture is NOT guaranteed in that mode.
  Prefer ALAD's own fullscreen button when delayed playback is required.
- DRM/protected video, CORS restrictions, and hardware overlay surfaces can block
  frame extraction. This implementation cannot guarantee delayed video for those
  sources. If capture is blocked, existing fallback behavior may play original
  video without delay rather than showing a false promise of delayed playback.
- This is not external Chrome window capture. The video must be opened in ALAD's
  BrowserActivity (for example, share the page URL from Chrome to ALAD).

## Verification
- ZIP structure and CRC tested.
- No APK build or physical vivo / Android 16 test completed in this environment.
- The Kotlin changes require a Gradle build and device test before claiming runtime
  success.
