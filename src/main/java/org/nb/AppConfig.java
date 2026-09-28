package org.nb;

import java.util.Locale;
import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

record AppConfig(
    String primaryInputName,
    String secondaryInputName,
    String outputName,
    boolean umbrellaEnabled,
    int umbrellaDelayMs,
    int umbrellaTailMs,
    int sostenutoController
) {

    private static final int DEFAULT_SOSTENUTO_CONTROLLER = 22;
    private static final int SOSTENUTO_THRESHOLD = 10;
    private static final int SOSTENUTO_HYSTERESIS = 2;

    AppConfig {
        if (primaryInputName == null || primaryInputName.isBlank()
            || outputName == null || outputName.isBlank() || secondaryInputName == null) {
            throw new IllegalArgumentException("Input/output names are required; use an empty secondaryInputName for one input");
        }
        if (umbrellaDelayMs < 0 || umbrellaTailMs < 0 || sostenutoController < 0 || sostenutoController > 127) {
            throw new IllegalArgumentException("Timings must be nonnegative and sostenutoController must be 0..127");
        }
    }

    static AppConfig load(Path path) throws IOException {
        try {
            AppConfig config = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build().readValue(path.toFile(), AppConfig.class);
            if (config == null) {
                throw new IOException("Configuration must be a JSON object");
            }
            return config;
        } catch (IOException exception) {
            throw new IOException("Cannot load configuration from " + path.toAbsolutePath() + ": " + exception.getMessage(), exception);
        }
    }

    static Path configurationPath() throws Exception {
        Path local = Path.of("sustsos.json").toAbsolutePath();
        if (Files.exists(local)) {
            return local;
        }
        Path application = Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(application) ? application.resolveSibling("sustsos.json") : local;
    }

    static AppConfig parse(String input) {
        String line = Objects.requireNonNull(input, "input").trim();
        String[] tokens = line.split("\\s*,\\s*");
        boolean hasCustomSostenutoController = hasCustomSostenutoController(tokens);

        int tailSeparator = line.lastIndexOf(',');
        int sostenutoController = DEFAULT_SOSTENUTO_CONTROLLER;
        if (hasCustomSostenutoController) {
            sostenutoController = Integer.parseInt(line.substring(tailSeparator + 1).trim());
            line = line.substring(0, tailSeparator).trim();
            tailSeparator = line.lastIndexOf(',');
        }

        int delaySeparator = line.lastIndexOf(',', tailSeparator - 1);
        int umbrellaSeparator = line.lastIndexOf(',', delaySeparator - 1);
        int outputSeparator = line.lastIndexOf(',', umbrellaSeparator - 1);
        int secondaryInputSeparator = line.lastIndexOf(',', outputSeparator - 1);

        int umbrellaTailMs = Integer.parseInt(line.substring(tailSeparator + 1).trim());
        int umbrellaDelayMs = Integer.parseInt(line.substring(delaySeparator + 1, tailSeparator).trim());
        boolean umbrellaEnabled = Boolean.parseBoolean(
            line.substring(umbrellaSeparator + 1, delaySeparator).trim());
        String outputName = line.substring(outputSeparator + 1, umbrellaSeparator).trim();

        String primaryInputName;
        String secondaryInputName;
        if (secondaryInputSeparator >= 0) {
            primaryInputName = line.substring(0, secondaryInputSeparator).trim();
            secondaryInputName = line.substring(secondaryInputSeparator + 1, outputSeparator).trim();
        } else {
            primaryInputName = line.substring(0, outputSeparator).trim();
            secondaryInputName = "";
        }

        return new AppConfig(
            primaryInputName,
            secondaryInputName,
            outputName,
            umbrellaEnabled,
            umbrellaDelayMs,
            umbrellaTailMs,
            sostenutoController
        );
    }

    private static boolean hasCustomSostenutoController(String[] tokens) {
        if (tokens.length < 3) {
            return false;
        }
        String possibleUmbrella = tokens[tokens.length - 3].trim().toLowerCase(Locale.ROOT);
        return !possibleUmbrella.equals("true") && !possibleUmbrella.equals("false");
    }

    boolean hasSecondaryInput() {
        return !secondaryInputName.isEmpty();
    }

    SostenutoSettings sostenutoSettings() {
        return new SostenutoSettings(
            SOSTENUTO_THRESHOLD,
            SOSTENUTO_HYSTERESIS,
            umbrellaEnabled,
            umbrellaDelayMs,
            umbrellaTailMs
        );
    }

    String summary() {
        if (!hasSecondaryInput()) {
            return ">> Config: in: %s out: %s umbrella: %s delay: %d tail=%d sostCcIn: %d".formatted(
                primaryInputName,
                outputName,
                umbrellaEnabled,
                umbrellaDelayMs,
                umbrellaTailMs,
                sostenutoController
            );
        }
        return ">> Config: in: %s in2: %s out: %s umbrella: %s delay: %d tail: %d sostCcIn: %d".formatted(
            primaryInputName,
            secondaryInputName,
            outputName,
            umbrellaEnabled,
            umbrellaDelayMs,
            umbrellaTailMs,
            sostenutoController
        );
    }
}
