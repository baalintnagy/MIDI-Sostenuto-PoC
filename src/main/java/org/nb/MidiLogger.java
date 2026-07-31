package org.nb;

import static org.nb.AnsiColor.RESET;
import static org.nb.ColorMappings.MIDI_MSG_COLOR;
import static org.nb.ColorMappings.MIDI_MSG_LOOKUP;
import static org.nb.ColorMappings.getAccentColor;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class MidiLogger implements AutoCloseable {

    private static final String RAW_COLORED_FORMAT = """
        ${RESET}CH:${RESET} %s%2d${RESET} ${RESET}|${RESET} %s%-9s${RESET} ${RESET}|${RESET} \
        ${RESET}D1:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        ${RESET}D2:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        ${RESET}Status:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        %s%s${RESET} ${RESET}|${RESET} %s%s${RESET} \
        """;
    private static final String COLORED_FORMAT = RAW_COLORED_FORMAT.replace("${RESET}", RESET);
    private static final String PLAIN_FORMAT =
        "CH: %2d | %-9s | D1: %3d | D2: %3d | Status: %3d | [%s] | %s ns%n";

    private final BlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>();
    private final PrintStream output;
    private final boolean colorsEnabled;

    private volatile boolean running;
    private Thread loggerThread;

    MidiLogger(PrintStream output, boolean colorsEnabled) {
        this.output = output;
        this.colorsEnabled = colorsEnabled;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        loggerThread = new Thread(this::processEntries, "midi-logger");
        loggerThread.setDaemon(true);
        loggerThread.start();
    }

    void log(int packedMessage, String inputName) {
        queue.offer(new LogEntry(new PackedMidiMessage(packedMessage), inputName));
    }

    private void processEntries() {
        while (running) {
            try {
                write(queue.take());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void write(LogEntry entry) {
        PackedMidiMessage message = entry.message();
        int channel = message.channel() + 1;
        long now = System.nanoTime();
        MidiMessageType messageType = MIDI_MSG_LOOKUP.getOrDefault(
            message.command(),
            MidiMessageType.OTHER
        );

        if (!colorsEnabled) {
            output.printf(
                PLAIN_FORMAT,
                channel,
                messageType,
                message.data1(),
                message.data2(),
                message.status(),
                entry.inputName(),
                formatNanoseconds(now)
            );
            return;
        }

        String messageColor = MIDI_MSG_COLOR.getOrDefault(messageType, AnsiColor.RED.bright());
        output.println(COLORED_FORMAT.formatted(
            AnsiColor.WHITE.dark(), channel,
            messageColor, messageType,
            getAccentColor(AnsiColor.YELLOW, messageType), message.data1(),
            getAccentColor(AnsiColor.GREEN, messageType), message.data2(),
            getAccentColor(AnsiColor.RED, messageType), message.status(),
            AnsiColor.BLUE.bright(), entry.inputName(),
            AnsiColor.GRAY.bright(), formatNanoseconds(now) + " ns"
        ));
    }

    private static String formatNanoseconds(long nanoseconds) {
        return "%,d".formatted(nanoseconds).replace(",", " ");
    }

    @Override
    public void close() {
        running = false;
        if (loggerThread != null) {
            loggerThread.interrupt();
        }
    }

    private record LogEntry(PackedMidiMessage message, String inputName) {
    }
}
