package org.nb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.Test;

class AppConfigTest {
    @TempDir
    Path directory;

    @Test
    void usesJsonForBlankEofAndInvalidInputButAllowsInteractiveOverride() throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(path, Files.readString(Path.of("sustsos.json"))
            .replace("Hammer 88 Pro", "Custom Keyboard").replace("22", "67"));
        for (String input : new String[] {"", " ", null, "invalid"}) {
            AppConfig config = SustSosApplication.resolveConfiguration(input, path);
            assertEquals("Custom Keyboard", config.primaryInputName());
            assertEquals(67, config.sostenutoController());
        }
        assertEquals("Keyboard", SustSosApplication.resolveConfiguration(
            "Keyboard, Loopback, true, 2, 3", directory.resolve("missing.json")).primaryInputName());
    }

    @Test
    void rejectsMissingMalformedIncompleteAndInvalidJson() throws Exception {
        Path path = directory.resolve("config.json");
        assertThrows(IOException.class, () -> AppConfig.load(path));
        String valid = Files.readString(Path.of("sustsos.json"));
        for (String json : new String[] {"{", "{}", "null", valid + " {}",
            valid.replace("true", "null"), valid.replace("true", "\"true\""),
            valid.replace("22", "128"), valid.replace("22", "-1"),
            valid.replace("\"umbrellaDelayMs\": 1", "\"umbrellaDelayMs\": -1"),
            valid.replace("\"outputName\"", "\"typo\"")}) {
            Files.writeString(path, json);
            assertThrows(IOException.class, () -> AppConfig.load(path), json);
        }
    }

    @Test
    void acceptsJsonWithoutSecondaryDevice() throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(path, Files.readString(Path.of("sustsos.json"))
            .replace("MIDIIN3 (Hammer 88 Pro)", ""));
        assertFalse(AppConfig.load(path).hasSecondaryInput());
    }

    @Test
    void parsesSingleInputConfiguration() {
        AppConfig config = AppConfig.parse("Keyboard, Loopback, true, 2, 3");

        assertEquals("Keyboard", config.primaryInputName());
        assertEquals("", config.secondaryInputName());
        assertEquals("Loopback", config.outputName());
        assertTrue(config.umbrellaEnabled());
        assertEquals(2, config.umbrellaDelayMs());
        assertEquals(3, config.umbrellaTailMs());
        assertEquals(22, config.sostenutoController());
        assertFalse(config.hasSecondaryInput());
    }

    @Test
    void parsesDualInputConfiguration() {
        AppConfig config = AppConfig.parse("Keyboard, Pedals, Loopback, false, 0, 1");

        assertEquals("Keyboard", config.primaryInputName());
        assertEquals("Pedals", config.secondaryInputName());
        assertEquals("Loopback", config.outputName());
        assertFalse(config.umbrellaEnabled());
        assertTrue(config.hasSecondaryInput());
    }

    @Test
    void parsesCustomSostenutoControllerForEitherInputShape() {
        AppConfig singleInput = AppConfig.parse("Keyboard, Loopback, true, 1, 2, 67");
        AppConfig dualInput = AppConfig.parse("Keyboard, Pedals, Loopback, true, 1, 2, 68");

        assertEquals(67, singleInput.sostenutoController());
        assertFalse(singleInput.hasSecondaryInput());
        assertEquals(68, dualInput.sostenutoController());
        assertTrue(dualInput.hasSecondaryInput());
    }

    @Test
    void preservesDocumentedHardwareDefaults() throws Exception {
        AppConfig defaults = AppConfig.load(java.nio.file.Path.of("sustsos.json"));

        assertEquals("Hammer 88 Pro", defaults.primaryInputName());
        assertEquals("MIDIIN3 (Hammer 88 Pro)", defaults.secondaryInputName());
        assertEquals("postSSB", defaults.outputName());
        assertEquals(22, defaults.sostenutoController());
    }
}
