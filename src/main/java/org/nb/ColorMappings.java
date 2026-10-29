package org.nb;

import java.util.Map;
import java.util.Set;

public final class ColorMappings {

    public static final Map<Integer, MidiMessageType> MIDI_MSG_LOOKUP =
        java.util.Arrays.stream(MidiMessageType.values()).collect(
            java.util.stream.Collectors.toUnmodifiableMap(MidiMessageType::command, type -> type));
    public static final Map<MidiMessageType, String> MIDI_MSG_COLOR = Map.of(
        MidiMessageType.NOTE_ON, AnsiColor.CYAN.bright(),
        MidiMessageType.NOTE_OFF, AnsiColor.CYAN.dark(),
        MidiMessageType.CC, AnsiColor.YELLOW.bright(),
        MidiMessageType.P_BEND, AnsiColor.YELLOW.dark(),
        MidiMessageType.CH_PR, AnsiColor.MAGENTA.bright(),
        MidiMessageType.POLY_AT, AnsiColor.MAGENTA.bright(),
        MidiMessageType.PC, AnsiColor.MAGENTA.bright());

    private static final Set<MidiMessageType> DARK_MESSAGE_TYPES = Set.of(
        MidiMessageType.NOTE_OFF,
        MidiMessageType.P_BEND,
        MidiMessageType.CH_PR);

    private ColorMappings() {
    }

    public static String getAccentColor(AnsiColor color, MidiMessageType type) {
        return DARK_MESSAGE_TYPES.contains(type) ? color.dark() : color.bright();
    }

}
