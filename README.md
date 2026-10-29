# SustSos

SustSos is a Windows MIDI transformer that emulates traditional hardware-piano sostenuto behavior. It coordinates sustain (CC64) and sostenuto (CC66) by retaining Note Off messages and handing held notes from one pedal mechanism to the other.

> [!IMPORTANT]
> SustSos is a work in progress and currently uses the Windows WinMM API directly.

You need at least one loopback MIDI port between SustSos and your DAW or instrument host. Options include [loopMIDI](https://www.tobias-erichsen.de/software/loopmidi.html) and [Windows MIDI Services](https://github.com/microsoft/MIDI).

## Behavior

- Implements selective sostenuto latching: notes pressed or already retained by sustain when CC66 engages are captured.
- Retains Note Off messages while sustain or sostenuto owns a note.
- Hands retained notes between sustain and sostenuto without an audible release.
- Accepts a primary MIDI input plus an optional dedicated pedal/control input.
- Remaps a configurable CC or Note On/Off trigger to internal CC66, with optional note toggle control.
- Suppresses the emulated CC64 and CC66 messages instead of forwarding them to the output.
- Logs incoming MIDI messages asynchronously with color-coded console output and optional JSON blacklist filtering.
- Uses WinMM directly through JNA for low-overhead MIDI I/O.

### Umbrella mode

Ableton Live can inject a Note Off before a repeated Note On on its internal bus. Umbrella mode surrounds affected Note On messages with a short CC64 pulse so that this automatic Note Off does not retrigger the instrument's release stage.

Umbrella mode has two timing controls:

- `delayMs`: delay before the protected Note On is sent.
- `tailMs`: duration after that Note On before the temporary CC64 pulse is released.

## Requirements

- Windows
- JDK 25 or newer and Maven to build; Java 25 or newer to run the JAR. The packaged Windows application bundles its runtime.
- A MIDI controller or digital piano
- A loopback MIDI output

## Build and run

From Git Bash, run `bash build.sh` for a clean build, tests, JARs, a Windows application image, and `target/SustSos-windows.zip`. The script resolves paths relative to itself; from another directory, invoke it as `bash /path/to/sustsos/build.sh`. The script deletes and recreates `target/`. Close any SustSos instance running there and any terminal tabs opened inside its output directories before building. Maven and Windows PowerShell must be on PATH, with `JAVA_HOME` pointing to JDK 25 or newer.

```powershell
mvn clean package
java -jar target/sustsos-1.0.4-all.jar
```

The package phase also creates a self-contained Windows application image in `target/jpackage-out/SustSos/`; launch `SustSos.exe` there. `build.sh` additionally creates `target/SustSos-windows.zip`. Maven alone does not create the ZIP.

Set `JAVA_HOME` to your JDK 25 (or newer) installation and confirm it with `mvn -version` before building. Compilation targets Java 25 and uses finalized language features only; preview flags are not required. The executable JAR and packaged launcher enable native access for the JNA WinMM bindings. When launching from an IDE or with a custom classpath, add `--enable-native-access=ALL-UNNAMED` to the JVM options.

## Configuration

At startup, SustSos lists the available WinMM inputs and outputs, followed by a numbered menu of JSON configuration filenames. Enter a file's number to load it. Configurations are discovered in the working directory and, for packaged builds, beside the application JAR (`app/`). The list is sorted by filename; a working-directory file takes precedence over a packaged file with the same name. Invalid selections or configuration files display an error and let you choose again.

Choose `0. Other...` to repeat the MIDI input/output list and enter a manual comma-separated configuration:

```text
<inName>[,<in2Name>],<outName>,<umbrella>,<delayMs>,<tailMs>[,<sostCcIn>][,note:<0..127>][,toggle:<true/false>]
```

| Field | Meaning |
| --- | --- |
| `inName` | Primary input for notes, pedals, pitch bend, modulation, and other MIDI messages. |
| `in2Name` | Optional dedicated input used only for the configured sostenuto CC or note trigger. |
| `outName` | Loopback or destination MIDI output. |
| `umbrella` | `true` to enable umbrella mode; otherwise `false`. |
| `delayMs` | Umbrella Note On delay in milliseconds. |
| `tailMs` | Umbrella CC64 tail in milliseconds. |
| `sostCcIn` | Optional incoming controller to remap to CC66; defaults to CC22. |
| `note:<0..127>` | Optional note number; selects note control instead of the configured CC. |
| `toggle:<true/false>` | Optional note interpretation: `true` toggles once per press; `false` (default) holds until release. Place this last, after the note field. Ignored in CC mode. |

When a secondary input is configured, only its matching sostenuto trigger messages are processed; other messages from that port are ignored after logging. With a single input, the trigger is read from the primary input. Other primary-input messages continue through normal MIDI processing. Blank or invalid manual input displays an error and repeats the manual prompt. No configuration file is loaded automatically. End of input stops startup.

### Examples

Single controller with CC22 remapping:

```text
USB MIDI Controller,Loopback MIDI Port,true,1,1,22
```

Primary keyboard plus dedicated pedal input:

```text
Yamaha P-125,USB Foot Controller,Loopback MIDI Port,true,1,1,22
```

Single controller with CC67 remapped to sostenuto:

```text
Korg Krome,Loopback MIDI Port,true,1,1,67
```

Umbrella mode disabled:

```text
USB MIDI Controller,Loopback MIDI Port,false,0,0
```

### JSON configuration examples

[Hammer88Pro.json](Hammer88Pro.json) uses CC22 from the dedicated Hammer 88 Pro input:

```json
{
  "primaryInputName": "Hammer 88 Pro",
  "secondaryInputName": "MIDIIN3 (Hammer 88 Pro)",
  "outputName": "postSSB",
  "umbrellaEnabled": true,
  "umbrellaDelayMs": 1,
  "umbrellaTailMs": 1,
  "sostenutoCC": 22,
  "sostenutoNote": null,
  "sostenutoNoteToggle": null
}
```

[Hammer88Pro_n_Launchpad.json](Hammer88Pro_n_Launchpad.json) uses note 78 from the Launchpad to toggle sostenuto and suppresses clock messages in the log:

```json
{
  "primaryInputName": "Hammer 88 Pro",
  "secondaryInputName": "LPProMK3 MIDI",
  "outputName": "postSSB",
  "umbrellaEnabled": true,
  "umbrellaDelayMs": 1,
  "umbrellaTailMs": 1,
  "sostenutoCC": 22,
  "sostenutoNote": 78,
  "sostenutoNoteToggle": true,
  "logFilter": {
    "blacklist": [
      { "type": "CLOCK" }
    ]
  }
}
```

The selected file is read using Jackson. There is no special configuration filename or automatic fallback. The project currently includes `Hammer88Pro.json` (CC22 control) and `Hammer88Pro_n_Launchpad.json` (note 78 toggle control with clock logging suppressed). The Windows package copies all project-root `*.json` files into `app/`; you can add or edit configurations there or in the working directory. Configurations are not embedded in the JAR.

The seven fields from `primaryInputName` through `sostenutoCC` are required. `sostenutoNote`, `sostenutoNoteToggle`, and `logFilter` are optional. Use an empty `secondaryInputName` for a single input. Timings must be nonnegative; CC and note numbers must be 0..127. Unknown properties or invalid values reject the file and return to the selection menu.

Device names must match the names printed at startup, ignoring letter case.

### Using a note to control sostenuto

Set `"sostenutoNote": 78` in a JSON configuration to use MIDI note number 78 as the sostenuto control. Keep the existing `sostenutoCC` field; omitting `sostenutoNote` (or setting it to `null`) selects CC control as before.

In hold mode (`sostenutoNoteToggle` omitted, null, or false), Note On with any positive velocity engages sostenuto; Note Off or Note On with velocity zero releases it. The triggering note is consumed rather than played. Other notes on the primary input continue to play normally. Note numbers must be 0..127.

The input port selection is unchanged: a nonempty `secondaryInputName` selects that port and filters out all messages except the configured trigger. With an empty secondary name, the trigger is read from the primary input. The incoming MIDI channel is preserved, so the trigger controls sostenuto on that channel.

Interactive configuration also accepts a `note:<number>` field after the timings or optional CC number, for example:

```text
Keyboard,Pedal Controller,Loopback,false,0,0,note:78
```

An existing CC number may precede the note field, such as `Keyboard,Loopback,false,0,0,22,note:78`. In note mode, the configured CC number is not remapped; ordinary primary-input CC messages retain their existing handling, including native CC66 sostenuto.

Set optional `"sostenutoNoteToggle": true` alongside `"sostenutoNote": 78` for click/toggle behavior: the first press engages sostenuto, the next press releases it. Release messages (Note Off or zero-velocity Note On) only prepare for the next press. Repeated Note On messages while held do not toggle again. State is independent per MIDI channel. Omit the boolean or set it to `null` or `false` for hold/release behavior. This option affects note control only; CC behavior is unchanged.

Interactive equivalent: `Keyboard,Pedal Controller,Loopback,false,0,0,note:78,toggle:true`.

Select your JSON file by number at startup. In `0. Other...`, enter a manual configuration; blank input repeats the prompt. The manual prompt shows CC22 and note78 toggle examples.

### Log filtering

Add an optional `logFilter` object to any JSON configuration:

```json
"logFilter": {
  "blacklist": [
    { "type": "CLOCK" },
    { "type": "ACTIVE_SENSING" },
    { "type": "CC", "cc": 1 },
    { "type": "CC", "cc": 11, "channel": 1 },
    { "type": "NOTE", "note": 78, "port": "LPProMK3 MIDI" }
  ]
}
```

Any matching rule suppresses the incoming log entry before it is queued. All fields in a rule must match; omitted fields match any value. `port` matches the configured input-name string exactly, including letter case (device lookup itself ignores case), `channel` is 1..16, and `cc`/`note` are 0..127. MIDI processing and output are unaffected. Filters apply to original incoming messages, before sostenuto remapping.

Types use the exact, case-sensitive log labels: `NOTE_ON`, `NOTE_OFF`, `CC`, `P_BEND`, `CH_PR`, `POLY_AT`, `PC`, `SYSEX_START`, `TIME_CODE`, `SONG_POSITION`, `SONG_SELECT`, `TUNE_REQUEST`, `SYSEX_END`, `CLOCK`, `START`, `CONTINUE`, `STOP`, `ACTIVE_SENSING`, `SYSTEM_RESET`, and `OTHER`. The additional alias `NOTE` matches both note events. For filtering, zero-velocity Note On counts as `NOTE_OFF`, although the log continues to display the original message type.

Use `cc` only with `CC`, `note` only with `NOTE`/`NOTE_ON`/`NOTE_OFF`, and `channel` only with channel message types. Unknown types, unknown fields, and invalid values fail configuration loading with an error. An omitted or null `logFilter`, or an empty `blacklist`, logs everything. When `logFilter` is an object, `blacklist` must be a non-null array of rules, each with a non-null `type`. Optional rule fields may be omitted or null. Manual configuration does not configure log filtering.

## Processing model

SustSos tracks each of the 128 notes independently on all 16 MIDI channels:

1. A Note On marks the key as pressed.
2. Sostenuto pedal-down snapshots the notes currently pressed or already retained by sustain.
3. Note Off is delayed while sustain is active or while an active sostenuto latch owns that note.
4. Releasing one pedal transfers retained notes to the other pedal when appropriate.
5. A retained Note Off is emitted once neither pedal owns the note.

The logger displays system messages with `CH: --` and uses `OTHER` for unrecognized statuses. The current WinMM input callback receives short MIDI messages; adding SysEx labels does not add long-buffer SysEx reception.

Input callbacks perform message routing, a dedicated logger thread formats diagnostics, and a scheduled worker handles umbrella timing. Native input callbacks are owned by their input objects so they remain strongly reachable for the lifetime of the WinMM handles.

## Project layout

- `SustSos` and `SustSosApplication`: entry point and application lifecycle.
- `AppConfig` and `ConfigurationMenu`: immutable configuration, JSON discovery, numbered selection, and manual input parsing.
- `SostenutoReceiver`: sustain, sostenuto, and umbrella state machine.
- `MidiEventRouter`, `PackedMidiMessage`, and `MidiMessages`: MIDI decoding, remapping, and construction.
- `WinMM*`: native WinMM bindings and resource ownership.
- `MidiLogger`, `MidiMessageType`, `ColorMappings`, `LogFilter`, and `AnsiColor`: asynchronous diagnostics.

## Troubleshooting

- If a device cannot be found, copy its name exactly from the startup list.
- If your terminal does not support ANSI colors, set `USE_COLORS` to `false` in `SustSosApplication`.
- For use outside Ableton Live, start with umbrella mode disabled.
- Press Enter at the quit prompt to trigger the shutdown sequence and MIDI panic cleanup.

## Releases

Prebuilt application images are available from the [GitHub releases page](https://github.com/baalintnagy/MIDI-Sostenuto-PoC/releases).

## License

SustSos is released under the [MIT License](LICENSE).
