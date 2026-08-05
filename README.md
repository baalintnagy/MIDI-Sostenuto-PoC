# SustSos

SustSos is a Windows MIDI transformer that emulates traditional hardware-piano sostenuto behavior. It coordinates sustain (CC64) and sostenuto (CC66) by retaining Note Off messages and handing held notes from one pedal mechanism to the other.

> [!IMPORTANT]
> SustSos is a work in progress and currently uses the Windows WinMM API directly.

You need at least one loopback MIDI port between SustSos and your DAW or instrument host. Options include [loopMIDI](https://www.tobias-erichsen.de/software/loopmidi.html) and [Windows MIDI Services](https://github.com/microsoft/MIDI).

## Behavior

- Implements selective sostenuto latching: only notes held when CC66 engages are captured.
- Retains Note Off messages while sustain or sostenuto owns a note.
- Hands retained notes between sustain and sostenuto without an audible release.
- Accepts a primary MIDI input plus an optional dedicated pedal/control input.
- Remaps a configurable controller number to CC66.
- Suppresses the emulated CC64 and CC66 messages instead of forwarding them to the output.
- Logs incoming MIDI messages asynchronously with color-coded console output.
- Uses WinMM directly through JNA for low-overhead MIDI I/O.

### Umbrella mode

Ableton Live can inject a Note Off before a repeated Note On on its internal bus. Umbrella mode surrounds affected Note On messages with a short CC64 pulse so that this automatic Note Off does not retrigger the instrument's release stage.

Umbrella mode has two timing controls:

- `delayMs`: delay before the protected Note On is sent.
- `tailMs`: duration after that Note On before the temporary CC64 pulse is released.

## Requirements

- Windows
- JDK 21 or newer
- Maven
- A MIDI controller or digital piano
- A loopback MIDI output

## Build and run

```powershell
mvn clean package
java -jar target/sustsos-1.0.1a-all.jar
```

The package phase also creates a self-contained Windows application image in `target/jpackage-out/SustSos/`.

## Configuration

At startup, SustSos lists the available WinMM inputs and outputs and then prompts for one comma-separated configuration line:

```text
<inName>[,<in2Name>],<outName>,<umbrella>,<delayMs>,<tailMs>[,<sostCcIn>]
```

| Field | Meaning |
| --- | --- |
| `inName` | Primary input for notes, pedals, pitch bend, modulation, and other MIDI messages. |
| `in2Name` | Optional dedicated input used only for the configured sostenuto controller. |
| `outName` | Loopback or destination MIDI output. |
| `umbrella` | `true` to enable umbrella mode; otherwise `false`. |
| `delayMs` | Umbrella Note On delay in milliseconds. |
| `tailMs` | Umbrella CC64 tail in milliseconds. |
| `sostCcIn` | Optional incoming controller to remap to CC66; defaults to CC22. |

When a secondary input is configured, only its matching `sostCcIn` messages are processed. With a single input, matching controller messages on the primary input are remapped instead. Invalid configuration input falls back to the built-in Hammer 88 Pro defaults after printing the parsing error.

### Examples

Single controller, default CC22 remapping:

```text
USB MIDI Controller,Loopback MIDI Port,true,1,1
```

Primary keyboard plus dedicated pedal input:

```text
Yamaha P-125,USB Foot Controller,Loopback MIDI Port,true,1,1
```

Single controller with CC67 remapped to sostenuto:

```text
Korg Krome,Loopback MIDI Port,true,1,1,67
```

Umbrella mode disabled:

```text
USB MIDI Controller,Loopback MIDI Port,false,0,0
```

The built-in fallback configuration is:

```text
Hammer 88 Pro,MIDIIN3 (Hammer 88 Pro),postSSB,true,1,1,22
```

Device names must match the names printed at startup, ignoring letter case.

## Processing model

SustSos tracks each of the 128 notes independently on all 16 MIDI channels:

1. A Note On marks the key as pressed.
2. Sostenuto pedal-down snapshots the notes currently pressed or already retained by sustain.
3. Note Off is delayed while sustain is active or while an active sostenuto latch owns that note.
4. Releasing one pedal transfers retained notes to the other pedal when appropriate.
5. A retained Note Off is emitted once neither pedal owns the note.

Input callbacks perform message routing, a dedicated logger thread formats diagnostics, and a scheduled worker handles umbrella timing. Native input callbacks are owned by their input objects so they remain strongly reachable for the lifetime of the WinMM handles.

## Project layout

- `SustSos` and `SustSosApplication`: entry point and application lifecycle.
- `AppConfig`: immutable configuration and command-line parsing.
- `SostenutoReceiver`: sustain, sostenuto, and umbrella state machine.
- `MidiEventRouter`, `PackedMidiMessage`, and `MidiMessages`: MIDI decoding, remapping, and construction.
- `WinMM*`: native WinMM bindings and resource ownership.
- `MidiLogger`, `MidiMessageType`, `Mappings`, and `AnsiColor`: asynchronous diagnostics.

## Troubleshooting

- If a device cannot be found, copy its name exactly from the startup list.
- If your terminal does not support ANSI colors, set `USE_COLORS` to `false` in `SustSosApplication`.
- For use outside Ableton Live, start with umbrella mode disabled.
- Press Enter at the quit prompt to trigger the shutdown sequence and MIDI panic cleanup.

## Releases

Prebuilt application images are available from the [GitHub releases page](https://github.com/baalintnagy/MIDI-Sostenuto-PoC/releases).

## License

SustSos is released under the [MIT License](LICENSE).
