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

class ChannelConfigurationTest {
    @TempDir Path directory;

    @Test
    void loadsNullableChannelsAndRejectsInvalidValues() throws Exception {
        for (String value : new String[] {"null", "1", "16"}) {
            AppConfig config = load(value, value);
            assertEquals(value.equals("null") ? null : Integer.valueOf(value), config.inputChannel());
            assertEquals(config.inputChannel(), config.outputChannel());
        }
        for (String invalid : new String[] {"0", "17", "-1", "\"1\"", "1.5"}) {
            assertThrows(IOException.class, () -> load(invalid, "null"));
            assertThrows(IOException.class, () -> load("null", invalid));
        }
        AppConfig manual = AppConfig.parse("Keys,Out,false,0,0,22");
        assertNull(manual.inputChannel());
        assertNull(manual.outputChannel());
    }

    private AppConfig load(String input, String output) throws IOException {
        String base = AppConfigTest.CONFIG_JSON.stripTrailing();
        Path path = directory.resolve("channels.json");
        Files.writeString(path, base.substring(0, base.length() - 1)
            + ",\"inputChannel\":" + input + ",\"outputChannel\":" + output + "}");
        return AppConfig.load(path);
    }

    @Test
    void filtersBothInputPortsButAllowsPrimarySystemMessages() throws Exception {
        var sink = new Sink();
        var config = new AppConfig("Keys", "Pedals", "Out", false, 0, 0, 22, false, null, 2, null, LogFilter.NONE);
        var logger = new MidiLogger(new PrintStream(OutputStream.nullOutputStream()), false);
        var router = new MidiEventRouter(config, logger, sink);
        router.routePrimary(PackedMidiMessage.from(MidiMessages.noteOn(0, 60, 100)).value());
        router.routePrimary(PackedMidiMessage.from(MidiMessages.noteOn(1, 61, 100)).value());
        router.routeSecondary(PackedMidiMessage.from(MidiMessages.controlChange(0, 22, 127)).value());
        router.routeSecondary(PackedMidiMessage.from(MidiMessages.controlChange(1, 22, 127)).value());
        router.routePrimary(0xF8);
        assertEquals(3, sink.messages.size());
        assertEquals(61, sink.messages.get(0).getData1());
        assertEquals(66, sink.messages.get(1).getData1());
        assertEquals(0xF8, sink.messages.get(2).getStatus());
    }

    @Test
    void remapsEveryChannelMessageWithoutMutatingInputAndPreservesSystemMessages() throws Exception {
        var sink = new Sink();
        var output = new ChannelOutputReceiver(sink, 16);
        for (int command = 0x80; command <= 0xE0; command += 0x10) {
            ShortMessage source = new ShortMessage(command, 2, 60, 100);
            output.send(source, 123);
            assertEquals(2, source.getChannel());
            assertEquals(15, sink.messages.getLast().getChannel());
            assertEquals(command, sink.messages.getLast().getCommand());
        }
        ShortMessage clock = new ShortMessage(0xF8);
        output.send(clock, 123);
        assertSame(clock, sink.messages.getLast());
        assertEquals(123, sink.timestamp);
        output.close();
        assertTrue(sink.closed);
    }

    @Test
    void nullOutputChannelPreservesMessages() {
        var sink = new Sink();
        var output = new ChannelOutputReceiver(sink, null);
        ShortMessage message = MidiMessages.noteOn(5, 60, 100);
        output.send(message, -1);
        assertSame(message, sink.messages.getFirst());
    }

    @Test
    void generatedNoteOffAndPanicMessagesUseOutputChannel() {
        var sink = new Sink();
        try (var transformer = new SostenutoReceiver(new ChannelOutputReceiver(sink, 4),
                new SostenutoSettings(10, 2, false, 0, 0))) {
            transformer.send(MidiMessages.noteOn(0, 60, 100), -1);
            transformer.send(MidiMessages.controlChange(0, 66, 127), -1);
            transformer.send(MidiMessages.noteOff(0, 60), -1);
            assertEquals(1, sink.messages.size());
            transformer.send(MidiMessages.controlChange(0, 66, 0), -1);
            assertEquals(ShortMessage.NOTE_OFF, sink.messages.getLast().getCommand());
            transformer.panic();
            assertTrue(sink.messages.stream().allMatch(message -> message.getChannel() == 3));
        }
    }

    private static class Sink implements Receiver {
        final List<ShortMessage> messages = new ArrayList<>();
        long timestamp;
        boolean closed;
        public void send(MidiMessage message, long timestamp) {
            messages.add((ShortMessage) message);
            this.timestamp = timestamp;
        }
        public void close() { closed = true; }
    }
}
