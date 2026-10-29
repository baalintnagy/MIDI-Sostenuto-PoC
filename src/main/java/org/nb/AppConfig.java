package org.nb;

import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

record AppConfig(
    @JsonProperty(required = true) String primaryInputName,
    @JsonProperty(required = true) String secondaryInputName,
    @JsonProperty(required = true) String outputName,
    @JsonProperty(required = true) boolean umbrellaEnabled,
    @JsonProperty(required = true) int umbrellaDelayMs,
    @JsonProperty(required = true) int umbrellaTailMs,
    @JsonProperty(required = true) int sostenutoCC,
    Integer sostenutoNote,
    Boolean sostenutoNoteToggle
) {

    private static final int DEFAULT_SOSTENUTO_CC = 22;
    private static final int SOSTENUTO_THRESHOLD = 10;
    private static final int SOSTENUTO_HYSTERESIS = 2;

    AppConfig(String primaryInputName, String secondaryInputName, String outputName,
              boolean umbrellaEnabled, int umbrellaDelayMs, int umbrellaTailMs, int sostenutoCC) {
        this(primaryInputName, secondaryInputName, outputName, umbrellaEnabled,
            umbrellaDelayMs, umbrellaTailMs, sostenutoCC, null);
    }

    AppConfig(String primaryInputName, String secondaryInputName, String outputName,
              boolean umbrellaEnabled, int umbrellaDelayMs, int umbrellaTailMs,
              int sostenutoCC, Integer sostenutoNote) {
        this(primaryInputName, secondaryInputName, outputName, umbrellaEnabled,
            umbrellaDelayMs, umbrellaTailMs, sostenutoCC, sostenutoNote, false);
    }

    AppConfig {
        sostenutoNoteToggle = Boolean.TRUE.equals(sostenutoNoteToggle);
        if (sostenutoNote != null && (sostenutoNote < 0 || sostenutoNote > 127)) {
            throw new IllegalArgumentException("sostenutoNote must be 0..127");
        }
        if (primaryInputName == null || primaryInputName.isBlank()
            || outputName == null || outputName.isBlank() || secondaryInputName == null) {
            throw new IllegalArgumentException("Input/output names are required; use an empty secondaryInputName for one input");
        }
        if (umbrellaDelayMs < 0 || umbrellaTailMs < 0 || sostenutoCC < 0 || sostenutoCC > 127) {
            throw new IllegalArgumentException("Timings must be nonnegative and sostenutoCC must be 0..127");
        }
    }

    static AppConfig load(Path path) throws IOException {
        try {
            AppConfig config = JsonMapper.builder()
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

    static java.util.List<Path> configurationDirectories() throws Exception {
        Path working = Path.of("").toAbsolutePath();
        Path application = Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(application) && !working.equals(application.getParent())) {
            return java.util.List.of(working, application.getParent());
        }
        return java.util.List.of(working);
    }

    static AppConfig parse(String input) {
        String line = Objects.requireNonNull(input, "input").trim();
        boolean toggle = false;
        int modeSeparator = line.lastIndexOf(',');
        String mode = line.substring(modeSeparator + 1).trim().toLowerCase(Locale.ROOT);
        if (mode.startsWith("toggle:")) {
            String enabled = mode.substring(7).trim();
            if (!enabled.equals("true") && !enabled.equals("false")) {
                throw new IllegalArgumentException("toggle must be true or false");
            }
            toggle = Boolean.parseBoolean(enabled);
            line = line.substring(0, modeSeparator).trim();
        }
        Integer note = null;
        int triggerSeparator = line.lastIndexOf(',');
        String trigger = line.substring(triggerSeparator + 1).trim();
        if (trigger.toLowerCase(Locale.ROOT).startsWith("note:")) {
            note = Integer.parseInt(trigger.substring(5).trim());
            line = line.substring(0, triggerSeparator).trim();
        }
        String[] tokens = line.split("\\s*,\\s*", -1);
        boolean hasCustomSostenutoCC = hasCustomSostenutoCC(tokens);

        int tailSeparator = line.lastIndexOf(',');
        int sostenutoCC = DEFAULT_SOSTENUTO_CC;
        if (hasCustomSostenutoCC) {
            sostenutoCC = Integer.parseInt(line.substring(tailSeparator + 1).trim());
            line = line.substring(0, tailSeparator).trim();
            tailSeparator = line.lastIndexOf(',');
        }

        int fieldCount = line.split(",", -1).length;
        if (fieldCount != 5 && fieldCount != 6) {
            throw new IllegalArgumentException("Expected primary input, optional secondary input, output, umbrella, delay and tail");
        }

        int delaySeparator = line.lastIndexOf(',', tailSeparator - 1);
        int umbrellaSeparator = line.lastIndexOf(',', delaySeparator - 1);
        int outputSeparator = line.lastIndexOf(',', umbrellaSeparator - 1);
        int secondaryInputSeparator = line.lastIndexOf(',', outputSeparator - 1);

        int umbrellaTailMs = Integer.parseInt(line.substring(tailSeparator + 1).trim());
        int umbrellaDelayMs = Integer.parseInt(line.substring(delaySeparator + 1, tailSeparator).trim());
        String umbrella = line.substring(umbrellaSeparator + 1, delaySeparator).trim();
        if (!umbrella.equalsIgnoreCase("true") && !umbrella.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("umbrella must be true or false");
        }
        boolean umbrellaEnabled = Boolean.parseBoolean(umbrella);
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
            sostenutoCC,
            note,
            toggle
        );
    }

    private static boolean hasCustomSostenutoCC(String[] tokens) {
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

    String sostenutoTrigger() {
        return sostenutoNote == null ? "CC" + sostenutoCC : "Note " + sostenutoNote + (sostenutoNoteToggle ? " (toggle)" : " (hold)");
    }

    String summary() {
        return ">> Config: in: %s%s out: %s umbrella: %s delay: %d tail: %d sostenuto input: %s".formatted(
            primaryInputName,
            hasSecondaryInput() ? " in2: " + secondaryInputName : "",
            outputName,
            umbrellaEnabled,
            umbrellaDelayMs,
            umbrellaTailMs,
            sostenutoTrigger()
        );
    }
}
