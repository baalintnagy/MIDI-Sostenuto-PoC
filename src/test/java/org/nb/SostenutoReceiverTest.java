package org.nb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SostenutoReceiverTest {

    private final RecordingReceiver output = new RecordingReceiver();
    private final SostenutoReceiver receiver = new SostenutoReceiver(
        output,
        new SostenutoSettings(10, 2, false, 0, 0)
    );

    @AfterEach
    void closeReceiver() {
        receiver.close();
    }

    @Test
    void passesOrdinaryNotesThroughUnchanged() {
        receiver.send(MidiMessages.noteOn(2, 60, 100), 10);
        receiver.send(MidiMessages.noteOff(2, 60), 20);

        assertEquals(List.of(
            new MidiEvent(ShortMessage.NOTE_ON, 2, 60, 100, 10),
            new MidiEvent(ShortMessage.NOTE_OFF, 2, 60, 0, 20)
        ), output.events());
    }

    @Test
    void delaysNoteOffUntilSustainIsReleased() {
        receiver.send(MidiMessages.noteOn(0, 60, 100), 10);
        receiver.send(MidiMessages.controlChange(0, 64, 127), 20);
        receiver.send(MidiMessages.noteOff(0, 60), 30);

        assertEquals(1, output.events().size());

        receiver.send(MidiMessages.controlChange(0, 64, 0), 40);

        assertEquals(List.of(
            new MidiEvent(ShortMessage.NOTE_ON, 0, 60, 100, 10),
            new MidiEvent(ShortMessage.NOTE_OFF, 0, 60, 0, 40)
        ), output.events());
    }

    @Test
    void sostenutoLatchesOnlyNotesHeldWhenPedalGoesDown() {
        receiver.send(MidiMessages.noteOn(0, 60, 100), 10);
        receiver.send(MidiMessages.controlChange(0, 66, 127), 20);
        receiver.send(MidiMessages.noteOn(0, 61, 100), 30);
        receiver.send(MidiMessages.noteOff(0, 60), 40);
        receiver.send(MidiMessages.noteOff(0, 61), 50);

        assertEquals(List.of(
            new MidiEvent(ShortMessage.NOTE_ON, 0, 60, 100, 10),
            new MidiEvent(ShortMessage.NOTE_ON, 0, 61, 100, 30),
            new MidiEvent(ShortMessage.NOTE_OFF, 0, 61, 0, 50)
        ), output.events());

        receiver.send(MidiMessages.controlChange(0, 66, 0), 60);

        assertEquals(
            new MidiEvent(ShortMessage.NOTE_OFF, 0, 60, 0, 60),
            output.events().get(3)
        );
    }

    @Test
    void handsDelayedNoteFromSustainToSostenuto() {
        receiver.send(MidiMessages.noteOn(0, 60, 100), 10);
        receiver.send(MidiMessages.controlChange(0, 64, 127), 20);
        receiver.send(MidiMessages.noteOff(0, 60), 30);
        receiver.send(MidiMessages.controlChange(0, 66, 127), 40);
        receiver.send(MidiMessages.controlChange(0, 64, 0), 50);

        assertEquals(1, output.events().size());

        receiver.send(MidiMessages.controlChange(0, 66, 0), 60);

        assertEquals(
            new MidiEvent(ShortMessage.NOTE_OFF, 0, 60, 0, 60),
            output.events().get(1)
        );
    }

    @Test
    void handsDelayedNoteFromSostenutoToSustain() {
        receiver.send(MidiMessages.noteOn(0, 60, 100), 10);
        receiver.send(MidiMessages.controlChange(0, 66, 127), 20);
        receiver.send(MidiMessages.noteOff(0, 60), 30);
        receiver.send(MidiMessages.controlChange(0, 64, 127), 40);
        receiver.send(MidiMessages.controlChange(0, 66, 0), 50);

        assertEquals(1, output.events().size());

        receiver.send(MidiMessages.controlChange(0, 64, 0), 60);

        assertEquals(
            new MidiEvent(ShortMessage.NOTE_OFF, 0, 60, 0, 60),
            output.events().get(1)
        );
    }

    @Test
    void umbrellaProtectsRepeatedNoteOnWithShortSustainPulse() throws InterruptedException {
        RecordingReceiver umbrellaOutput = new RecordingReceiver(4);
        SostenutoReceiver umbrellaReceiver = new SostenutoReceiver(
            umbrellaOutput,
            new SostenutoSettings(10, 2, true, 0, 0)
        );
        try {
            umbrellaReceiver.send(MidiMessages.noteOn(0, 60, 90), 10);
            umbrellaReceiver.send(MidiMessages.controlChange(0, 64, 127), 20);
            umbrellaReceiver.send(MidiMessages.noteOff(0, 60), 30);
            umbrellaReceiver.send(MidiMessages.noteOn(0, 60, 100), 40);

            assertTrue(umbrellaOutput.awaitEvents());
            assertEquals(List.of(
                new MidiEvent(ShortMessage.NOTE_ON, 0, 60, 90, 10),
                new MidiEvent(ShortMessage.CONTROL_CHANGE, 0, 64, 127, -1),
                new MidiEvent(ShortMessage.NOTE_ON, 0, 60, 100, -1),
                new MidiEvent(ShortMessage.CONTROL_CHANGE, 0, 64, 0, -1)
            ), umbrellaOutput.events());
        } finally {
            umbrellaReceiver.close();
        }
    }

    private record MidiEvent(int command, int channel, int data1, int data2, long timestamp) {
    }

    private static final class RecordingReceiver implements Receiver {
        private final List<MidiEvent> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch expectedEvents;

        RecordingReceiver() {
            this(0);
        }

        RecordingReceiver(int expectedEventCount) {
            expectedEvents = new CountDownLatch(expectedEventCount);
        }

        @Override
        public void send(MidiMessage message, long timestamp) {
            ShortMessage shortMessage = (ShortMessage) message;
            events.add(new MidiEvent(
                shortMessage.getCommand(),
                shortMessage.getChannel(),
                shortMessage.getData1(),
                shortMessage.getData2(),
                timestamp
            ));
            expectedEvents.countDown();
        }

        List<MidiEvent> events() {
            return events;
        }

        boolean awaitEvents() throws InterruptedException {
            return expectedEvents.await(1, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
        }
    }
}
