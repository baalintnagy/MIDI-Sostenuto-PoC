package org.nb;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.nb.AnsiColor.RESET;
import static org.nb.Mappings.MIDI_MSG_COLOR;
import static org.nb.Mappings.MIDI_MSG_LOOKUP;
import static org.nb.Mappings.getAccentColor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

public class SustSos {

    // ---------- Config ----------

    private static String IN_NAME = "Hammer 88 Pro";
    private static String IN2_NAME = "MIDIIN3 (Hammer 88 Pro)"; // optional second input
    private static String OUT_NAME = "postSSB";

    private static int CC66_THRESH = 10;
    private static int CC66_HYST = 2;

    private static boolean UMBRELLA = true;
    private static int UMBRELLA_DELAY_MS = 1;
    private static int UMBRELLA_TAIL_MS = 1;
    private static int SOST_CC_IN = 22; // CC from in2 remapped to CC66

    // ---------- JNA callbacks held at class level to prevent GC ----------

    private static WinMM.MidiInProc callback1 = null;
    private static WinMM.MidiInProc callback2 = null;

    // ---------- WinMM JNA interface ----------

    public interface WinMM extends Library {
        WinMM INSTANCE = Native.load("winmm", WinMM.class);

        int midiInGetNumDevs();

        int midiInGetDevCapsA(int deviceId, MIDIINCAPS caps, int size);

        int midiInOpen(PointerByReference handle, int deviceId,
            MidiInProc callback, Pointer instance, int flags);

        int midiInStart(Pointer handle);

        int midiInStop(Pointer handle);

        int midiInClose(Pointer handle);

        int midiOutGetNumDevs();

        int midiOutGetDevCapsA(int deviceId, MIDIOUTCAPS caps, int size);

        int midiOutOpen(PointerByReference handle, int deviceId,
            Pointer callback, Pointer instance, int flags);

        int midiOutShortMsg(Pointer handle, int msg);

        int midiOutClose(Pointer handle);

        interface MidiInProc extends Callback {
            void invoke(Pointer handle, int msg,
                Pointer instance, Pointer param1, Pointer param2);
        }

        @Structure.FieldOrder({ "wMid", "wPid", "vDriverVersion", "szPname" })
        class MIDIINCAPS extends Structure {
            public short wMid, wPid;
            public int vDriverVersion;
            public byte[] szPname = new byte[32];

            public String name() {
                return Native.toString(szPname);
            }
        }

        @Structure.FieldOrder({ "wMid", "wPid", "vDriverVersion", "szPname",
            "wTechnology", "wVoices", "wNotes", "wChannelMask", "dwSupport" })
        class MIDIOUTCAPS extends Structure {
            public short wMid, wPid;
            public int vDriverVersion;
            public byte[] szPname = new byte[32];
            public short wTechnology, wVoices, wNotes, wChannelMask;
            public int dwSupport;

            public String name() {
                return Native.toString(szPname);
            }
        }
    }

    static final int CALLBACK_FUNCTION = 0x30000;
    static final int MIM_DATA = 0x3C3;

    // ---------- WinMM output Receiver ----------

    static final class WinMMReceiver implements Receiver {
        private final Pointer handle;

        WinMMReceiver(Pointer handle) {
            this.handle = handle;
        }

        @Override
        public void send(MidiMessage msg, long ts) {
            if (msg instanceof ShortMessage sm) {
                int packed = (sm.getStatus() & 0xFF)
                    | ((sm.getData1() & 0xFF) << 8)
                    | ((sm.getData2() & 0xFF) << 16);
                WinMM.INSTANCE.midiOutShortMsg(handle, packed);
            }
        }

        @Override
        public void close() {
            WinMM.INSTANCE.midiOutClose(handle);
        }
    }

    // ---------- Device helpers ----------

    static void listInputs() {
        int n = WinMM.INSTANCE.midiInGetNumDevs();
        conWriteLine("=== WinMM MIDI Inputs ===");
        for (int i = 0; i < n; i++) {
            WinMM.MIDIINCAPS caps = new WinMM.MIDIINCAPS();
            WinMM.INSTANCE.midiInGetDevCapsA(i, caps, caps.size());
            conWriteF("  [%d] %s%n", i, caps.name());
        }
    }

    static void listOutputs() {
        int n = WinMM.INSTANCE.midiOutGetNumDevs();
        conWriteLine("=== WinMM MIDI Outputs ===");
        for (int i = 0; i < n; i++) {
            WinMM.MIDIOUTCAPS caps = new WinMM.MIDIOUTCAPS();
            WinMM.INSTANCE.midiOutGetDevCapsA(i, caps, caps.size());
            conWriteF("  [%d] %s%n", i, caps.name());
        }
    }

    static int findInput(String name) {
        int n = WinMM.INSTANCE.midiInGetNumDevs();
        for (int i = 0; i < n; i++) {
            WinMM.MIDIINCAPS caps = new WinMM.MIDIINCAPS();
            WinMM.INSTANCE.midiInGetDevCapsA(i, caps, caps.size());
            if (caps.name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalStateException("MIDI input not found: " + name);
    }

    static int findOutput(String name) {
        int n = WinMM.INSTANCE.midiOutGetNumDevs();
        for (int i = 0; i < n; i++) {
            WinMM.MIDIOUTCAPS caps = new WinMM.MIDIOUTCAPS();
            WinMM.INSTANCE.midiOutGetDevCapsA(i, caps, caps.size());
            if (caps.name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalStateException("MIDI output not found: " + name);
    }

    // ---------- Main ----------

    public static void main(String[] args) throws Exception {

        // Start async logger before MIDI processing
        startAsyncLogger();

        listInputs();
        conWriteLine("");
        listOutputs();
        conWriteLine("");

        conWriteLine(
            "Enter config: <inName>[,<in2Name>],<outName>,<umbrella:true/false>,<delayMs>,<tailMs>[,<sostCcIn>]");
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line = br.readLine().trim();

            // Detect optional trailing sostCcIn:
            // When absent, token[-3] is the umbrella boolean.
            // When present, token[-3] is tailMs (an integer).
            String[] tokens = line.split("\\s*,\\s*");
            boolean hasSostCc = false;
            try {
                String maybeUmb = tokens[tokens.length - 3].trim().toLowerCase();
                hasSostCc = !maybeUmb.equals("true") && !maybeUmb.equals("false");
            } catch (Exception ignored) {
            }

            // Strip optional sostCcIn from the right before main parsing
            int tail = line.lastIndexOf(',');
            if (hasSostCc) {
                SOST_CC_IN = Integer.parseInt(line.substring(tail + 1).trim());
                line = line.substring(0, tail).trim();
                tail = line.lastIndexOf(',');
            }
            int delay = line.lastIndexOf(',', tail - 1);
            int umb = line.lastIndexOf(',', delay - 1);
            int out = line.lastIndexOf(',', umb - 1);
            int in2 = line.lastIndexOf(',', out - 1);

            UMBRELLA_TAIL_MS = Integer.parseInt(line.substring(tail + 1).trim());
            UMBRELLA_DELAY_MS = Integer.parseInt(line.substring(delay + 1, tail).trim());
            UMBRELLA = Boolean.parseBoolean(line.substring(umb + 1, delay).trim());
            OUT_NAME = line.substring(out + 1, umb).trim();

            if (in2 >= 0) {
                IN_NAME = line.substring(0, in2).trim();
                IN2_NAME = line.substring(in2 + 1, out).trim();
            } else {
                IN_NAME = line.substring(0, out).trim();
                IN2_NAME = "";
            }

            if (IN2_NAME.isEmpty()) {
                conWriteF(">> Config: in: %s out: %s umbrella: %s delay: %d tail=%d sostCcIn: %d%n",
                    IN_NAME, OUT_NAME, UMBRELLA, UMBRELLA_DELAY_MS, UMBRELLA_TAIL_MS, SOST_CC_IN);
            } else {
                conWriteF(">> Config: in: %s in2: %s out: %s umbrella: %s delay: %d tail: %d sostCcIn: %d%n",
                    IN_NAME, IN2_NAME, OUT_NAME, UMBRELLA, UMBRELLA_DELAY_MS, UMBRELLA_TAIL_MS, SOST_CC_IN);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Open output first, then build SostenutoReceiver around it
        int outId = findOutput(OUT_NAME);
        PointerByReference outRef = new PointerByReference();
        WinMM.INSTANCE.midiOutOpen(outRef, outId, null, null, 0);
        Pointer outHandle = outRef.getValue();

        WinMMReceiver rx = new WinMMReceiver(outHandle);
        SostenutoReceiver sost = new SostenutoReceiver(rx);

        // Primary input — all messages forwarded to SostenutoReceiver
        int inId = findInput(IN_NAME);
        PointerByReference inRef = new PointerByReference();
        callback1 = (handle, msg, instance, param1, param2) -> {
            if (msg == MIM_DATA) {
                int packed = (int) Pointer.nativeValue(param1);
                int status = packed & 0xFF;
                int d1 = (packed >> 8) & 0xFF;
                int d2 = (packed >> 16) & 0xFF;
                logRawMidiAsync(packed, IN_NAME);
                try {
                    // If no secondary input, remap SOST_CC_IN -> CC66 here
                    if (IN2_NAME.isEmpty() && (status & 0xF0) == 0xB0 && d1 == SOST_CC_IN) {
                        ShortMessage sm = new ShortMessage();
                        sm.setMessage(ShortMessage.CONTROL_CHANGE, status & 0x0F, 66, d2);
                        sost.send(sm, -1);
                        return;
                    }
                    ShortMessage sm = new ShortMessage();
                    sm.setMessage(status, d1, d2);
                    sost.send(sm, -1);
                } catch (InvalidMidiDataException e) {
                    e.printStackTrace();
                }
            }
        };
        WinMM.INSTANCE.midiInOpen(inRef, inId, callback1, null, CALLBACK_FUNCTION);
        Pointer inHandle = inRef.getValue();

        // Optional second input — filters to SOST_CC_IN only, remaps to CC66
        Pointer in2Handle = null;
        if (!IN2_NAME.isEmpty()) {
            int in2Id = findInput(IN2_NAME);
            PointerByReference in2Ref = new PointerByReference();
            callback2 = (handle, msg, instance, param1, param2) -> {
                if (msg == MIM_DATA) {
                    int packed = (int) Pointer.nativeValue(param1);
                    int status = packed & 0xFF;
                    int d1 = (packed >> 8) & 0xFF;
                    int d2 = (packed >> 16) & 0xFF;
                    logRawMidiAsync(packed, IN2_NAME);
                    if ((status & 0xF0) == 0xB0 && d1 == SOST_CC_IN) {
                        try {
                            ShortMessage sm = new ShortMessage();
                            sm.setMessage(ShortMessage.CONTROL_CHANGE, status & 0x0F, 66, d2);
                            sost.send(sm, -1);
                        } catch (InvalidMidiDataException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            WinMM.INSTANCE.midiInOpen(in2Ref, in2Id, callback2, null, CALLBACK_FUNCTION);
            in2Handle = in2Ref.getValue();
        }
        final Pointer in2HandleFinal = in2Handle;

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        WinMM.INSTANCE.midiInStart(inHandle);
        if (in2Handle != null) {
            WinMM.INSTANCE.midiInStart(in2Handle);
        }

        conWriteLine("");
        conWriteLine(">> IN : " + IN_NAME);
        if (!IN2_NAME.isEmpty()) {
            conWriteLine(">> IN2: " + IN2_NAME + "  (CC" + SOST_CC_IN + " -> CC66)");
        }
        conWriteLine(">> OUT: " + OUT_NAME);

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopAsyncLogger();
            WinMM.INSTANCE.midiInStop(inHandle);
            WinMM.INSTANCE.midiInClose(inHandle);
            if (in2HandleFinal != null) {
                WinMM.INSTANCE.midiInStop(in2HandleFinal);
                WinMM.INSTANCE.midiInClose(in2HandleFinal);
            }
            tryCatchSuppress(sost::panic);
            tryCatchSuppress(sost::close);
            rx.close();
            conWriteLine("\n[bye]");
        }));

        startQuitWatcher();
        Thread.currentThread().join();
    }

    // Toggle for color support - set to false if terminal doesn't support ANSI colors
    private static final boolean USE_COLORS = true;

    // Async logging infrastructure
    private static final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();
    private static volatile Thread loggerThread;
    private static volatile boolean loggerRunning = true;

    private static class LogEntry {
        final int packed;
        final String inputName;

        LogEntry(int packed, String inputName) {
            this.packed = packed;
            this.inputName = inputName;
        }
    }

    private static void startAsyncLogger() {
        loggerThread = new Thread(() -> {
            while (loggerRunning) {
                try {
                    LogEntry entry = logQueue.take();
                    if (entry != null) {
                        logRawMidiSync(entry.packed, entry.inputName);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "midi-logger");
        loggerThread.setDaemon(true);
        loggerThread.start();
    }

    private static void stopAsyncLogger() {
        loggerRunning = false;
        if (loggerThread != null) {
            loggerThread.interrupt();
        }
    }

    private static void logRawMidiAsync(int packed, String inputName) {
        logQueue.offer(new LogEntry(packed, inputName));
    }

    private static final String RAW_LINE_FORMAT = """
        ${RESET}CH:${RESET} %s%2d${RESET} ${RESET}|${RESET} %s%-9s${RESET} ${RESET}|${RESET} \
        ${RESET}D1:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        ${RESET}D2:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        ${RESET}Status:${RESET} %s%3d${RESET} ${RESET}|${RESET} \
        %s%s${RESET} ${RESET}|${RESET} %s%s${RESET} \
        """;

    private static final String LINE_FORMAT = RAW_LINE_FORMAT.replace("${RESET}", RESET);

    private static void logRawMidiSync(int packed, String inputName) {
        int status = packed & 0xFF;
        int d1 = (packed >> 8) & 0xFF;
        int d2 = (packed >> 16) & 0xFF;
        long now = System.nanoTime();
        int cmd = status & 0xF0;
        int ch = (status & 0x0F) + 1;

        MidiMessageType msgType = MIDI_MSG_LOOKUP.getOrDefault(cmd, MidiMessageType.OTHER);
        String msgColor = MIDI_MSG_COLOR.getOrDefault(msgType, AnsiColor.RED.bright());


        if (USE_COLORS) {
            String line = LINE_FORMAT.formatted(
                AnsiColor.WHITE.dark(), ch,
                msgColor, msgType,
                getAccentColor(AnsiColor.YELLOW, msgType), d1,
                getAccentColor(AnsiColor.GREEN, msgType), d2,
                getAccentColor(AnsiColor.RED, msgType), status,
                AnsiColor.BLUE.bright(), inputName,
                AnsiColor.GRAY.bright(), "%,d ns".formatted(now).replace(",", " "));
            conWriteLine(line);
        } else {
            conWriteF("CH: %2d | %-9s | D1: %3d | D2: %3d | Status: %3d | [%s] | %s ns%n",
                ch, msgType, d1, d2, status, inputName, String.format("%,d", now).replace(",", " "));
        }
    }

    // ---------- The MIDI transformer ----------

    static final class SostenutoReceiver implements Receiver {
        private final Receiver out;

        private final boolean[] sustainOn = new boolean[16];
        private final boolean[] sostOn = new boolean[16];

        private final int[] lastCC64 = new int[16];
        private final int[] lastCC66 = new int[16];

        private final boolean[][] pressed = new boolean[16][128];
        private final boolean[][] latched = new boolean[16][128];
        private final boolean[][] delayedOff = new boolean[16][128];

        private final ScheduledExecutorService sched = newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sost-umbrella");
            return t;
        });

        private final AtomicIntegerArray umbrellaDepth = new AtomicIntegerArray(16);
        private volatile boolean off = false;



        SostenutoReceiver(Receiver out) {
            this.out = out;
            Arrays.fill(lastCC64, -1);
            Arrays.fill(lastCC66, -1);
        }

        private static boolean handleCC(int cc, int ctrl, int val, int[] last, int ch, ThrRunnable r) {
            if (cc != ctrl) {
                return false;
            }
            if (val == last[ch]) {
                return true;
            }
            last[ch] = val;
            tryCatchSuppress(r);
            return true;
        }

        private void outSend(MidiMessage m, long ts) {
            out.send(m, ts);
        }

        @Override
        public void send(MidiMessage inmsg, long timeStamp) {
            if (off) {
                return;
            }

            tryCatchSuppress(() -> {
                if (!(inmsg instanceof ShortMessage sm)) {
                    outSend(inmsg, timeStamp);
                    return;
                }
                var msg = copyOf(sm);
                int cmd = sm.getCommand();
                int ch = sm.getChannel();

                // ----- Control Change -----
                if (cmd == ShortMessage.CONTROL_CHANGE) {
                    int ctrl = sm.getData1();
                    if (ctrl == 121 || ctrl == 123) {
                        panic();
                        return;
                    }
                    int val = sm.getData2();
                    if (handleCC(64, ctrl, val, lastCC64, ch, () -> onCC64(ch, val, timeStamp))
                        || handleCC(66, ctrl, val, lastCC66, ch, () -> onCC66(ch, val, timeStamp))) {
                        return;
                    }
                    outSend(msg, timeStamp);
                    return;
                }

                // ----- Notes -----
                if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {
                    int note = sm.getData1();
                    int vel = sm.getData2();
                    boolean isOff = cmd == ShortMessage.NOTE_OFF || vel == 0;

                    if (!isOff) {
                        boolean umbrella = shouldUmbrella(ch, note);
                        pressed[ch][note] = true;
                        if (umbrella) {
                            sendUmbrellaNoteOn(ch, note, vel, timeStamp);
                            return;
                        }
                        outSend(msg, timeStamp);
                        return;
                    }

                    // NOTE OFF
                    pressed[ch][note] = false;
                    if (sustainOn[ch] || (sostOn[ch] && latched[ch][note])) {
                        delayedOff[ch][note] = true;
                        return;
                    }
                    delayedOff[ch][note] = false;
                    outSend(msg, timeStamp);
                    return;
                }

                // ----- All other messages -----
                outSend(msg, timeStamp);
            });
        }

        private void onCC64(int ch, int val, long ts) {
            boolean now = val >= 64;
            if (now && !sustainOn[ch]) {
                sustainOn[ch] = true;
                return;
            }
            if (!(!now && sustainOn[ch])) {
                return;
            }
            sustainOn[ch] = false;
            IntStream.range(0, 128)
                .filter(n -> delayedOff[ch][n] && !pressed[ch][n] && !(sostOn[ch] && latched[ch][n]))
                .forEach(n -> sendNoteOff(ch, n, ts));
        }

        private void onCC66(int ch, int val, long ts) {
            boolean lastOn = sostOn[ch];
            boolean nowOn = lastOn ? val > Math.max(0, CC66_THRESH - CC66_HYST) : val >= CC66_THRESH;

            if (nowOn && !lastOn) {
                IntStream.range(0, 128).forEach(n -> latched[ch][n] = pressed[ch][n] || delayedOff[ch][n]);
                sostOn[ch] = true;
                return;
            }
            if (!(!nowOn && lastOn)) {
                return;
            }

            IntConsumer consumer = sustainOn[ch]
                ? n -> delayedOff[ch][n] = true
                : n -> sendNoteOff(ch, n, ts);
            IntStream.range(0, 128).filter(n -> latched[ch][n] && !pressed[ch][n]).forEach(consumer);
            Arrays.fill(latched[ch], false);
            sostOn[ch] = false;
        }

        private void sendNoteOff(int ch, int note, long ts) {
            delayedOff[ch][note] = false;
            outSend(noteOff(ch, note), ts);
        }

        void panic() {
            off = true;
            try {
                IntStream.range(0, umbrellaDepth.length()).forEach(i -> umbrellaDepth.set(i, 0));
                IntStream.range(0, 16).forEach(ch -> {
                    sendCC(ch, 64, 0, -1);
                    sendCC(ch, 66, 0, -1);
                    sendCC(ch, 69, 0, -1);
                    IntStream.range(0, 128)
                        .filter(n -> pressed[ch][n] || latched[ch][n] || delayedOff[ch][n])
                        .forEach(n -> sendNoteOff(ch, n, -1));
                    sendCC(ch, 123, 0, -1);
                    sendCC(ch, 121, 0, -1);
                    sendCC(ch, 120, 0, -1);
                    Arrays.fill(pressed[ch], false);
                    Arrays.fill(latched[ch], false);
                    Arrays.fill(delayedOff[ch], false);
                    sustainOn[ch] = false;
                    sostOn[ch] = false;
                    lastCC64[ch] = -1;
                    lastCC66[ch] = -1;
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                off = false;
            }
        }

        private void sendCC(int ch, int ctrl, int val, long ts) {
            outSend(cc(ch, ctrl, val), ts);
        }

        @Override
        public void close() {
            sched.shutdown();
            try {
                if (!sched.awaitTermination(10, TimeUnit.SECONDS)) {
                    sched.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean shouldUmbrella(int ch, int note) {
            return UMBRELLA && (delayedOff[ch][note] || latched[ch][note]);
        }

        private void sendUmbrellaNoteOn(int ch, int note, int vel, long ts) {
            boolean needDown = umbrellaDepth.getAndIncrement(ch) == 0;
            if (needDown) {
                sendCC(ch, 64, 127, -1);
            }
            sched.schedule(() -> {
                outSend(noteOn(ch, note, vel), -1);
                sched.schedule(() -> {
                    int depth = umbrellaDepth.decrementAndGet(ch);
                    if (depth < 0) {
                        umbrellaDepth.set(ch, 0);
                        return;
                    }
                    if (depth == 0) {
                        sendCC(ch, 64, 0, -1);
                    }
                }, UMBRELLA_TAIL_MS, TimeUnit.MILLISECONDS);
            }, UMBRELLA_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    // ---------- MIDI message helpers ----------

    private static ShortMessage cc(int ch, int ctrl, int val) {
        return tryCatchWrapRT(() -> {
            ShortMessage m = new ShortMessage();
            m.setMessage(ShortMessage.CONTROL_CHANGE, ch, ctrl, val);
            return m;
        });
    }

    private static ShortMessage noteOn(int ch, int note, int vel) {
        return tryCatchWrapRT(() -> {
            ShortMessage m = new ShortMessage();
            m.setMessage(ShortMessage.NOTE_ON, ch, note, vel);
            return m;
        });
    }

    private static ShortMessage noteOff(int ch, int note) {
        return tryCatchWrapRT(() -> {
            ShortMessage m = new ShortMessage();
            m.setMessage(ShortMessage.NOTE_OFF, ch, note, 0);
            return m;
        });
    }

    private static ShortMessage copyOf(ShortMessage sm) {
        return tryCatchWrapRT(() -> {
            ShortMessage c = new ShortMessage();
            c.setMessage(sm.getStatus(), sm.getData1(), sm.getData2());
            return c;
        });
    }

    // ---------- Functional helpers ----------

    @FunctionalInterface
    public interface ThrSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrRunnable {
        void run() throws Exception;
    }

    static <T> T tryCatchWrapRT(ThrSupplier<T> core) {
        try {
            return core.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void tryCatchSuppress(ThrRunnable core) {
        try {
            core.run();
        } catch (Exception ig) {
            ig.printStackTrace();
        }
    }

    // ---------- Console / quit ----------

    private static void startQuitWatcher() {
        Thread t = new Thread(() -> tryCatchSuppress(() -> {
            conWriteLine("\n[quit] Press ENTER to exit.");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line = br.readLine();
            if (line != null && line.isEmpty()) {
                System.exit(0);
            }
        }), "quit-watcher");
        t.setDaemon(true);
        t.start();
    }

    private static void conWriteLine(String s) {
        System.out.println(s);
    }

    private static void conWriteF(String s, Object... args) {
        System.out.printf(s, args);
    }
}