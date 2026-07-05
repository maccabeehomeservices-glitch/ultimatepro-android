# UltimatePro Android: Claude project rules

This file is auto-loaded by Claude Code when working in this folder.
The universal rules (Six Rules, em-dash ban, no fabrication, formatting,
verification) live in `C:\Users\dadus\Desktop\CLAUDE-RULES.md` and apply 
here too. This file adds UltimatePro Android-specific rules on top.

## Project context

- Kotlin + Jetpack Compose + Hilt.
- Package: `com.ultimatepro` (renamed from com.ultimatecrm on Apr 4, 2026).
- Test device: Samsung Galaxy Note 9, serial `287cf22f1c047ece`, 
  Android 10 / API 29.
- ADB binary: `C:\Users\dadus\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Java JDK: `C:\Program Files\Java\jdk-17.0.18`.
- Firebase: project `ultimatecrm-a9e32`.
- Sister projects:
  - Backend: C:\ultimatecrm\backend (Node.js + Express + PostgreSQL on 
    Railway, project steadfast-beauty)
  - Web: C:\ultimatecrm\web (React 18 + Vite 5 + Tailwind, hosted on 
    Railway, domain ultimatepro.pro)

## Hard rules (never violate)

- **`AlertDialog` for ALL forms containing text input. NEVER 
  `ModalBottomSheet`.** The Note 9 (Android 10 / API 29) has a broken 
  IME interaction with `ModalBottomSheet` that hides the keyboard 
  behind the sheet or breaks input focus. This bug is hardware/OS 
  specific and not present on newer devices, but the Note 9 is the 
  primary test device. AlertDialog only.

- **`AppSwitch` not raw `Switch`.** The custom `AppSwitch` composable 
  has the right styling and behavior; raw `Switch` does not match the 
  design system.

- **`QtyStepperRow` for all numeric quantity inputs.** Hand-written 
  number fields cause keyboard and validation issues.

- **`DatePickerDialog` (carousel style) for all date fields. Never free 
  text date input.** Users mistype, regex breaks, dates get saved 
  wrong.

- **All ADB commands use `-s 287cf22f1c047ece`** to target the Note 9 
  specifically. Without the flag, ADB picks an arbitrary connected 
  device.

- **Always release builds for testing.** Debug builds hardcode the 
  backend URL to a local dev IP and break against production. Build 
  with `gradlew assembleRelease`, install with 
  `adb -s 287cf22f1c047ece install -r app\build\outputs\apk\release\app-release.apk`.

- **Clean builds only when files were renamed/deleted or stale DEX 
  errors appear.** Otherwise incremental builds are faster and safer.

## API field name conventions (must match backend)

- Paste ticket response: `job_title`, `job_description`, 
  `leftover_notes`, `phone`. NOT `title` / `description` / `notes` / 
  `customer_phone`.
- Jobs POST: `scheduled_start` ISO string, null-safe.
- `EstimateTier` data class field names: `tier_label`, `description`, 
  `line_items`, `subtotal`, `tax_total`, `discount_total`, `total`, 
  `sort_order`. With `@SerializedName` annotations matching backend 
  snake_case.

## When in doubt

Ask David. Test on Note 9 before claiming it works. Visual rendering 
on the actual device is the final verification, not Android Studio 
preview.
