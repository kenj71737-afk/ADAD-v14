# FIX14 Fullscreen – Automatic and Manual Delay Controls

Base: FIX14 in-app fullscreen branch.

## Added in this revision
- Fullscreen HUD remains visible over ALAD's own fullscreen WebView mode.
- HUD exposes `AUTO` and `THỦ CÔNG` modes while video is fullscreen.
- AUTO mode reuses the existing estimated dubbing latency flow and selected delay.
  The periodic sync poll continues to update the active WebView delay while the
  Activity is resumed.
- MANUAL mode freezes automatic updates and exposes minus/plus controls in 0.5s
  increments, bounded to the existing 3–10 second delay range.
- Delay status is displayed in the fullscreen HUD.
- The original browser controls and seek bar remain available after exiting
  fullscreen; the existing selected delay and mode are retained.
- The same WebView and its media/audio graph remain alive during fullscreen.

## Caveats
- AUTO is based on the existing estimated dubbing delay measurement, not a
  sample-accurate audiovisual synchronizer.
- MANUAL controls the existing WebAudio DelayNode and compatible canvas frame
  buffer. Sites that block audio/frame extraction or use DRM/protected surfaces
  cannot be guaranteed to delay.
- This code does not add a new external Chrome window capture path.
- ZIP integrity was checked. No APK build or physical vivo/Android 16 test was
  completed; runtime behavior remains unverified.
