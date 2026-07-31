package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

final class MidiMessages {

    private MidiMessages() {
    }

    static ShortMessage controlChange(int channel, int controller, int value) {
        return create(ShortMessage.CONTROL_CHANGE, channel, controller, value);
    }

    static ShortMessage noteOn(int channel, int note, int velocity) {
        return create(ShortMessage.NOTE_ON, channel, note, velocity);
    }

    static ShortMessage noteOff(int channel, int note) {
        return create(ShortMessage.NOTE_OFF, channel, note, 0);
    }

    static ShortMessage copyOf(ShortMessage source) {
        try {
            ShortMessage copy = new ShortMessage();
            copy.setMessage(source.getStatus(), source.getData1(), source.getData2());
            return copy;
        } catch (InvalidMidiDataException exception) {
            throw new IllegalArgumentException("Invalid MIDI message", exception);
        }
    }

    private static ShortMessage create(int command, int channel, int data1, int data2) {
        try {
            ShortMessage message = new ShortMessage();
            message.setMessage(command, channel, data1, data2);
            return message;
        } catch (InvalidMidiDataException exception) {
            throw new IllegalArgumentException("Invalid MIDI message data", exception);
        }
    }
}
