package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

record PackedMidiMessage(int value) {

    static PackedMidiMessage from(ShortMessage message) {
        int packed = (message.getStatus() & 0xFF)
            | ((message.getData1() & 0xFF) << 8)
            | ((message.getData2() & 0xFF) << 16);
        return new PackedMidiMessage(packed);
    }

    int status() {
        return value & 0xFF;
    }

    int command() {
        return status() & 0xF0;
    }

    int channel() {
        return status() & 0x0F;
    }

    int data1() {
        return (value >> 8) & 0xFF;
    }

    int data2() {
        return (value >> 16) & 0xFF;
    }

    boolean isControlChange(int controller) {
        return command() == ShortMessage.CONTROL_CHANGE && data1() == controller;
    }

    ShortMessage toShortMessage() throws InvalidMidiDataException {
        ShortMessage message = new ShortMessage();
        message.setMessage(status(), data1(), data2());
        return message;
    }
}
