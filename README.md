# MIDI Sustain Sostenuto Transformer

## Important

> This is a Work in Progress Project - the code is ugly
> Windows only for now

You need at least one loopback MIDI port to use this application.
Examples:
- loopMIDI: https://www.tobias-erichsen.de/software/loopmidi.html (Kind of abandoned, last version is from 2019) 
- Windows MIDI Services: pre-released, unstable, but tameable on Windows 11.

## Overview

A MIDI processor that emulates CC66 (Sostenuto) behavior of oldschool hardware digital pianos and keyboards: 
Main consideration: handing over held notes from CC66 (Sostenuto) and/to CC64 (Sustain) mechanism.
The implementation clears CC64 and CC66, and uses Note-Off message retention.

> This means emulated CC66 and CC64 messages are not relayed to the loopback port.


## Releases

> Uses App image packaging - no installation required

[📦 View and download releases on GitHub](https://github.com/baalintnagy/MIDI-Sostenuto-PoC/releases)

## Features

- **Dual Input Support**: Primary MIDI input (keys, pedals, pitch-bender, mod-wheel, etc) plus optional secondary input (if sostenuto control is provided on a different port - MIDI controllers creates dedicated MIDI inputs for CTRL messages.)
- **Sostenuto Implementation**: True Sostenuto (CC66) functionality with latching mechanism: Note-Off retention.
- **Umbrella**: (Specific to Ableton Live). This having enabled, the transformer injects small Sustain (CC64) impulses - to avoid triggering unwanted ADSR/R artifacts due to Ableton Live injecting automatic Note-Off messages before consecutive Note-On messages on its internal BUS.
- **Real-time Processing**: Sub-millisecond MIDI-latency with optimized threading. (Tested usingthe unstable Windows MIDI Services.)
- **Color-coded Logging**: Comprehensive MIDI message monitoring with ANSI color support
- **Windows Native Integration**: Uses WinMM API for direct hardware access

## Build Requirements

- Java 21 or higher
- Windows operating system (WinMM API dependency)
- Maven build system
- MIDI-compatible digital piano or controller

## Installation

### Build from Source

1. Clone the repository:
```bash
git clone <repository-url>
cd sustsos
```

2. Build the project:
```bash
mvn clean package
```

3. Run the application:
```bash
java -jar target/sustsos-*-all.jar
```

### Windows Executable

The build process also creates a bundled Windows executable with embedded JRE:

```bash
mvn clean package
```

The executable will be located in `target/jpackage-out/`.

## Usage

### Configuration

When you start SustSos, it will display available MIDI inputs and outputs, then prompt for configuration:

```
Enter config: <inName>[,<in2Name>],<outName>,<umbrella:true/false>,<delayMs>,<tailMs>[,<sostCcIn>]
```

**Parameters:**
- `inName`: Primary MIDI input device name
- `in2Name` (optional): Secondary MIDI input for sostenuto control
- `outName`: MIDI output device name
- `umbrella`: Enable/disable umbrella mode (true/false)
- `delayMs`: Umbrella mode delay in milliseconds
- `tailMs`: Umbrella mode tail duration in milliseconds
- `sostCcIn` (optional): CC number to internally remap to CC66 (sostenuto), and handle accordingly

**NOTE** In case of config parsing error, this version will silently fall back to my specific hardware. (Hammer 88 Pro)

### Example Configurations

**Basic setup with single controller:**
```
USB MIDI Controller,LoopMIDI Port,true,1,1
```

**Dual input setup (main keyboard + separate pedal controller):**
```
Yamaha P-125,USB Foot Controller,LoopMIDI Port,true,1,1
```

**Popular MIDI Controller Configurations:**

#### MIDI Controllers
- **Novation Launchkey Series:**
  ```
  Launchkey 49 MK3,LoopBack MIDI Port,true,1,1
  ```
- **Native Instruments Komplete Kontrol:**
  ```
  Komplete Kontrol A61,LoopBack MIDI Port,true,1,1
  ```
- **Arturia KeyLab Series:**
  ```
  KeyLab Essential 61,LoopBack MIDI Port,true,1,1
  ```

#### Professional Controllers
- **Hammer 88 Pro (developer's setup):**
  ```
  Hammer 88 Pro,MIDIIN3 (Hammer 88 Pro),LoopBack MIDI Port,true,1,1
  ```
- **M-Audio Oxygen Series:**
  ```
  Oxygen 49,LoopBack MIDI Port,true,1,1
  ```

#### Custom CC Remapping
Some controllers use different CC numbers for sostenuto-like functionality:

- **Korg keyboards (CC67 for sostenuto):**
  ```
  Korg Krome,LoopBack MIDI Port,true,1,1,67
  ```
- **Roland keyboards (CC67 for sostenuto):**
  ```
  Roland RD-2000,LoopBack MIDI Port,true,1,1,67
  ```

#### Advanced Setup with Umbrella Mode Disabled
For use outside Ableton Live:
```
USB MIDI Controller,LoopBack MIDI Port,false,0,0
```

**Tips:**
- Use `loopMIDI` for simple configuration - latency promises not guaranteed
- Use `Windows MIDI Services` for guaranteed sub-millisecond latency, suffer confuguration comsequences
- To quickly see available device names, just run the application from terminal and let the cmd-line configuration fail.
- Umbrella mode recommended for Ableton Live users, primarily, but can be used with any DAW

## Technical Details

### Sostenuto Logic

The application implements true sostenuto behavior:

1. **Latching**: When sostenuto is engaged, currently pressed/delayed notes are latched
2. **Selective Sustain**: Only latched notes continue sounding when keys are released
3. **Release**: When sostenuto is disengaged, latched notes are released (unless sustain is active)

### Threading Model

- **Main Thread**: MIDI input processing and message routing
- **Logger Thread**: Asynchronous MIDI message logging
- **Umbrella Thread**: Scheduled umbrella mode operations
- **Quit Watcher**: Graceful shutdown handling

## Configuration Constants

Key configurable parameters in `SustSos.java`:

```java
private static int CC66_THRESH = 10;      // Sostenuto activation threshold
private static int CC66_HYST = 2;         // Hysteresis to prevent chatter
private static boolean UMBRELLA = true;   // Enable umbrella mode
private static int UMBRELLA_DELAY_MS = 1; // Umbrella delay in milliseconds
private static int UMBRELLA_TAIL_MS = 1;  // Umbrella tail duration
```

## Troubleshooting

Set `USE_COLORS = false` in the source code to disable ANSI colors if terminal doesn't support them.

### Dependencies

- **JNA 5.14.0**: Java Native Access for WinMM API integration
- **Java MIDI API**: Built-in MIDI message handling

### Building

```bash
# Development build
mvn compile

# Full package with executable
mvn clean package

# Windows executable only
mvn clean package
```

## License

This project is licensed under the MIT License - see the details below:

```
MIT License

Copyright (c) 2025 Balint Nagy

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
