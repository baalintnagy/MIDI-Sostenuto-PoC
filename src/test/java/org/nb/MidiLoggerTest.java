package org.nb;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MidiLoggerTest {
    @Test
    void namesSystemMessagesInPlainAndColoredOutput() throws Exception {
        int[] statuses = {0xF0, 0xF1, 0xF2, 0xF3, 0xF6, 0xF7, 0xF8, 0xFA, 0xFB, 0xFC, 0xFE, 0xFF};
        String[] names = {"SYSEX_START", "TIME_CODE", "SONG_POSITION", "SONG_SELECT", "TUNE_REQUEST",
            "SYSEX_END", "CLOCK", "START", "CONTINUE", "STOP", "ACTIVE_SENSING", "SYSTEM_RESET"};
        for (boolean colors : new boolean[] {false, true}) {
            String[] lines = render(statuses, colors);
            for (int i = 0; i < statuses.length; i++) {
                assertTrue(lines[i].contains(names[i]), lines[i]);
                assertTrue(lines[i].contains("CH: --"), lines[i]);
                assertTrue(lines[i].contains("Status: " + statuses[i]), lines[i]);
            }
        }
    }

    @Test
    void preservesChannelMessagesAndUsesOtherForUndefinedStatuses() throws Exception {
        String[] lines = render(new int[] {0x90, 0x8F, 0xB3, 0xF4, 0xF9, 0xFD}, false);
        assertTrue(lines[0].contains("CH:  1 | NOTE_ON"));
        assertTrue(lines[1].contains("CH: 16 | NOTE_OFF"));
        assertTrue(lines[2].contains("CH:  4 | CC"));
        for (int i = 3; i < lines.length; i++) {
            assertTrue(lines[i].contains("CH: -- | OTHER"));
        }
    }

    private String[] render(int[] statuses, boolean colors) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var written = new CountDownLatch(statuses.length);
        var sink = new OutputStream() {
            @Override public void write(int value) {
                bytes.write(value);
                if (value == '\n') written.countDown();
            }
        };
        try (var logger = new MidiLogger(new PrintStream(sink), colors)) {
            logger.start();
            for (int status : statuses) logger.log(status, "Test Port");
            assertTrue(written.await(5, TimeUnit.SECONDS), "Logger did not finish writing");
        }
        return bytes.toString().replaceAll("\u001B\\[[0-9;]*m", "").lines().toArray(String[]::new);
    }
}
