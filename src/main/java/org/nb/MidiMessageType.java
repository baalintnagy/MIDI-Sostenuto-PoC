package org.nb;

public enum MidiMessageType {

    NOTE_ON(0x90),
    NOTE_OFF(0x80),
    CC(0xB0),
    P_BEND(0xE0),
    CH_PR(0xD0),
    POLY_AT(0xA0),
    PC(0xC0),
    SYSEX_START(0xF0),
    TIME_CODE(0xF1),
    SONG_POSITION(0xF2),
    SONG_SELECT(0xF3),
    TUNE_REQUEST(0xF6),
    SYSEX_END(0xF7),
    CLOCK(0xF8),
    START(0xFA),
    CONTINUE(0xFB),
    STOP(0xFC),
    ACTIVE_SENSING(0xFE),
    SYSTEM_RESET(0xFF),
    OTHER(0x00);

    private final int command;

    MidiMessageType(int command) {
        this.command = command;
    }

    static MidiMessageType from(PackedMidiMessage message) {
        return ColorMappings.MIDI_MSG_LOOKUP.getOrDefault(
            message.status() >= 0xF0 ? message.status() : message.command(), OTHER);
    }

    public int command() {
        return command;
    }
}
