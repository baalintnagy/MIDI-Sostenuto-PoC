package org.nb;


import static de.tobiaserichsen.tevm.TeVirtualMIDI.TE_VM_FLAGS_INSTANTIATE_BOTH;
import static de.tobiaserichsen.tevm.TeVirtualMIDI.TE_VM_FLAGS_PARSE_RX;
import static de.tobiaserichsen.tevm.TeVirtualMIDI.TE_VM_FLAGS_PARSE_TX;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import de.tobiaserichsen.tevm.TeVirtualMIDI;
import de.tobiaserichsen.tevm.TeVirtualMIDIException;

public class SustSosScambled {

    // ---------- Defaults (override via CLI flags) ----------

    private static String IN_NAME =
            // "Hammer 88 Pro";
            "preSS";
    private static String OUT_NAME = "postSS";

    private static int CC66_THRESH = 10; // engage threshold
    private static int CC66_HYST = 2; // hysteresis: disengage below (THRESH - HYST)

    private static boolean UMBRELLA = true;
    private static int UMBRELLA_DELAY_MS = 2;
    private static int UMBRELLA_TAIL_MS = 2;

    private static final Map<Integer, String> MSG_NAMES = Map.of(
            ShortMessage.NOTE_ON, "NOTE_ON ",
            ShortMessage.NOTE_OFF, "NOTE_OFF",
            ShortMessage.CONTROL_CHANGE, "CC      ",
            ShortMessage.PITCH_BEND, "PBEND   ",
            ShortMessage.CHANNEL_PRESSURE, "CHPR    ",
            ShortMessage.POLY_PRESSURE, "POLYAT  ",
            ShortMessage.PROGRAM_CHANGE, "PC      ");

    @FunctionalInterface
    public static interface ThrSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public static interface ThrRunnable {
        void run() throws Exception;
    }

    static <T> T tryCatchWrapRT(ThrSupplier<T> core) {
        try {
            return core.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static <T> T tryCatchDefault(ThrSupplier<T> core, T def) {
        try {
            return core.get();
        } catch (Exception ig) {
            ig.printStackTrace();
            return def;
        }
    }

    static void tryCatchSuppress(ThrRunnable core) {
        try {
            core.run();
        } catch (Exception ig) {
            ig.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        prep();
        Thread.sleep(1000);

        // Enumerate devices
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        List<MidiDevice.Info> inInfos = Arrays.stream(infos).filter(SustSosScambled::isInputCapable).collect(Collectors.toList());
        List<MidiDevice.Info> outInfos = Arrays.stream(infos).filter(SustSosScambled::isOutputCapable).collect(Collectors.toList());

        conWriteLine("=== MIDI Inputs ===");
        listDevices(inInfos);
        conWriteLine("");
        conWriteLine("=== MIDI Outputs ===");
        listDevices(outInfos);
        conWriteLine("");

        conWriteLine("Enter config: <inName>,<outName>,<umbrella:true/false>,<delayMs>,<tailMs>");
        try {

            @SuppressWarnings("all")
            Scanner sc = new Scanner(System.in);
            sc.useDelimiter("\\s*,\\s*|\\R+");
            IN_NAME = sc.next().trim(); // first token (input name)
            OUT_NAME = sc.next().trim(); // second token (output name)
            UMBRELLA = sc.nextBoolean(); // true/false
            UMBRELLA_DELAY_MS = sc.nextInt();
            UMBRELLA_TAIL_MS = sc.nextInt();

            conWriteF(">> Config: in=%s out=%s umbrella=%s delay=%d tail=%d%n",
                IN_NAME, OUT_NAME, UMBRELLA, UMBRELLA_DELAY_MS, UMBRELLA_TAIL_MS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        MidiDevice.Info inInfo = pickDevice(inInfos, IN_NAME, "input");
        MidiDevice.Info outInfo = pickDevice(outInfos, OUT_NAME, "output");

        MidiDevice inDev = MidiSystem.getMidiDevice(inInfo);
        MidiDevice outDev = MidiSystem.getMidiDevice(outInfo);

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        inDev.open();
        outDev.open();

        Transmitter tx = inDev.getTransmitter();
        Receiver rx = outDev.getReceiver();

        conWriteLine("");
        conWriteLine(">> IN : " + inInfo.getName());
        conWriteLine(">> OUT: " + outInfo.getName());

        SostenutoReceiver sost = new SostenutoReceiver(rx);
        tx.setReceiver(sost);

        // Graceful shutdown (panic)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tryCatchSuppress(tx::close);

            tryCatchSuppress(sost::panic);
            tryCatchSuppress(sost::close);


            tryCatchSuppress(rx::close);
            tryCatchSuppress(inDev::close);
            tryCatchSuppress(outDev::close);

            conWriteLine("\n[bye]");
        }));



        startQuitWatcher();
        Thread.currentThread().join();
    }

    // ---------- The MIDI transformer ----------
    static final class SostenutoReceiver implements Receiver {
        private final Receiver out;

        // Per-channel pedal states
        private final boolean[] sustainOn = new boolean[16]; // CC64
        private final boolean[] sostOn = new boolean[16]; // CC66

        // Last CC values to drop repeats (de-noise)
        private final int[] lastCC64 = new int[16];
        private final int[] lastCC66 = new int[16];

        // Per-channel note sets (bitsets would also work; booleans are simple & fast)
        private final boolean[][] pressed = new boolean[16][128];
        // private final boolean[][] sustained = new boolean[16][128]; // offs swallowed
        // under CC64
        private final boolean[][] latched = new boolean[16][128]; // captured at CC66 engage
        private final boolean[][] delayedOff = new boolean[16][128]; // to send when CC64 goes low

        private final ScheduledExecutorService sched = newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sost-umbrella");
            // t.setDaemon(true);
            return t;
        });
        private final ExecutorService outSer = newSingleThreadExecutor(r -> {
            var t = new Thread(r, "sost-out");
            // t.setDaemon(true);
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
            // outSer.execute(() -> out.send(m, ts));
        }

        @Override
        public void send(MidiMessage inmsg, long timeStamp) {
            if (off) {
                return;
            }

            tryCatchSuppress(() -> {
                if (!(inmsg instanceof ShortMessage sm)) {
                    // SysEx / Meta etc. → pass through
                    outSend(inmsg, timeStamp);
                    return;
                }
                var msg = copyOf((ShortMessage) inmsg);
                logIn(msg, timeStamp);
                int cmd = sm.getCommand();
                int ch = sm.getChannel(); // 0..15

                // ----- Control Change -----
                if (cmd == ShortMessage.CONTROL_CHANGE) {
                    int ctrl = sm.getData1();
                    if (ctrl == 121 || ctrl == 123) {
                        panic();
                        return;
                    }

                    int val = sm.getData2();
                    if (handleCC(64, ctrl, val, lastCC64, ch, () -> onCC64(ch, val, timeStamp))
                            ||
                            handleCC(66, ctrl, val, lastCC66, ch, () -> onCC66(ch, val, timeStamp))) {
                        return;
                    }

                    // Other CCs pass through unchanged
                    outSend(msg, timeStamp);
                    return;
                }

                // ----- Notes -----
                if (cmd == ShortMessage.NOTE_ON
                        || cmd == ShortMessage.NOTE_OFF) {
                    int note = sm.getData1();
                    int vel = sm.getData2();
                    boolean isOff = cmd == ShortMessage.NOTE_OFF || vel == 0;

                    if (!isOff) {
                        boolean umbrella = shouldUmbrella(ch, note); // <— decide FIRST
                        // mutate local state only after decision
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
                    if (sustainOn[ch] || sostOn[ch] && latched[ch][note]) {
                        delayedOff[ch][note] = true;
                        return;
                    }
                    delayedOff[ch][note] = false;
                }

                // ----- All other channel voice messages (AT, PB, PC, ChPress) -----
                outSend(msg, timeStamp);
                return;

            });

        }

        // ---- Pedal logic ----
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
                    .filter(n -> delayedOff[ch][n]
                            && !pressed[ch][n]
                            && !(sostOn[ch] && latched[ch][n]))
                    .forEach(n -> sendNoteOff(ch, n, ts));
        }

        private void onCC66(int ch, int val, long ts) {
            boolean lastOn = sostOn[ch];
            boolean nowOn = lastOn ? val > Math.max(0, CC66_THRESH - CC66_HYST) : val >= CC66_THRESH;

            if (nowOn && !lastOn) {
                // Engage: snapshot sounding
                IntStream.range(0, 128)
                        .forEach(n -> latched[ch][n] = pressed[ch][n] || delayedOff[ch][n]);
                sostOn[ch] = true;
                return;
            }
            if (!(!nowOn && lastOn)) {
                return;
            }

            // Disengage: release latched notes that aren't pressed; either now or later (if
            // CC64 is held)
            IntConsumer consumer = sustainOn[ch]
                    ? n -> delayedOff[ch][n] = true
                    : n -> sendNoteOff(ch, n, ts);
            IntStream.range(0, 128)
                    .filter(n -> latched[ch][n] && !pressed[ch][n])
                    .forEach(consumer);

            Arrays.fill(latched[ch], false);
            sostOn[ch] = false;
        }

        private void sendNoteOff(int ch, int note, long ts) {
            delayedOff[ch][note] = false;
            outSend(noteOff(ch, note), ts);
        }

        // ---- Panic: kill everything politely across all channels ----
        void panic() {
            off = true;
            try {
                IntStream.range(0, umbrellaDepth.length()).forEach(i -> umbrellaDepth.set(i, 0));
                outSer.submit(() -> IntStream.range(0, 16)
                        .forEach(ch -> {
                            sendCC(ch, 64, 0, -1); // Sustain Off

                            sendCC(ch, 66, 0, -1); // Sostenuto Off
                            sendCC(ch, 69, 0, -1); // Hold 2 Off

                            IntStream.range(0, 128)
                                    .filter(n -> pressed[ch][n] || latched[ch][n] || delayedOff[ch][n])
                                    .forEach(n -> sendNoteOff(ch, n, -1));
                            sendCC(ch, 123, 0, -1); // All Notes Off
                            sendCC(ch, 121, 0, -1); // Reset all Controls

                            sendCC(ch, 120, 0, -1); // All Sound Off

                            // reset state
                            Arrays.fill(pressed[ch], false);
                            Arrays.fill(latched[ch], false);
                            Arrays.fill(delayedOff[ch], false);
                            sustainOn[ch] = false;
                            sostOn[ch] = false;
                            lastCC64[ch] = -1;
                            lastCC66[ch] = -1;
                        })).get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
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
            // 1) stop future umbrella tasks from *starting*
            sched.shutdown(); // not shutdownNow: let any task already running complete
            try {
                if (!sched.awaitTermination(10, TimeUnit.SECONDS)) {
                    sched.shutdownNow(); // cancel any still-pending delays
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 2) no new outbound tasks; let running/queued sends finish
            outSer.shutdown();
            try {
                outSer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }


        private void logIn(ShortMessage sm, long ts) {
            final int ch = sm.getChannel() + 1; // 1..16
            final int cmd = sm.getCommand();
            final int d1 = sm.getData1();
            final int d2 = sm.getData2();

            long now = System.nanoTime() / 1_000_000; // ms ;

            String kind = MSG_NAMES.getOrDefault(cmd, "OTHER  ");

            // Print concise, GC-light line
            if (cmd == ShortMessage.CONTROL_CHANGE) {
                conWriteF("  IN %dms   ch%02X  %s   cc  =%02X   val=%02X%n", now, ch, kind, d1, d2);
            } else if (kind.startsWith("NOTE")) {
                conWriteF("  IN %dms   ch%02X  %s   note=%02X   vel=%02X%n", now, ch, kind, d1, d2);
            } else {
                conWriteF("  IN %dms   ch%02X  %s   d1  =%02X   d2 =%02X   status=0x%02X%n", now, ch, kind, d1, d2, sm.getStatus());
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

            // emit NOTE_ON after the delay
            sched.schedule(() -> {
                outSend(noteOn(ch, note, vel), -1);

                // chain the lift AFTER the note with the tail
                sched.schedule(() -> {
                    int depth = umbrellaDepth.decrementAndGet(ch);
                    if (depth < 0) {
                        umbrellaDepth.set(ch, 0);
                        return;
                    }
                    if (depth == 0) {
                        sendCC(ch, 64, 0, -1);
                    }
                }, UMBRELLA_TAIL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);

            }, UMBRELLA_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }


    }

    private static ShortMessage cc(int ch, int ctrl, int val) {
        return tryCatchWrapRT(
                () -> {
                    ShortMessage m = new ShortMessage();
                    m.setMessage(ShortMessage.CONTROL_CHANGE, ch, ctrl, val);
                    return m;
                });
    }

    private static ShortMessage noteOn(int ch, int note, int vel) {
        return tryCatchWrapRT(
                () -> {
                    ShortMessage m = new ShortMessage();
                    m.setMessage(ShortMessage.NOTE_ON, ch, note, vel);
                    return m;
                });
    }

    private static ShortMessage noteOff(int ch, int note) {
        return tryCatchWrapRT(
                () -> {
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

    private static boolean isInputCapable(MidiDevice.Info info) {
        return tryCatchDefault(
                () -> MidiSystem.getMidiDevice(info).getMaxTransmitters() != 0,
                false);
    }

    private static boolean isOutputCapable(MidiDevice.Info info) {
        return tryCatchDefault(
                () -> MidiSystem.getMidiDevice(info).getMaxReceivers() != 0,
                false);
    }

    private static void listDevices(List<MidiDevice.Info> infos) {
        infos.stream()
                .forEach(info -> conWriteF("    %-40s (%s)%n", info.getName(), info.getDescription()));
    }

    private static MidiDevice.Info pickDevice(List<MidiDevice.Info> pool, String name, String inout) {
        return pool.stream()
                .filter(info -> info.getName().equalsIgnoreCase(name))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("No %s:%s MIDI devices found.".formatted(name, inout)));
    }

    private static void startQuitWatcher() {
        Thread t = new Thread(() ->
            tryCatchSuppress(() -> {
                conWriteLine("\n[quit] Press ENTER to exit.");
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String line = br.readLine(); // blocks until Enter in IDE consoles too
                if (line != null && line.isEmpty()) {
                    // Trigger a clean shutdown; your shutdown hook will run.
                    System.exit(0);
                }
        }),

                "quit-watcher");
        t.setDaemon(true); // won’t prevent JVM exit
        t.start();
    }

    private static void conWriteLine(String s) {
        System.out.println(s);
    }

    private static void conWriteF(String s, Object... args) {
        System.out.printf(s, args);
    }

    // ----

    static TeVirtualMIDI inputPort = null;
    static TeVirtualMIDI outputPort = null;

    static void prep() {


        try {

            try {
                System.out.println("teVM version: " + TeVirtualMIDI.getVersionString());
                System.out.println("teVM driver:  " + TeVirtualMIDI.getDriverVersionString());
            } catch (Throwable t) {
                System.err.println("teVM native load FAILED: " + t);
                t.printStackTrace();
                return; // no point continuing
            }

            // Maximum SysEx size (65535 is safe)
            int maxSysex = 65535;

            // Create the "preSSS" port (bidirectional)
            inputPort = new TeVirtualMIDI("preSSS", 65535,
                TE_VM_FLAGS_PARSE_RX | TE_VM_FLAGS_PARSE_TX | TE_VM_FLAGS_INSTANTIATE_BOTH);
            System.out.println("Created port: preSSS");

            // Create the "postSSS" port (bidirectional)
            outputPort = new TeVirtualMIDI("postSSS", 65535,
                TE_VM_FLAGS_PARSE_RX | TE_VM_FLAGS_PARSE_TX | TE_VM_FLAGS_INSTANTIATE_BOTH);
            System.out.println("Created port: postSSS");

        } catch (TeVirtualMIDIException e) {
            System.err.println("TeVirtualMIDI error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } /*finally {
            // Clean shutdown: close ports
            if (inputPort != null) {
                try {
                    inputPort.shutdown();
                } catch (Exception e) {
                }
                System.out.println("Closed preSSS");
            }
            if (outputPort != null) {
                try {
                    outputPort.shutdown();
                } catch (Exception e) {
                }
                System.out.println("Closed postSSS");
            }
        }*/
    }
}
