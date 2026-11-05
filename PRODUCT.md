# OpenPodsMax

Android companion and Linux desktop controls for AirPods Max, including Max 2.

## Linux Quick Toggle

Mode: Operate. The user changes listening mode and checks headphone battery
without leaving the existing right-side Quick Toggle. Expand the inline section
for conversation awareness, ear detection policy and native audio handoff.
Use the installed ii Quickshell theme, typography and widgets. No separate page
or application window. No network coordinator or audio relay.

Only observed headphone state may be presented as confirmed. Hardware setting
changes remain pending until readback; unavailable values stay unknown.
Native switching uses the same AAP session as headphone controls. The current
Moto experiment needs temporary root for Apple identity and loses that identity
after reboot; Linux controls must not imply this limitation is solved.

Platform: existing Android application and Linux Qt Quick / Quickshell desktop.
Preserve the desktop's system-adaptive theme and existing generic Bluetooth UI.
