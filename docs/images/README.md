# Documentation capture

`pixel-playback.png` is an unedited ADB screenshot captured on a Pixel 10 Pro XL on 2026-10-19, showing the private RC1 control page. The headset was disconnected. It documents the interface, not a fresh hardware acceptance test.

The image was visually checked for personal device names, addresses, serial numbers, notification contents and excluded product renders. None are visible. It retains the Android status and navigation bars. No setting was changed for this capture.

The public-tree guard pins this reviewed image by SHA-256. App source and the documentation capture are provided under the repository license; Android system UI and third-party status icons remain their respective owners' material.

## Native system controls

`pixel-system-controls.png` is the original, unedited Pixel device-details capture from the earlier private integration acceptance session, after metadata registration was restored. It shows the actual Android Settings page, not a mockup or app-rendered replacement. The owner-provided headset display name is visible; no device address, serial number, account email or notification contents are shown.

This historical capture includes the small product icon used by that private test build. It is included only to document the system interface; the product artwork is not supplied as an app resource in public builds. Android UI and product imagery remain their respective owners' material and are not relicensed as original GPL artwork. The current release uses basic vector artwork.

On the later screenshot visit, the system entry had reverted to Android's default controls, illustrating the documented registration-persistence limitation. This image shows the registered result, not the phone's current state.
