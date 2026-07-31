package org.nb;

public enum MidiMessageType {

    NOTE_ON(0x90),
    NOTE_OFF(0x80),
    CC(0xB0),
    P_BEND(0xE0),
    CH_PR(0xD0),
    POLY_AT(0xA0),
    PC(0xC0),
    OTHER(0x00);

    private final int command;

    MidiMessageType(int command) {
        this.command = command;
    }

    public int command() {
        return command;
    }
}
