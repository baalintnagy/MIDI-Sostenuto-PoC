# MIDI Sustain Sostenuto Transformer

## Important

> This is a Work in Progress Project
> Windows only for now

## Overview

A MIDI processor that emulates CC66 (Sostenuto) behavior with handing over held notes from and/to sustain.
The implementation uses Note-Off message retention. 

## Features

- **Dual Input Support**: Primary MIDI input plus optional secondary input for sostenuto control
- **Sostenuto Implementation**: True sostenuto functionality with latching mechanism
- **Umbrella**: Injects small Sustain impulses to avoid triggering unwanted ADST/R due to Ableton Live injecting automatic Note-Off messages before Note-On messages.
- **Real-time Processing**: Sub-millisecond MIDI-latency with optimized threading
- **Color-coded Logging**: Comprehensive MIDI message monitoring with ANSI color support
- **Windows Native Integration**: Uses WinMM API for direct hardware access

## Requirements

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
java -jar target/sustsos-1.0-all.jar
```

### Windows Executable

The build process also creates a bundled Windows executable with embedded JRE:

```bash
mvn clean package -Pwindows
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

### Example Configurations

**Basic setup:**
```
In-port-1,In-port-2,Loopback-port,true,1,1,22
```

## Technical Details

### Sostenuto Logic

The application implements true sostenuto behavior:

1. **Latching**: When sostenuto is engaged, currently pressed/delayed notes are latched
2. **Selective Sustain**: Only latched notes continue sounding when keys are released
3. **Release**: When sostenuto is disengaged, latched notes are released (unless sustain is active)

### Umbrella Mode

Umbrella mode allows proper retriggering of sustained/sostenuto notes:

1. When a sustained note is played again, it's temporarily released
2. The new note-on is sent after a configurable delay
3. The note is automatically re-sustained after the tail duration
4. This prevents note stacking and maintains natural piano behavior

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

### Common Issues

1. **MIDI Device Not Found**: Ensure device names match exactly (case-insensitive)
2. **High Latency**: Check system performance and reduce logging verbosity
3. **No Sound Output**: Verify MIDI output device is properly connected and configured
4. **Crash on Startup**: Ensure Java 21+ is installed and in PATH

### Debug Mode

Set `USE_COLORS = false` in the source code to disable ANSI colors if terminal doesn't support them.

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── org/
│   │       └── nb/
│   │           └── SustSos.java    # Main application
│   └── resources/                  # Resource files
└── test/                           # Test files
```

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
mvn clean package -Pwindows
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
