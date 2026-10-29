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
    void remapsConfiguredCCOnPrimaryWhenThereIsNoSecondaryInput() {
        AppConfig config = new AppConfig("Keys", "", "Out", false, 0, 0, 22);
        MidiEventRouter router = new MidiEventRouter(config, logger, output);

        router.routePrimary(PackedMidiMessage.from(MidiMessages.controlChange(3, 22, 127)).value());

        assertEquals(List.of(new MidiData(ShortMessage.CONTROL_CHANGE, 3, 66, 127)), output.messages());
    }

    @Test
    void secondaryInputFiltersEverythingExceptConfiguredCC() {
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

    @Test
    void noteControlHandlesLowVelocityExplicitOffAndZeroVelocityOn() {
        for (String secondary : new String[] {"", "Pedals"}) {
            MidiEventRouter router = new MidiEventRouter(
                new AppConfig("Keys", secondary, "Out", false, 0, 0, 22, 60), logger, output);
            java.util.function.IntConsumer route = secondary.isEmpty() ? router::routePrimary : router::routeSecondary;
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 1)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOff(2, 60)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 127)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 0)).value());
        }
        List<MidiData> cycle = List.of(
            new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 127),
            new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 0),
            new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 127),
            new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 0));
        assertEquals(java.util.stream.Stream.concat(cycle.stream(), cycle.stream()).toList(), output.messages());
    }

    @Test
    void noteToggleChangesOncePerPressAndKeepsChannelsIndependent() {
        for (String secondary : new String[] {"", "Pedals"}) {
            output.messages().clear();
            MidiEventRouter router = new MidiEventRouter(
                new AppConfig("Keys", secondary, "Out", false, 0, 0, 22, 60, true), logger, output);
            java.util.function.IntConsumer route = secondary.isEmpty() ? router::routePrimary : router::routeSecondary;
            route.accept(PackedMidiMessage.from(MidiMessages.noteOff(2, 60)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 1)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 100)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOff(2, 60)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(3, 60, 100)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 100)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 0)).value());
            route.accept(PackedMidiMessage.from(MidiMessages.noteOn(2, 60, 100)).value());
            assertEquals(List.of(
                new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 127),
                new MidiData(ShortMessage.CONTROL_CHANGE, 3, 66, 127),
                new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 0),
                new MidiData(ShortMessage.CONTROL_CHANGE, 2, 66, 127)), output.messages());
        }
    }

    @Test
    void blacklistingControlLogsDoesNotChangeMidiRouting() {
        var filter = new LogFilter(List.of(new LogFilter.Rule("CC", null, null, null, null)));
        try (var filteredLogger = new MidiLogger(new PrintStream(OutputStream.nullOutputStream()), false, filter)) {
            MidiEventRouter router = new MidiEventRouter(
                new AppConfig("Keys", "", "Out", false, 0, 0, 22), filteredLogger, output);
            router.routePrimary(PackedMidiMessage.from(MidiMessages.controlChange(3, 22, 127)).value());
            assertEquals(List.of(new MidiData(ShortMessage.CONTROL_CHANGE, 3, 66, 127)), output.messages());
        }
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
