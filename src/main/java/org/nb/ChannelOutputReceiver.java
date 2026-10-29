package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/** Applies the channel override to all output, including generated pedal and panic messages. */
final class ChannelOutputReceiver implements Receiver {
    private final Receiver output;
    private final Integer channel;

    ChannelOutputReceiver(Receiver output, Integer channel) {
        if (channel != null && (channel < 1 || channel > 16)) {
            throw new IllegalArgumentException("outputChannel must be null or 1..16");
        }
        this.output = output;
        this.channel = channel;
    }

    @Override
    public void send(MidiMessage message, long timestamp) {
        if (channel != null && message instanceof ShortMessage shortMessage && shortMessage.getStatus() < 0xF0) {
            try {
                ShortMessage remapped = new ShortMessage();
                remapped.setMessage(shortMessage.getCommand(), channel - 1,
                    shortMessage.getData1(), shortMessage.getData2());
                output.send(remapped, timestamp);
            } catch (InvalidMidiDataException exception) {
                throw new IllegalArgumentException("Cannot remap MIDI output channel", exception);
            }
        } else {
            output.send(message, timestamp);
        }
    }

    @Override
    public void close() {
        output.close();
    }
}
