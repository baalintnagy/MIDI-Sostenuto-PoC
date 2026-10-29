package org.nb;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class SustSosApplication {

    static final String CONFIG_PROMPT =
        "Enter config: <inName>[,<in2Name>],<outName>,<umbrella:true/false>,"
            + "<delayMs>,<tailMs>[,<sostCcIn>][,note:<0..127>][,toggle:<true/false>]\n"
            + "Adding note:<number> selects note control instead of CC control.\n"
            + "toggle:true switches on/off once per press; toggle:false holds until release.\n"
            + "The trigger uses in2Name when provided, otherwise inName.\n"
            + "CC example: Keyboard,Pedal Controller,Loopback,false,0,0,22\n"
            + "Note example: Keyboard,Pedal Controller,Loopback,false,0,0,note:78,toggle:true";
    private static final boolean USE_COLORS = true;

    void run() throws Exception {
        WinMMMidiSystem midiSystem = new WinMMMidiSystem();
        midiSystem.printDevices(System.out);

        AppConfig config = readConfiguration(midiSystem);
        MidiLogger logger = new MidiLogger(System.out, USE_COLORS, config.logFilter());
        logger.start();
        WinMMReceiver nativeOutput = midiSystem.openOutput(config.outputName());
        SostenutoReceiver transformer = new SostenutoReceiver(nativeOutput, config.sostenutoSettings());
        MidiEventRouter eventRouter = new MidiEventRouter(config, logger, transformer);

        WinMMInput primaryInput = midiSystem.openInput(config.primaryInputName(), eventRouter::routePrimary);
        WinMMInput secondaryInput = config.hasSecondaryInput()
            ? midiSystem.openInput(config.secondaryInputName(), eventRouter::routeSecondary)
            : null;

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        primaryInput.start();
        if (secondaryInput != null) {
            secondaryInput.start();
        }

        printActiveDevices(config);
        registerShutdownHook(logger, primaryInput, secondaryInput, transformer, nativeOutput);
        startQuitWatcher();
        Thread.currentThread().join();
    }

    private static AppConfig readConfiguration(WinMMMidiSystem midiSystem) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        AppConfig config = ConfigurationMenu.select(reader, System.out,
            ConfigurationMenu.discover(AppConfig.configurationDirectories()),
            () -> midiSystem.printDevices(System.out));
        System.out.println(config.summary());
        return config;
    }
    private static void printActiveDevices(AppConfig config) {
        System.out.println();
        System.out.println(">> IN : " + config.primaryInputName());
        if (config.hasSecondaryInput()) {
            System.out.printf(
                ">> IN2: %s  (%s -> CC66)%n",
                config.secondaryInputName(),
                config.sostenutoTrigger()
            );
        }
        System.out.println(">> OUT: " + config.outputName());
    }

    private static void registerShutdownHook(
        MidiLogger logger,
        WinMMInput primaryInput,
        WinMMInput secondaryInput,
        SostenutoReceiver transformer,
        WinMMReceiver nativeOutput
    ) {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("sustsos-shutdown").unstarted(() -> {
            logger.close();
            primaryInput.close();
            if (secondaryInput != null) {
                secondaryInput.close();
            }
            runSafely(transformer::panic);
            runSafely(transformer::close);
            nativeOutput.close();
            System.out.println("\n[bye]");
        }));
    }

    private static void startQuitWatcher() {
        Thread.ofPlatform().name("quit-watcher").daemon().start(() -> runSafely(() -> {
            System.out.println("\n[quit] Press ENTER to exit.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (line != null && line.isEmpty()) {
                System.exit(0);
            }
        }));
    }

    private static void runSafely(ThrowingAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
