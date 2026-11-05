---
name: OpenPodsMax Linux Quick Toggle
description: Native headphone controls within the existing ii Quickshell sidebar.
spacing:
  section: "8px"
  mode-grid: "6px"
  header: "10px"
  header-copy: "2px"
  expanded-horizontal-inset: "12px"
  expanded-top-inset: "4px"
  expanded-bottom-inset: "8px"
components:
  headphone-header:
    height: "68px"
  listening-mode:
    height: "36px"
  ear-pause-selector:
    height: "34px"
---

# Design System: OpenPodsMax Linux Quick Toggle

## Overview

**Creative North Star: "Headphone controls beside the desktop's other quick toggles."**

This document covers the native Linux Qt Quick / Quickshell integration only.
Its visual authority is the installed ii shell: Material theme roles, configured
fonts, rounded widgets and ripple interactions. It does not define the Android
application's design system. Product scope is recorded in [PRODUCT.md](../PRODUCT.md).

The compact header identifies the headphones, then shows the observed listening
mode, audio source and one headphone battery value. Expanding reveals less
frequent controls in place, keeping the existing Quick Toggle as the working
surface. Feedback describes observed state, waiting and failure without implying
that a click has already changed the headphones.

**Key Characteristics:**

- Native ii widgets and system-adaptive theme roles.
- Compact identity and status before detailed controls.
- In-place expansion with bounded scrolling.
- Readback-driven selection and visible keyboard focus.

Implementation references are [AirPodsQuickControls.qml](quickshell/AirPodsQuickControls.qml),
[OpenPodsMax.qml](quickshell/OpenPodsMax.qml) and [install.py](install.py).
Theme references below are relative to the installed ii shell root.

## Colors

The palette follows the shell's current Material theme. The frontmatter deliberately
contains no fixed color values: the integration binds to `Appearance` roles,
whose values depend on the user's theme and transparency configuration.

### Primary

- `Appearance.colors.colPrimary`: selected listening-mode background and keyboard
  focus outline.
- `Appearance.colors.colOnPrimary`: selected listening-mode text and its inset
  focus outline.

### Neutral

- `Appearance.colors.colLayer1`: compact header surface.
- `Appearance.colors.colOnSurface`: unselected listening-mode text.
- `Appearance.colors.colSubtext`: observed mode/source, section label and waiting
  feedback.
- `StyledText` defaults: ordinary labels, device identity and battery text inherit
  the shell's foreground role.

Errors use `Appearance.colors.colError`. Hover, active and disabled colors come
from the inherited widgets, especially `modules/common/widgets/RippleButton.qml`.

**The Theme Ownership Rule.** Bind to the shell's semantic roles; do not freeze a
palette sampled from one screenshot into this integration.

## Typography

Use `StyledText`, which inherits the configured ii main font and native text
rendering. The device name adds `font.bold`; secondary status, listening labels
and feedback use `Appearance.font.pixelSize.small`. Material Symbols Rounded
supplies the expand/collapse chevron. Font families and theme sizes remain owned
by `modules/common/Appearance.qml` and the shell configuration.

Device identity and mode/source each occupy one line and elide at the right edge.
Waiting and error feedback wraps. Keep these roles distinct through weight and
the inherited foreground/subtext contrast rather than adding a display typeface.

## Layout

The installer inserts the headphone section before `CenterWidgetGroup` in
`modules/ii/sidebarRight/SidebarRightContent.qml`. Generic connectivity controls
remain part of the existing sidebar. No separate application window or navigation
surface is introduced.

The header fills the available width. It contains a proportionally fitted
headphone asset (44 by 50 logical pixels), a flexible two-line text column, one
battery value and a chevron. Layout spacing and component dimensions are recorded
in the frontmatter; Qt Quick dimensions are logical pixels rather than physical
screenshot pixels.

Expanded content uses an equal-width two-column listening-mode grid, switching
to one column below 320 logical pixels of available scroll width. Labeled policy
rows, the handoff action and feedback follow. A clipped `ScrollView` fills the
width and applies the horizontal, top and bottom insets in the frontmatter.
Its content column explicitly uses `expandedScroll.availableWidth`; flexible
labels and mode buttons have zero minimum width so text cannot widen the panel.
Policy labels wrap, while mode labels elide. The selector's preferred width is
the lesser of 154 logical pixels and 46% of available scroll width.

The scroll view disables horizontal scrolling and limits its preferred height
to the lesser of the content height including vertical padding and
`controlsMaxHeight`. The installed sidebar supplies 45% of
its background height; the component's standalone default is 400 logical pixels.
This is a height constraint, not a web breakpoint system.

While expanded, the installer hides `CenterWidgetGroup` and `BottomWidgetGroup`
and adds a flexible spacer. Collapsing restores those groups. Preserve this
relationship when modifying the integration so expanded controls remain usable
within the sidebar's height.

## Elevation & Depth

The headphone section adds no shadow layer. Tonal surfaces, selection fills,
rounded shapes and the parent sidebar provide hierarchy. Keep the inherited
Material ripple and hover behavior from `RippleButton`; there is no independent
OpenPodsMax motion theme or floating control panel.

## Shapes

The header and mode buttons use the shell's rounded `RippleButton` language and
`Appearance.rounding.small`. Switches, selector and action button retain their
native ii silhouettes through `StyledSwitch`, `StyledComboBox` and `DialogButton`.
Do not replace these with mobile-platform lookalikes.

Keyboard focus on the header is a two-pixel outline. Mode buttons use the same
outline width inset by two logical pixels; its color switches to the on-primary
role when the mode is selected. These outlines are state indicators rather than
permanent decorative borders.

## Components

### Headphone header

A `RippleButton` toggles inline expansion. It is visible only for a connected
headphone device; losing visibility collapses the section. Use the existing
headphone asset, one battery percentage and an expand/collapse chevron. Unknown
battery is an em dash. Only a known listening mode prefixes the source label.
Source labels distinguish this computer, another device, a call, idle and
unavailable state. Accessible text identifies the device, source and expansion
action.

### Listening modes

Four defined choices are noise cancellation, transparency, adaptive and off.
Adaptive is hidden when unsupported. Mode controls require ANC support and are
disabled while a request is busy. A selected fill requires known mode readback;
accessible radio-button semantics mirror that state. Keep the four choices in
the same responsive control group rather than introducing tabs or a new page.

### Policy rows

Conversation awareness and automatic switching use the inherited switch widget.
Conversation awareness additionally requires adaptive support and known state.
The ear-removal policy uses a compact selector with Off, Either earcup and Both
earcups. Labels stay on the left and controls on the right. All are disabled
while another request is busy.

### Handoff action

The full-width `DialogButton` reads “Use on this computer”. It is unavailable
while busy, when automatic switching is disabled, during a call or when the
observed source already is this computer. Source feedback remains an observation;
this document makes no claim about hardware acceptance or audio verification.

### Pending and error feedback

Requests retain observed controls until the expected property arrives through
the status watcher. “Waiting for headphones…” appears while busy; errors use the
error role and accessible alert semantics. A six-second readback timeout clears
the pending property and reports missing confirmation. Command failure and
disconnect also clear the pending request and expose their error. The command
process can keep the controls busy until it exits.

**The Readback Rule.** A requested value is not a confirmed value. Preserve known,
unknown, pending and error distinctions when extending any control.

## Do's and Don'ts

### Do:

- **Do** reuse ii theme roles, typography and native widgets.
- **Do** keep identification and observed status in the compact header.
- **Do** preserve bounded expansion, scrolling and lower-widget visibility bindings.
- **Do** keep focus outlines, accessible names and readback-driven selection.

### Don't:

- **Don't** create a separate window for this Quick Toggle workflow.
- **Don't** hardcode the current theme's colors, font family or corner scale.
- **Don't** introduce iOS or Android styling into this native desktop surface.
- **Don't** display requested settings or unknown headphone values as confirmed.
