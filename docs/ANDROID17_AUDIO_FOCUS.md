# Android 17 wear pause/resume: transient audio focus

## Evidence / root cause

Fold8 SM-F9710 (Android API 37), OpenPodsMax 0.2.11, YouTube Music
(target SDK 37), 2026-11-22:

- 20:18:32.250 WEAR_PAUSE_SESSION_REQUESTED; YouTube Music ends foreground service.
- 20:18:35.285 WEAR_RESUME_REQUESTED from MediaController.play().
- 20:18:35.293 YouTube Music restarts FGS from MEDIA_SESSION_CALLBACK,
  without while-in-use (WIU) capability.
- 20:18:37.271 AudioHardening background playback muted, level: full.
- The player remains PLAYING, A2DP stays connected, but its AudioTrack
  shows mutedState:opControlAudio.

Official Android 17 behavior: https://developer.android.com/about/versions/17/changes/bg-audio
Audio focus behavior: https://developer.android.com/media/optimize/audio-focus

## Design

For Pause when removed and Resume when worn enabled, interpret a
valid full-removal event as a temporary interruption. Request
AUDIOFOCUS_GAIN_TRANSIENT from OpenPodsMax's already-running connected-device
foreground service, and release it only after stable full reinsertion.

The media app owns its MediaSession/foreground-service lifecycle. We do not
send a cross-app transport pause() or play(). Existing AAP generation,
approved bond, exact route, normal audio mode and music-active preconditions
remain. If focus is denied, do not fall back to the old pause/resume path.
A headset switch, AAP reset, focus loss or service destruction clears ownership.

The preference key resume_when_worn_android is retained to preserve user
configuration, but notification-listener permission is no longer necessary
and the old session controller is deleted. With auto-resume off, the existing
user-selected pause-only behavior remains.

## Verification

- Isolated ordinary Android app on the same Fold8:
  AUDIOFOCUS_GAIN_TRANSIENT while YouTube Music is playing -> YouTube Music
  pauses, retains FGS -> releasing focus after six seconds -> resumes,
  AudioTrack mutedState:none, no new AudioHardening record.
- Existing JVM regressions and WearFocusPolicy tests pass.
- Compile, Android lint and packaging verified for the modified app.
- On-device wear cycle using the new release APK not yet accepted.

## Compatibility and safety limits

Focus is a framework contract, not guaranteed cross-app automatic play.
YouTube/Spotify/VLC may resume differently. Regular YouTube without a
background-play entitlement can stop playback when no longer visible.
Loss of AAP session/service while holding focus must release the claim;
whether the media app resumes on another route is platform/app-dependent.
Live acceptance must include disconnect and manual-pause scenarios.
