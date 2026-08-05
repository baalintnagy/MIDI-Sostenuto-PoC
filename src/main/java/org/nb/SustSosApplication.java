package org.nb;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class SustSosApplication {

    private static final String CONFIG_PROMPT =
        "Enter config: <inName>[,<in2Name>],<outName>,<umbrella:true/false>,"
            + "<delayMs>,<tailMs>[,<sostCcIn>]";
    private static final boolean USE_COLORS = true;

    void run() throws Exception {
        MidiLogger logger = new MidiLogger(System.out, USE_COLORS);
        logger.start();

        WinMMMidiSystem midiSystem = new WinMMMidiSystem();
        midiSystem.printDevices(System.out);

        AppConfig config = readConfiguration();
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

    private static AppConfig readConfiguration() {
        System.out.println(CONFIG_PROMPT);
        AppConfig config = AppConfig.defaults();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            config = AppConfig.parse(reader.readLine());
            System.out.println(config.summary());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return config;
    }

    private static void printActiveDevices(AppConfig config) {
        System.out.println();
        System.out.println(">> IN : " + config.primaryInputName());
        if (config.hasSecondaryInput()) {
            System.out.printf(
                ">> IN2: %s  (CC%d -> CC66)%n",
                config.secondaryInputName(),
                config.sostenutoController()
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.close();
            primaryInput.close();
            if (secondaryInput != null) {
                secondaryInput.close();
            }
            runSafely(transformer::panic);
            runSafely(transformer::close);
            nativeOutput.close();
            System.out.println("\n[bye]");
        }, "sustsos-shutdown"));
    }

    private static void startQuitWatcher() {
        Thread quitWatcher = new Thread(() -> runSafely(() -> {
            System.out.println("\n[quit] Press ENTER to exit.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (line != null && line.isEmpty()) {
                System.exit(0);
            }
        }), "quit-watcher");
        quitWatcher.setDaemon(true);
        quitWatcher.start();
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
