package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

final class MidiEventRouter {

    private static final int SOSTENUTO_CC = 66;

    private final AppConfig config;
    private final MidiLogger logger;
    private final Receiver receiver;
    private final boolean[] notePressed = new boolean[16];
    private final boolean[] noteToggledOn = new boolean[16];

    MidiEventRouter(AppConfig config, MidiLogger logger, Receiver receiver) {
        this.config = config;
        this.logger = logger;
        this.receiver = receiver;
    }

    void routePrimary(int value) {
        logger.log(value, config.primaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        boolean remapSostenuto = !config.hasSecondaryInput()
            && isSostenutoInput(message);
        forward(message, remapSostenuto);
    }

    void routeSecondary(int value) {
        logger.log(value, config.secondaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        if (isSostenutoInput(message)) {
            forward(message, true);
        }
    }

    private boolean isSostenutoInput(PackedMidiMessage message) {
        if (config.sostenutoNote() == null) {
            return message.isControlChange(config.sostenutoCC());
        }
        return message.data1() == config.sostenutoNote()
            && (message.command() == ShortMessage.NOTE_ON || message.command() == ShortMessage.NOTE_OFF);
    }

    private void forward(PackedMidiMessage message, boolean remapSostenuto) {
        try {
            if (remapSostenuto) {
                int value = message.data2();
                if (config.sostenutoNote() != null) {
                    boolean pressed = message.command() == ShortMessage.NOTE_ON && message.data2() > 0;
                    int channel = message.channel();
                    if (config.sostenutoNoteToggle()) {
                        boolean wasPressed = notePressed[channel];
                        notePressed[channel] = pressed;
                        if (!pressed || wasPressed) {
                            return;
                        }
                        noteToggledOn[channel] = !noteToggledOn[channel];
                        value = noteToggledOn[channel] ? 127 : 0;
                    } else {
                        value = pressed ? 127 : 0;
                    }
                }
                receiver.send(
                    MidiMessages.controlChange(message.channel(), SOSTENUTO_CC,
                        value),
                    -1
                );
            } else {
                receiver.send(message.toShortMessage(), -1);
            }
        } catch (InvalidMidiDataException exception) {
            exception.printStackTrace();
        }
    }
}
