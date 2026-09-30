#line 1 "C:\\Users\\user\\Documents\\NeuroVibe\\NEUROSENSE_PRODUCT_AUDIT_AND_MASTER_PROMPT.md"
# NeuroSense Product Audit and Master Improvement Prompt

## How to use this document

Copy the complete prompt below into an AI coding agent working from the `NeuroVibe` repository. The prompt is based on a source-code audit and an on-device scan of the installed app on a Samsung SM-T220.

---

## Master prompt

You are a senior Android product engineer, product designer, UX writer, accessibility specialist, and QA lead. Work directly in the existing NeuroVibe repository and turn the native Android application **NeuroSense** into a polished, reliable, production-quality companion product for the NeuroVibe Bluetooth wellness device.

Do not produce only a concept, mockup, or written recommendation. Inspect the existing implementation, preserve the working hardware protocol and safety behavior, implement the improvements, build the APK, run lint and tests, and visually verify the result on representative phone and tablet sizes.

### Product context

- Product name: **NeuroSense**
- Hardware name: **NeuroVibe**
- Platform: native Android, Java
- Current minimum Android version: Android 8.0 / API 26
- Current compile and target SDK: 35
- Architecture: one `MainActivity` with programmatically constructed views plus three custom views
- Communication: Bluetooth Low Energy only
- Privacy model: no login, no cloud, no database, no analytics, and no internet dependency
- Local storage: Android `SharedPreferences` for onboarding and one custom preset
- Control range: integer values from `0` through `230`
- Hardware behavior: the same value controls PWM frequency and proportional motor level
- Both motors are controlled together
- BLE service UUID: `7b3a0001-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- BLE command characteristic UUID: `7b3a0002-6f3b-4b5d-9a2e-0f6d4c2b1a00`
- BLE command payload: one unsigned byte from `0` through `230`
- Safety rule: `0` must always stop both motors
- Safety rule: motors must stop on disconnect, communication failure, and when the app moves to the background
- Product positioning: wellness control product, not a medical device; do not add diagnostic or treatment claims

### Existing file map

- `NeuroSense/app/src/main/java/com/neurosense/app/MainActivity.java` — UI shell, pages, dialogs, state, BLE scanning and GATT control
- `NeuroSense/app/src/main/java/com/neurosense/app/CapsuleSliderView.java` — accessible custom `0–230 Hz` control
- `NeuroSense/app/src/main/java/com/neurosense/app/PulseRingView.java` — active-output visualization
- `NeuroSense/app/src/main/java/com/neurosense/app/PresetLevelBarView.java` — preset level visualization
- `NeuroSense/app/src/main/res/drawable/` — icons, slider resources, backgrounds
- `NeuroSense/app/src/main/res/drawable-nodpi/neurovibe_product.png` — product image
- `NeuroSense/app/src/main/res/values/` — colors, styles, and strings
- `NeuroSense/app/src/main/AndroidManifest.xml` — Bluetooth, vibration, and location compatibility permissions
- `NeuroVibe.ino` — ESP32-C3 firmware; preserve its protocol unless a coordinated protocol change is explicitly required

## Complete inventory of the current app

### 1. First-run onboarding

The first launch currently contains:

- NeuroSense brain-circuit logo
- Eyebrow label: “NEUROVIBE COMPANION”
- Headline: “Control with confidence”
- Private and precise control description
- NeuroVibe product image
- Wellness/non-medical disclaimer
- “Set up NeuroVibe” primary action
- Privacy line: local, no account, Bluetooth only
- `welcome_seen` local preference that skips onboarding on later launches

### 2. Persistent app shell

The main shell currently contains:

- NeuroSense logo and wordmark
- Global connection-status pill
- Status values: disconnected, searching, connecting, connected, and error
- Settings icon
- Four-item bottom navigation: Device, Control, Presets, Help
- White/lavender visual language with purple primary actions, green success, amber caution, and red stop/error treatment

### 3. Device page

The Device page currently contains:

- “DEVICE” section eyebrow
- “Your NeuroVibe” title
- Dynamic connected/disconnected supporting text
- Product image hero card
- Device name and state pill
- Connect, searching, connecting, disconnect states
- Bluetooth icon and primary connection action
- Signal-strength card using RSSI captured during scanning
- “BLE Direct” connection card
- Inline device-not-found state
- Inline communication-error state
- Information that system Bluetooth pairing is not required
- Permission request and rationale flow
- 12-second filtered BLE scan
- Direct GATT connection and service discovery
- Firmware compatibility validation using the expected characteristic

### 4. Control page

The Control page currently contains:

- “LIVE CONTROL” section eyebrow
- “Set the intensity” title
- Explanation that changes commit when the control is released
- State chip for disconnected, ready, or active output
- Circular frequency display
- Current frequency in Hz
- Both-motors label
- Calculated percentage level
- Animated pulse rings while output is active
- Custom capsule slider from `0` through `230`
- Drag preview and value bubble
- Haptic feedback at 10 Hz intervals and endpoints
- BLE commit on release to reduce redundant writes
- Keyboard and accessibility increment/decrement support
- Accessibility range semantics matching a standard SeekBar
- `−10` and `+10` fine-adjustment controls
- Disabled state when no NeuroVibe is connected
- Stop-output button
- Gradual-use safety guidance

### 5. Presets page

The Presets page currently contains:

- “PRESET PROGRAMS” section eyebrow
- “Choose your starting point” title
- Explicit preview-before-start messaging
- Gentle preset: `60 Hz`, approximately `26%`
- Balanced preset: `115 Hz`, `50%`
- Strong preset: `180 Hz`, approximately `78%`
- Visual level indicator for every built-in preset
- Selected-preset state
- Custom preset stored locally, default `120 Hz`
- Custom preset editor using a standard SeekBar
- Preview panel after selecting a preset
- Start action enabled only while connected
- Emergency stop shown while output is active

### 6. Help and safety page

The Help page currently contains:

- “SUPPORT” section eyebrow
- “Help & safety” title
- Persistent red stop-all-output panel
- Wellness/non-medical disclaimer
- Quick-help section
- Three-step connection card
- Connectivity troubleshooting card and dialog
- Hardware/power/cable-care card
- Motor-control explanation
- Unexpected-disconnection explanation
- Two safety-protocol cards
- Hardware-integrity and cable-care list

### 7. Settings and dialogs

The app currently contains:

- Settings/About dialog with local-only, no-cloud, control-range, and device information
- Bluetooth permission rationale dialog
- Connectivity checklist dialog
- Custom preset editor dialog
- Toasts for scan timeouts, permission denial, scan failure, disconnect, GATT incompatibility, and communication failure

### 8. BLE and safety behavior

The current implementation contains:

- Service-UUID-filtered BLE scanning
- Low-latency scan mode
- Android 12+ Nearby Devices permission support
- Android 11-and-older location-permission compatibility
- GATT service discovery
- Validation of the expected motor command characteristic
- One-byte, write-without-response motor commands
- Duplicate command suppression
- Immediate `0` write after connection
- Motor reset on disconnect
- Motor reset on communication failure
- Motor stop from every emergency-stop entry point
- Motor stop in `onPause`
- Firmware-side motor stop when BLE disconnects

## Problems and product gaps that must be fixed

### Priority 0 — safety and functional reliability

1. Keep every existing stop safeguard. Add tests or verifiable checks for stop-on-background, stop-on-disconnect, stop-on-write-failure, manual stop, and reconnect-at-zero.
2. Add a connection timeout after a device is found but GATT connection/service discovery never completes. The current timeout covers scanning only.
3. Make scan, connection, service-discovery, incompatible-firmware, permission-denied, Bluetooth-off, and communication-error states persistent and actionable in the UI. Do not rely only on a Toast.
4. Add clear Retry, Open Bluetooth settings, and Open App Settings actions where appropriate.
5. Handle “Don’t ask again” permission denial and explain how to restore permission.
6. Translate platform BLE error codes into calm, useful user-facing guidance while preserving technical details for optional diagnostics.
7. Do not claim the hardware applied a value merely because a write-without-response call was accepted. Use accurate wording such as “command sent” or add protocol acknowledgement only if firmware and app are updated together.
8. Make manual disconnect resolve quickly and visibly. Prevent controls from remaining apparently active while disconnection is pending.
9. Prevent overlapping scans, duplicate GATT objects, late callbacks from stale connections, and UI updates from an obsolete device session.
10. Confirm all BLE calls remain permission-safe across API 26–35+.

### Priority 1 — incomplete and broken UX

1. The Hardware help card is visually presented as an action but currently has no click behavior. Implement a complete hardware-care detail sheet/dialog or make the card visibly non-interactive.
2. Settings is too minimal for a finished product. Add app version/build, privacy summary, supported device, Bluetooth protocol status, replay onboarding, troubleshooting entry, safety/legal text, and optional copyable diagnostics. Do not add fake account or cloud settings.
3. Make onboarding accessible again from Settings. Do not require clearing app data.
4. Replace critical Toast-only messages with inline banners, dialogs, or snackbars that remain long enough to understand and act on.
5. Add explicit Cancel search while scanning and Retry after timeout.
6. Add meaningful empty, loading, success, error, and recovery states to each relevant page.
7. Preserve page scroll position and selected item position when a page re-renders. The current remove-and-rebuild pattern can jump the user to the top.
8. Preserve current tab, selected preset, custom editor state, and safe UI state across rotation and activity recreation.
9. Prevent repeated page reconstruction from restarting decorative animations or losing focus unnecessarily.
10. Add clear pressed, focused, disabled, loading, and selected states for all buttons, cards, tabs, and custom controls.

### Priority 1 — product-quality UI improvements

1. Retain the recognizable white/lavender NeuroVibe identity, but formalize it into reusable design tokens for color, spacing, typography, radius, elevation, icon sizes, and motion.
2. Use a consistent type scale and reduce ad hoc hard-coded text sizes.
3. Keep one obvious primary action per screen.
4. Keep destructive stop actions visually distinct from connection errors and ordinary secondary actions.
5. Make the Device page feel like a device dashboard rather than a large marketing image. Balance the product image, live state, and primary action.
6. Make the Control page prioritize live value, precise adjustment, and stop access. The decorative pulse visualization must never compete with the control itself.
7. Give presets meaningful product-oriented descriptions without making medical claims.
8. Improve information hierarchy in Help so urgent safety, setup, troubleshooting, operating guidance, and maintenance are clearly separated.
9. Use concise, consistent terminology: choose “output”, “level”, “frequency”, “preset”, “connect”, and “disconnect” deliberately and use them consistently.
10. Avoid wording that implies Hz and intensity are independent; in this device they are derived from the same command value.
11. Do not display battery level, firmware version, session duration, or other telemetry unless the BLE protocol genuinely provides it.
12. Ensure long headings such as “Choose your starting point” adapt gracefully on narrow screens and large font sizes.

### Priority 1 — responsive layout

1. The Samsung SM-T220 portrait rendering is clean, but layouts rely heavily on fixed dp sizes. Make all pages responsive across compact phones, large phones, tablets, landscape, split-screen, and multi-window.
2. Introduce width constraints so cards do not become excessively wide on tablets.
3. Use adaptive one-column/two-column compositions where helpful, especially Device, Control, and Help.
4. Ensure the bottom navigation does not cover scroll content or safety actions.
5. Respect system bars, display cutouts, gesture navigation, and keyboard insets.
6. Verify at minimum: 360×640 dp phone, 411×891 dp phone, 600×960 dp tablet, landscape tablet, and 200% font scale.
7. Avoid fixed-size content that clips translated or accessibility-sized text.

### Priority 1 — accessibility

1. Meet WCAG AA contrast for text and actionable components.
2. Keep minimum 48×48 dp touch targets.
3. Expose bottom-navigation selection semantics, not only color changes.
4. Add useful content descriptions to meaningful icons and mark decorative graphics as not important for accessibility.
5. Announce connection, disconnection, active-output, stopped-output, and critical error changes to assistive technology.
6. Ensure dialogs have logical initial focus and predictable dismissal.
7. Preserve the slider’s range semantics, keyboard operation, haptics, and accessible state description.
8. Verify switch access and TalkBack ordering on every page.
9. Do not rely on color alone for connected, active, warning, or error states.
10. Respect reduced-motion preferences; pulse animation must be optional or subdued.
11. Ensure all text remains readable at 200% font scale without overlap or truncation.

### Priority 2 — maintainability and Android engineering

1. Move all user-facing strings into `strings.xml`, including formatting plurals/placeholders. Current lint reports hard-coded/setText internationalization warnings.
2. Break the monolithic `MainActivity` into maintainable page/state components. Use a clear unidirectional state model or ViewModel so Bluetooth state is not tangled with view construction.
3. Prefer XML layouts, reusable custom components, or Compose only if migration risk is controlled. Do not perform a partial framework migration that leaves two inconsistent systems.
4. Centralize design tokens in resources instead of repeating raw colors and dimensions in Java.
5. Remove or intentionally use unused resources.
6. Eliminate avoidable allocations inside custom `onDraw` methods; lint currently identifies draw-time allocations.
7. Review deprecated APIs and API guards.
8. Review target SDK availability and update only when the installed toolchain supports it and behavior changes are handled.
9. Add stable view IDs to support testing and state restoration.
10. Add unit tests for value clamping, percentage conversion, preset behavior, duplicate-write suppression, and connection state transitions.
11. Add instrumentation tests for navigation, permission states, preset editing, stop behavior, and accessibility labels.
12. Add a small fake BLE layer so disconnected, scanning, connecting, connected, active, error, incompatible firmware, and timeout states can be tested without physical hardware.

## Required page-by-page end state

### Onboarding

- Keep it brief: value proposition, three setup steps, privacy statement, safety disclaimer, and one primary action.
- Include progress or step context only if onboarding becomes multi-step.
- Support Skip only if the device can be connected later without confusion.
- Allow replay from Settings.
- Explain Nearby Devices permission immediately before requesting it.

### Device

- Show a compact product identity area, prominent real connection status, and one connection action.
- Disconnected: Connect action plus concise setup requirements.
- Scanning: animated but reduced-motion-safe progress, elapsed/timeout context, and Cancel.
- Connecting: device found, connection in progress, and Cancel/Disconnect.
- Connected: Ready state, last observed RSSI with plain-language quality, and Disconnect.
- Error: persistent reason, safe state confirmation, Retry, and relevant settings/help action.
- Do not label a single initial RSSI sample as continuously live. Either refresh RSSI or label it as last observed.

### Control

- Disconnected state must explain why controls are unavailable and offer a direct path to Device/Connect.
- Connected state must show `0 Hz`, `0%`, and “Ready” until output starts.
- Active state must clearly show output is active on both motors.
- Keep the slider, value readout, percentage, `−10/+10`, and Stop action.
- Add one-step increments through accessibility/keyboard and retain 10-step tactile markers.
- Disable decrement at `0` and increment at `230`, with correct semantics.
- Consider optional direct-entry only if it can be implemented safely with validation.
- Keep the Stop action reachable without scrolling when output is active, especially on compact screens.
- Provide confirmation that output stopped.

### Presets

- Keep Gentle `60`, Balanced `115`, Strong `180`, and locally saved Custom.
- Make selection clearly different from execution.
- Preview must include name, Hz, derived percentage, both-motor scope, and explicit Start.
- Keep Start disabled while disconnected and provide a Connect path.
- Make custom editing accessible, validated, and cancelable without losing the previous value.
- Add Reset to default for the custom preset if useful.
- Do not run output merely by tapping a preset card.

### Help and safety

- Keep Stop all output first and easy to reach.
- Make the stop control available while connected; explain disconnected safe state when unavailable.
- Implement working details for both Connectivity and Hardware cards.
- Organize content into Setup, Connection troubleshooting, Using controls, Safety, and Hardware care.
- Keep the non-medical disclaimer accurate and readable.
- Add app/device diagnostics and support information without fabricating contact details.

### Settings/About

- Product and app version/build
- Connection/privacy summary
- Replay onboarding
- Permission status and open-settings action
- Bluetooth troubleshooting
- Safety and legal information
- Copy diagnostics action containing only non-sensitive technical details
- No login, subscription, cloud sync, analytics, or fabricated settings

## State matrix that must be designed and tested

Design explicit UI for every combination that matters:

- First launch / returning launch
- Bluetooth unavailable
- Bluetooth turned off
- Permission not requested
- Permission temporarily denied
- Permission permanently denied
- Ready to scan
- Scanning
- Scan canceled
- Scan timed out / device not found
- Scan platform failure
- Device found / connecting
- Connection timeout
- GATT connected / discovering services
- Incompatible firmware or characteristic missing
- Connected and ready at zero
- Connected with active output
- Command send failure
- Unexpected disconnect
- Manual disconnect in progress
- App backgrounded and output forced to zero
- Preset not selected
- Preset selected while disconnected
- Preset selected while connected
- Custom preset editing, saved, canceled, and reset

## Visual and interaction direction

- Professional, calm, precise, modern hardware-companion product
- Light neutral background with restrained lavender/purple brand color
- White cards with subtle borders; avoid excessive shadows
- Red reserved for stop/destructive/error actions
- Green reserved for verified connected/ready states
- Amber reserved for caution or transitional attention
- Consistent 8-point spacing system
- Clear page titles and short explanatory copy
- High-quality product image treatment without letting it dominate core controls
- Subtle, purposeful motion only
- Haptic feedback for discrete control confirmation and stop
- No glassmorphism, visual clutter, medical imagery, unsupported metrics, or decorative dashboards

## Non-negotiable constraints

1. Preserve the BLE UUIDs and one-byte `0–230` command format unless app and firmware changes are intentionally coordinated.
2. Preserve immediate stop behavior and fail-safe disconnect behavior.
3. Never allow a preset to start from card selection alone.
4. Never fabricate device data.
5. Keep operation local-only and offline.
6. Do not add medical claims.
7. Keep Android 8.0 compatibility unless a documented product decision changes it.
8. Preserve or improve accessibility.
9. Do not remove useful behavior merely to simplify the redesign.
10. Preserve user data during upgrades.

## Required implementation workflow

1. Inspect all current files and run a baseline build and lint.
2. Document the existing BLE state transitions before refactoring.
3. Create a state model and design-token plan.
4. Implement Priority 0 safety/reliability fixes first.
5. Implement page improvements and responsive layouts.
6. Add accessibility semantics and state announcements.
7. Add tests and fake BLE states.
8. Run `assembleDebug`, unit tests, instrumentation tests where available, and `lintDebug`.
9. Render or capture every primary page and major state on phone and tablet.
10. Fix clipping, contrast, spacing, focus order, and touch-target problems found during visual QA.
11. Install the final APK on the connected Samsung device and perform a smoke test.

## Deliverables

- Updated Android source and resources
- Preserved and documented BLE protocol behavior
- Complete responsive page set
- Implemented Hardware help flow
- Expanded Settings/About flow
- Persistent inline error/recovery states
- State-model or ViewModel separation
- Accessibility improvements
- Unit and UI tests
- Updated README with screen inventory, safety behavior, setup, and build instructions
- Successful debug APK
- Lint/test report with zero errors and an explanation of any intentionally retained warnings
- Before/after screenshots for Device, Control, Presets, Help, onboarding, Settings, and key error states

## Definition of done

The work is complete only when:

- The app builds successfully.
- Every page and dialog is reachable and functional.
- The Hardware help card works.
- All connection and permission failures offer persistent recovery actions.
- Stop behavior is verified for every safety path.
- Control and preset behavior remains accurate for the `0–230` protocol.
- No content clips at tested screen sizes and 200% font scale.
- TalkBack can navigate and understand every action and state.
- The UI is consistent across phone and tablet.
- No fake telemetry or medical claims are introduced.
- The final APK installs and launches on the Samsung SM-T220.

Before changing code, summarize the current state and proposed architecture. After implementation, report exactly what changed, which tests passed, known limitations, and the absolute path to the final APK.

---

## Audit note

The installed build was successfully scanned on a Samsung SM-T220. Device, Control, Presets, Help, Settings, custom preset editing, preset preview, and connectivity help were inspected. The source-level BLE, permission, lifecycle, and safety paths were also reviewed.
