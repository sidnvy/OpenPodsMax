# Wear playback gestures

For the Moto AirPods Max 2 investigation, the user explicitly chose **pause when either cup is lifted**. This changes the earlier full-removal-only policy.

- A fresh, valid fully worn `(0,0)` report must arm detection first. Starting or reconnecting while a cup is already off does not issue a pause.
- Either valid off-cup report `(1,0)`, `(0,1)` or `(1,1)` starts a 500 ms pause debounce. Duplicate reports and transitions between these off-cup shapes retain the first deadline.
- Putting both cups back on before the deadline cancels the pause. Unknown sensor values and session reset/disconnect cancel intent and require a fresh worn baseline.
- A removal issues at most one pause. Another pause requires a new fully worn report followed by removal.
- Resume behavior is unchanged: both cups must report worn for 500 ms, and the app must still own a confirmed pause of the same player and track. Lifting or replacing only one cup does not trigger resume.

Exact approved identity, current AAP session, media route, normal audio mode, active playback and manual-pause protections remain in force. No media control is authorized from an unknown sensor state.

The regression includes a recorded asymmetric Moto transition where one cup reported removed but no `(1,1)` report arrived before reinsertion. Hardware acceptance should cover each cup separately, quick adjustments, full removal, reconnect while off, and background pause/resume. This preference change does not establish the cause of every earlier intermittent failure or resolve Android 17 player-audio authorization restrictions.
