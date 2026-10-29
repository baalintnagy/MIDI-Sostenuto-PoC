package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

final class MidiEventRouter {

    private static final int SOSTENUTO_CC = 66;

    private final AppConfig config;
    private final MidiLogger logger;
    private final Receiver receiver;
    private final SostenutoSettings settings;
    private final boolean[] triggerPressed = new boolean[16];
    private final boolean[] triggerToggledOn = new boolean[16];

    MidiEventRouter(AppConfig config, MidiLogger logger, Receiver receiver) {
        this.config = config;
        this.settings = config.sostenutoSettings();
        this.logger = logger;
        this.receiver = receiver;
    }

    void routePrimary(int value) {
        logger.log(value, config.primaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        if (!acceptsChannel(message)) return;
        boolean remapSostenuto = !config.hasSecondaryInput()
            && isSostenutoInput(message);
        forward(message, remapSostenuto);
    }

    void routeSecondary(int value) {
        logger.log(value, config.secondaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        if (!acceptsChannel(message)) return;
        if (isSostenutoInput(message)) {
            forward(message, true);
        }
    }

    private boolean acceptsChannel(PackedMidiMessage message) {
        return config.inputChannel() == null || message.status() >= 0xF0
            || message.channel() + 1 == config.inputChannel();
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
                boolean noteInput = config.sostenutoNote() != null;
                if (!noteInput && Boolean.TRUE.equals(config.invertCC())) {
                    value = 127 - value;
                }
                int channel = message.channel();
                if (config.sostenutoToggle()) {
                    boolean wasPressed = triggerPressed[channel];
                    int releaseThreshold = Math.max(0, settings.activationThreshold() - settings.hysteresis());
                    boolean pressed = noteInput
                        ? message.command() == ShortMessage.NOTE_ON && value > 0
                        : wasPressed ? value > releaseThreshold : value >= settings.activationThreshold();
                    triggerPressed[channel] = pressed;
                    if (!pressed || wasPressed) {
                        return;
                    }
                    triggerToggledOn[channel] = !triggerToggledOn[channel];
                    value = triggerToggledOn[channel] ? 127 : 0;
                } else if (noteInput) {
                    value = message.command() == ShortMessage.NOTE_ON && value > 0 ? 127 : 0;
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
