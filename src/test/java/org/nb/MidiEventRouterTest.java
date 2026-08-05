package org.nb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import org.junit.jupiter.api.Test;

class MidiEventRouterTest {

    private final MidiLogger logger = new MidiLogger(new PrintStream(OutputStream.nullOutputStream()), false);
    private final RecordingReceiver output = new RecordingReceiver();

    @Test
    void remapsConfiguredControllerOnPrimaryWhenThereIsNoSecondaryInput() {
        AppConfig config = new AppConfig("Keys", "", "Out", false, 0, 0, 22);
        MidiEventRouter router = new MidiEventRouter(config, logger, output);

        router.routePrimary(PackedMidiMessage.from(MidiMessages.controlChange(3, 22, 127)).value());

        assertEquals(List.of(new MidiData(ShortMessage.CONTROL_CHANGE, 3, 66, 127)), output.messages());
    }

    @Test
    void secondaryInputFiltersEverythingExceptConfiguredController() {
        AppConfig config = new AppConfig("Keys", "Pedals", "Out", false, 0, 0, 22);
        MidiEventRouter router = new MidiEventRouter(config, logger, output);

        router.routeSecondary(PackedMidiMessage.from(MidiMessages.noteOn(0, 60, 100)).value());
        router.routeSecondary(PackedMidiMessage.from(MidiMessages.controlChange(1, 21, 127)).value());
        router.routeSecondary(PackedMidiMessage.from(MidiMessages.controlChange(2, 22, 64)).value());

        assertEquals(List.of(new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 64)), output.messages());
    }

    @Test
    void primaryInputDoesNotRemapWhenSecondaryInputIsConfigured() {
        AppConfig config = new AppConfig("Keys", "Pedals", "Out", false, 0, 0, 22);
        MidiEventRouter router = new MidiEventRouter(config, logger, output);

        router.routePrimary(PackedMidiMessage.from(MidiMessages.controlChange(0, 22, 127)).value());

        assertEquals(List.of(new MidiData(ShortMessage.CONTROL_CHANGE, 0, 22, 127)), output.messages());
    }

    private record MidiData(int command, int channel, int data1, int data2) {
    }

    private static final class RecordingReceiver implements Receiver {
        private final List<MidiData> messages = new ArrayList<>();

        @Override
        public void send(MidiMessage message, long timestamp) {
            ShortMessage shortMessage = (ShortMessage) message;
            messages.add(new MidiData(
                shortMessage.getCommand(),
                shortMessage.getChannel(),
                shortMessage.getData1(),
                shortMessage.getData2()
            ));
        }

        List<MidiData> messages() {
            return messages;
        }

        @Override
        public void close() {
        }
    }
}
