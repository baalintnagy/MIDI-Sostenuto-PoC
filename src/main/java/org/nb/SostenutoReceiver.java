package org.nb;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

final class SostenutoReceiver implements Receiver {

    private static final int CHANNEL_COUNT = 16;
    private static final int NOTE_COUNT = 128;

    private static final int SUSTAIN_CONTROLLER = 64;
    private static final int SOSTENUTO_CONTROLLER = 66;
    private static final int SOFT_PEDAL_CONTROLLER = 69;
    private static final int RESET_ALL_CONTROLLERS = 121;
    private static final int ALL_NOTES_OFF = 123;
    private static final int ALL_SOUND_OFF = 120;

    private final Receiver output;
    private final SostenutoSettings settings;
    private final boolean[] sustainOn = new boolean[CHANNEL_COUNT];
    private final boolean[] sostenutoOn = new boolean[CHANNEL_COUNT];
    private final int[] lastSustainValue = new int[CHANNEL_COUNT];
    private final int[] lastSostenutoValue = new int[CHANNEL_COUNT];
    private final boolean[][] pressed = new boolean[CHANNEL_COUNT][NOTE_COUNT];
    private final boolean[][] latched = new boolean[CHANNEL_COUNT][NOTE_COUNT];
    private final boolean[][] delayedOff = new boolean[CHANNEL_COUNT][NOTE_COUNT];
    private final AtomicIntegerArray umbrellaDepth = new AtomicIntegerArray(CHANNEL_COUNT);
    private final ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("sost-umbrella").factory());

    private volatile boolean off;

    SostenutoReceiver(Receiver output, SostenutoSettings settings) {
        this.output = output;
        this.settings = settings;
        Arrays.fill(lastSustainValue, -1);
        Arrays.fill(lastSostenutoValue, -1);
    }

    @Override
    public void send(MidiMessage input, long timestamp) {
        if (off) {
            return;
        }

        try {
            if (!(input instanceof ShortMessage shortMessage)) {
                output.send(input, timestamp);
                return;
            }

            ShortMessage message = MidiMessages.copyOf(shortMessage);
            int channel = shortMessage.getChannel();

            switch (shortMessage.getCommand()) {
                case ShortMessage.CONTROL_CHANGE -> handleControlChange(message, channel, timestamp);
                case ShortMessage.NOTE_ON, ShortMessage.NOTE_OFF -> handleNote(message, channel, timestamp);
                default -> output.send(message, timestamp);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void handleControlChange(ShortMessage message, int channel, long timestamp) {
        int controller = message.getData1();
        if (controller == RESET_ALL_CONTROLLERS || controller == ALL_NOTES_OFF) {
            panic();
            return;
        }

        int value = message.getData2();
        if (handleController(
                SUSTAIN_CONTROLLER,
                controller,
                value,
                lastSustainValue,
                channel,
                () -> handleSustain(channel, value, timestamp))
            || handleController(
                SOSTENUTO_CONTROLLER,
                controller,
                value,
                lastSostenutoValue,
                channel,
                () -> handleSostenuto(channel, value, timestamp))) {
            return;
        }

        output.send(message, timestamp);
    }

    private static boolean handleController(
        int expectedController,
        int controller,
        int value,
        int[] previousValues,
        int channel,
        Runnable handler
    ) {
        if (controller != expectedController) {
            return false;
        }
        if (value == previousValues[channel]) {
            return true;
        }
        previousValues[channel] = value;
        handler.run();
        return true;
    }

    private void handleNote(ShortMessage message, int channel, long timestamp) {
        int note = message.getData1();
        int velocity = message.getData2();
        boolean noteOff = message.getCommand() == ShortMessage.NOTE_OFF || velocity == 0;

        if (!noteOff) {
            boolean needsUmbrella = shouldUmbrella(channel, note);
            pressed[channel][note] = true;
            if (needsUmbrella) {
                sendUmbrellaNoteOn(channel, note, velocity);
            } else {
                output.send(message, timestamp);
            }
            return;
        }

        pressed[channel][note] = false;
        if (sustainOn[channel] || (sostenutoOn[channel] && latched[channel][note])) {
            delayedOff[channel][note] = true;
            return;
        }

        delayedOff[channel][note] = false;
        output.send(message, timestamp);
    }

    private void handleSustain(int channel, int value, long timestamp) {
        boolean nowOn = value >= 64;
        if (nowOn && !sustainOn[channel]) {
            sustainOn[channel] = true;
            return;
        }
        if (nowOn || !sustainOn[channel]) {
            return;
        }

        sustainOn[channel] = false;
        IntStream.range(0, NOTE_COUNT)
            .filter(note -> delayedOff[channel][note]
                && !pressed[channel][note]
                && !(sostenutoOn[channel] && latched[channel][note]))
            .forEach(note -> sendNoteOff(channel, note, timestamp));
    }

    private void handleSostenuto(int channel, int value, long timestamp) {
        boolean wasOn = sostenutoOn[channel];
        int releaseThreshold = Math.max(0, settings.activationThreshold() - settings.hysteresis());
        boolean nowOn = wasOn ? value > releaseThreshold : value >= settings.activationThreshold();

        if (nowOn && !wasOn) {
            IntStream.range(0, NOTE_COUNT)
                .forEach(note -> latched[channel][note] = pressed[channel][note] || delayedOff[channel][note]);
            sostenutoOn[channel] = true;
            return;
        }
        if (nowOn || !wasOn) {
            return;
        }

        IntConsumer release = sustainOn[channel]
            ? note -> delayedOff[channel][note] = true
            : note -> sendNoteOff(channel, note, timestamp);
        IntStream.range(0, NOTE_COUNT)
            .filter(note -> latched[channel][note] && !pressed[channel][note])
            .forEach(release);
        Arrays.fill(latched[channel], false);
        sostenutoOn[channel] = false;
    }

    private void sendNoteOff(int channel, int note, long timestamp) {
        delayedOff[channel][note] = false;
        output.send(MidiMessages.noteOff(channel, note), timestamp);
    }

    void panic() {
        off = true;
        try {
            IntStream.range(0, umbrellaDepth.length()).forEach(channel -> umbrellaDepth.set(channel, 0));
            IntStream.range(0, CHANNEL_COUNT).forEach(channel -> {
                sendControlChange(channel, SUSTAIN_CONTROLLER, 0, -1);
                sendControlChange(channel, SOSTENUTO_CONTROLLER, 0, -1);
                sendControlChange(channel, SOFT_PEDAL_CONTROLLER, 0, -1);
                IntStream.range(0, NOTE_COUNT)
                    .filter(note -> pressed[channel][note]
                        || latched[channel][note]
                        || delayedOff[channel][note])
                    .forEach(note -> sendNoteOff(channel, note, -1));
                sendControlChange(channel, ALL_NOTES_OFF, 0, -1);
                sendControlChange(channel, RESET_ALL_CONTROLLERS, 0, -1);
                sendControlChange(channel, ALL_SOUND_OFF, 0, -1);
                clearChannel(channel);
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            off = false;
        }
    }

    private void clearChannel(int channel) {
        Arrays.fill(pressed[channel], false);
        Arrays.fill(latched[channel], false);
        Arrays.fill(delayedOff[channel], false);
        sustainOn[channel] = false;
        sostenutoOn[channel] = false;
        lastSustainValue[channel] = -1;
        lastSostenutoValue[channel] = -1;
    }

    private void sendControlChange(int channel, int controller, int value, long timestamp) {
        output.send(MidiMessages.controlChange(channel, controller, value), timestamp);
    }

    private boolean shouldUmbrella(int channel, int note) {
        return settings.umbrellaEnabled() && (delayedOff[channel][note] || latched[channel][note]);
    }

    private void sendUmbrellaNoteOn(int channel, int note, int velocity) {
        boolean needsPedalDown = umbrellaDepth.getAndIncrement(channel) == 0;
        if (needsPedalDown) {
            sendControlChange(channel, SUSTAIN_CONTROLLER, 127, -1);
        }
        scheduler.schedule(() -> {
            output.send(MidiMessages.noteOn(channel, note, velocity), -1);
            scheduler.schedule(
                () -> finishUmbrella(channel),
                settings.umbrellaTailMs(),
                TimeUnit.MILLISECONDS
            );
        }, settings.umbrellaDelayMs(), TimeUnit.MILLISECONDS);
    }

    private void finishUmbrella(int channel) {
        int depth = umbrellaDepth.decrementAndGet(channel);
        if (depth < 0) {
            umbrellaDepth.set(channel, 0);
            return;
        }
        if (depth == 0) {
            sendControlChange(channel, SUSTAIN_CONTROLLER, 0, -1);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
