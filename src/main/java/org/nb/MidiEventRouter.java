package org.nb;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;

final class MidiEventRouter {

    private static final int SOSTENUTO_CONTROLLER = 66;

    private final AppConfig config;
    private final MidiLogger logger;
    private final Receiver receiver;

    MidiEventRouter(AppConfig config, MidiLogger logger, Receiver receiver) {
        this.config = config;
        this.logger = logger;
        this.receiver = receiver;
    }

    void routePrimary(int value) {
        logger.log(value, config.primaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        boolean remapSostenuto = !config.hasSecondaryInput()
            && message.isControlChange(config.sostenutoController());
        forward(message, remapSostenuto);
    }

    void routeSecondary(int value) {
        logger.log(value, config.secondaryInputName());
        PackedMidiMessage message = new PackedMidiMessage(value);
        if (message.isControlChange(config.sostenutoController())) {
            forward(message, true);
        }
    }

    private void forward(PackedMidiMessage message, boolean remapSostenuto) {
        try {
            if (remapSostenuto) {
                receiver.send(
                    MidiMessages.controlChange(message.channel(), SOSTENUTO_CONTROLLER, message.data2()),
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
