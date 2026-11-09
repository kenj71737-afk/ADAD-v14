# FIX14 – Video-only fullscreen (keep ALAD delay)

## User requirement
When pressing ALAD's fullscreen button, enlarge only the currently playing video,
like YouTube fullscreen. Do not show the rest of the page (recommended videos,
episode choices, comments, etc.) around or below it. Keep AUTO/manual delay active.

## Changes
- The ALAD fullscreen button now asks the page to find the currently playing HTML
  video and its player container.
- The player container is styled edge-to-edge (100vw x 100vh) with black background.
- Page siblings along the ancestor path are hidden while fullscreen is active, so
  recommendation cards and other page content are not left below the video.
- The original video element is NOT moved/recreated. Its existing WebAudio graph and
  ALAD delayed-frame canvas remain attached. The canvas is explicitly excluded from
  sibling hiding.
- On exit, all modified inline styles are restored. The page is not reloaded.
- If no active HTML video is found, ALAD exits fullscreen and displays a message.
- Existing AUTO and THỦ CÔNG delay HUD remains available.

## Limits / verification
- Works only for accessible HTML5 video elements and player DOM structures. Some
  sites use DRM, protected video surfaces, cross-origin restrictions, shadow DOM,
  or unusual player layouts; video-only cropping and delayed frames cannot be
  guaranteed for those sites.
- Generic player-container detection may need site-specific tuning.
- The WebAudio/canvas delay is the existing implementation; this change does not
  provide sample-accurate AV sync.
- ZIP integrity checked. No Gradle/APK build and no physical vivo test completed.
