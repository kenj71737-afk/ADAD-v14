# FIX14 – Preserve ALAD delay when website requests native fullscreen

## Root cause
WebChromeClient native custom fullscreen moves the HTML video out of the WebView DOM.
ALAD's delayed video-frame canvas and WebAudio DelayNode are attached to that DOM.
The earlier handler accepted the detached native view, while the page script disabled
the delayed canvas/audio path when it detected fullscreen. Thus the AUTO/manual HUD
could still show a delay value while the picture was no longer delayed.

## Changes
- Reject the website's native detached fullscreen callback, keeping the HTML video
  inside the same WebView/DOM.
- Show a Vietnamese explanation directing the user to ALAD's own fullscreen button.
- Remove the page-script branch that explicitly hid the delayed canvas and reset the
  WebAudio delay to zero upon DOM fullscreen detection.
- ALAD fullscreen still hides app toolbar/settings and uses immersive full-phone
  mode; the same WebView, frame buffer, and audio DelayNode stay attached.
- AUTO/manual delay controls remain available in ALAD fullscreen HUD.

## Important limitation
This preserves the delay pipeline by refusing the website's native fullscreen mode.
It does NOT guarantee that every website's player can be made edge-to-edge fullscreen
inside the page. Use ALAD's fullscreen button. On websites whose player stays small
inside its page layout, it may not fill the entire display; a site-specific CSS/player
integration would be needed. DRM/CORS/hardware protected surfaces may still block
frame capture. No guarantee of delay for unsupported sources.

## Verification
ZIP CRC checked. No Gradle build/APK build or physical vivo test completed.
