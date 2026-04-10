package org.nb;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Mappings() {

    public static final Map<Integer, MidiMessageType> MIDI_MSG_LOOKUP = Arrays.stream(
        MidiMessageType.values())
        .collect(Collectors.toUnmodifiableMap(
            MidiMessageType::getCmd,
            Function.identity()));

    public static final Map<MidiMessageType, String> MIDI_MSG_COLOR = Map.of(
        MidiMessageType.NOTE_ON, AnsiColor.CYAN.bright(),
        MidiMessageType.NOTE_OFF, AnsiColor.CYAN.dark(),
        MidiMessageType.CC, AnsiColor.YELLOW.bright(),
        MidiMessageType.P_BEND, AnsiColor.YELLOW.dark(),
        MidiMessageType.CH_PR, AnsiColor.MAGENTA.bright(),
        MidiMessageType.POLY_AT, AnsiColor.MAGENTA.bright(),
        MidiMessageType.PC, AnsiColor.MAGENTA.bright());

    protected static final Set<MidiMessageType> DARKS = Set.of(
        MidiMessageType.NOTE_OFF,
        MidiMessageType.P_BEND,
        MidiMessageType.CH_PR);

    public static String getAccentColor(AnsiColor color, MidiMessageType type) {
        return DARKS.contains(type) ? color.dark() : color.bright();
    }

}
