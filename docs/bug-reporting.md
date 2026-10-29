# Sancho issue reporting design

This document describes the small, offline-first report workflow for bugs and missing features found while using Sancho. The Android editor/export and local analyzer are implemented; connected-PC sync remains out of scope for now.

## User flow

1. Open Settings and choose `Report an issue`.
2. Choose `Bug` or `Missing feature`.
3. Enter a short title and description.
4. Optionally add reproduction steps, expected behavior, and actual behavior.
5. Attach one or more screenshots through Android’s Photo Picker.
6. Review the generated report and save it locally.
7. Edit or delete saved reports from the report list.
8. Export one report or a bundle through Android’s document picker or share sheet.

Reports should remain useful without network access. Sancho should never upload them automatically.

## Report contents

Each report uses a stable JSON schema with a human-readable Markdown export:

- report identifier and creation time;
- category, title, and description;
- optional reproduction steps and expected/actual behavior;
- screenshots selected explicitly by the user;
- creation and update timestamps;
- schema version for future evaluation tooling.

Diagnostic metadata such as app version, device information, model/backend, TTFT, generation status, and error status can be added later through the metrics/settings decision.

Reports should use a bundle format when exported:

```text
sancho_report.zip
├── manifest.json
├── reports/<report-id>.json
├── reports/<report-id>.md
└── screenshots/<report-id>-<number>.jpg
```

## Privacy defaults

- Prompt text, responses, attached images, model files, credentials, and filesystem paths are excluded by default.
- A separate `Include diagnostic context` control may include selected technical metadata.
- Screenshots are explicit attachments selected by the user and are shown in the report preview before saving.
- Prompt text, response text, and chat images remain excluded even when screenshots are attached.
- Reports are stored in private app storage until the user exports or deletes them.

## Moving reports to the local PC

Sancho should export through Android’s Storage Access Framework rather than exposing its private app directory. The user selects the destination, normally the AI profile’s `Downloads` directory, and Sancho writes the ZIP bundle there.

The bundle can then be moved to the development PC in either of these ways:

- use GrapheneOS Files and USB file transfer to copy the bundle from `Downloads`;
- with the AI profile active and USB debugging authorized, use `adb pull` on the exported file, for example:

```sh
adb shell ls /sdcard/Download/sancho_report.zip
adb pull /sdcard/Download/sancho_report.zip reports/inbox/
```

The Files route is the primary user-facing path. ADB is a developer convenience and should not be required by the app.

## Optional connected-PC sync

There is currently no PC server in this repository. The proposed direct-sync component is a small local companion service, `sancho-reportd`, running on the development PC and listening only on `127.0.0.1`.

The intended flow is:

1. The developer starts the companion service with a future `make reports-server` command.
2. The command verifies that the authorized AI-profile device is present and configures an ADB reverse tunnel, for example `adb reverse tcp:8787 tcp:8787`.
3. Sancho shows `Send to connected PC` in the report detail screen.
4. After explicit confirmation, Sancho sends the selected report bundle to the local service over `127.0.0.1`.
5. The service validates, stores, and acknowledges the bundle.

Sancho should not silently upload reports merely because a USB cable was connected. The explicit action avoids surprising transfers and keeps the feature usable when the phone is connected to a different computer. A one-time pairing token shown by the PC service should be required before the first transfer; later transfers can use a locally stored token that the user can revoke.

The export-to-Downloads flow remains the fallback when ADB is unavailable or the user does not want to run the service.

## Later evaluation workflow

Exported reports can be copied into the repository’s ignored `reports/inbox/` directory and analyzed with the dependency-free `make reports-analyze` command below. The original ZIP remains unchanged; analysis output is derived and disposable.

## Automated analysis and prioritization

After export, run the dependency-free analyzer with the ZIP path as its argument:

```sh
make reports-analyze FILE=/path/to/sancho_report.zip
```

The command writes `sancho_report.analysis.json` and `sancho_report.analysis.md` beside the input ZIP. The first implementation uses deterministic rules and produces a reviewable proposal rather than changing code automatically.

Each analyzed report should receive:

- priority: `P0` through `P3`;
- implementation risk: `Low`, `Medium`, or `High`;
- confidence score;
- affected area and likely feature;
- supporting evidence and recommended next action.

Priority should consider user impact, whether the app is blocked or unusable, reproducibility, data loss or privacy impact, and the number of duplicate reports. Implementation risk should consider permissions, storage or schema migration, model/runtime changes, security exposure, device scope, and test complexity.

An optional local LLM may later help summarize descriptions, but report text must be treated as untrusted data and the model output must be schema-validated. Analysis may propose a priority or implementation plan; it must not edit source code, execute commands, or create a commit without explicit developer action.

The proposed local workflow is:

```text
sancho_report.zip
    -> make reports-analyze FILE=sancho_report.zip
    -> sancho_report.analysis.json
    -> sancho_report.analysis.md
    -> human review and issue/commit planning
```

## Resolved design decisions

- No labels are needed while Sancho has one developer.
- Screenshots are supported as explicit user-selected attachments and exported beside the report inside the bundle.
- Reports can be edited and deleted before export; deletion requires confirmation and removes the report attachments.

## Remaining design choice

- Whether a future metrics setting controls which performance fields are captured.
