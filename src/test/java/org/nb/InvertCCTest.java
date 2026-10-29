package org.nb;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvertCCTest {
    @TempDir Path directory;

    @Test
    void jsonAcceptsOptionalNullableBooleanAndRejectsOtherTypes() throws Exception {
        Path path = directory.resolve("invert.json");
        for (String value : new String[] {"null", "false", "true", "\"true\"", "1"}) {
            String base = AppConfigTest.CONFIG_JSON.stripTrailing();
            Files.writeString(path, base.substring(0, base.length() - 1) + ",\"invertCC\":" + value + "}");
            if (value.equals("1") || value.equals("\"true\"")) {
                assertThrows(IOException.class, () -> AppConfig.load(path));
            } else {
                assertEquals(value.equals("null") ? null : Boolean.valueOf(value), AppConfig.load(path).invertCC());
            }
        }
        Files.writeString(path, AppConfigTest.CONFIG_JSON);
        assertNull(AppConfig.load(path).invertCC());
    }

    @Test
    void holdInvertsOnlyTheConfiguredCCOnEitherInputShape() {
        for (String secondary : new String[] {"", "Pedals"}) {
            var sink = new Sink();
            var router = router(secondary, false, null, true, sink);
            java.util.function.IntConsumer route = secondary.isEmpty() ? router::routePrimary : router::routeSecondary;
            for (int value : new int[] {127, 0, 64, 126}) route.accept(cc(22, value));
            assertEquals(List.of(0, 127, 63, 1), sink.values());
            router.routePrimary(cc(21, 25));
            assertEquals(25, sink.messages.getLast().getData2());
            assertEquals(21, sink.messages.getLast().getData1());
        }
    }

    @Test
    void invertedToggleUsesTransformedPressEdgesAndHysteresis() {
        var sink = new Sink();
        var router = router("", true, null, true, sink);
        // Normalized values: 0, 9, 10, 127, 9, 8, 9, 10.
        for (int value : new int[] {127, 118, 117, 0, 118, 119, 118, 117}) {
            router.routePrimary(cc(22, value));
        }
        assertEquals(List.of(127, 0), sink.values());
    }

    @Test
    void falseAndNullPreserveCCAndNoteModeIgnoresInversion() {
        for (Boolean invert : new Boolean[] {null, false}) {
            var sink = new Sink();
            var router = router("", false, null, invert, sink);
            router.routePrimary(cc(22, 25));
            assertEquals(List.of(25), sink.values());
        }
        for (boolean toggle : new boolean[] {false, true}) {
            var sink = new Sink();
            var router = router("", toggle, 78, true, sink);
            for (int velocity : new int[] {1, 0, 1, 0}) {
                router.routePrimary(PackedMidiMessage.from(MidiMessages.noteOn(0, 78, velocity)).value());
            }
            assertEquals(toggle ? List.of(127, 0) : List.of(127, 0, 127, 0), sink.values());
        }
    }

    private MidiEventRouter router(String secondary, boolean toggle, Integer note, Boolean invert, Sink sink) {
        return new MidiEventRouter(new AppConfig("Keys", secondary, "Out", false, 0, 0, 22,
            toggle, invert, note, null, null, LogFilter.NONE),
            new MidiLogger(new PrintStream(OutputStream.nullOutputStream()), false), sink);
    }

    private int cc(int cc, int value) {
        return PackedMidiMessage.from(MidiMessages.controlChange(0, cc, value)).value();
    }

    private static class Sink implements Receiver {
        final List<ShortMessage> messages = new ArrayList<>();
        public void send(MidiMessage message, long timestamp) { messages.add((ShortMessage) message); }
        public void close() {}
        List<Integer> values() { return messages.stream().map(ShortMessage::getData2).toList(); }
    }
}
