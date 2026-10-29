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
    static final String CONFIG_JSON = """
        {
          "primaryInputName": "Hammer 88 Pro",
          "secondaryInputName": "LPProMK3 MIDI",
          "outputName": "postSSB",
          "umbrellaEnabled": true,
          "umbrellaDelayMs": 1,
          "umbrellaTailMs": 1,
          "sostenutoCC": 22,
          "sostenutoNote": null,
          "sostenutoNoteToggle": false
        }
        """;
    @TempDir
    Path directory;

    @Test
    void rejectsMissingMalformedIncompleteAndInvalidJson() throws Exception {
        Path path = directory.resolve("config.json");
        assertThrows(IOException.class, () -> AppConfig.load(path));
        String valid = CONFIG_JSON;
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
        Files.writeString(path, CONFIG_JSON
            .replace("LPProMK3 MIDI", ""));
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
        assertEquals(22, config.sostenutoCC());
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
    void parsesCustomSostenutoCCForEitherInputShape() {
        AppConfig singleInput = AppConfig.parse("Keyboard, Loopback, true, 1, 2, 67");
        AppConfig dualInput = AppConfig.parse("Keyboard, Pedals, Loopback, true, 1, 2, 68");

        assertEquals(67, singleInput.sostenutoCC());
        assertFalse(singleInput.hasSecondaryInput());
        assertEquals(68, dualInput.sostenutoCC());
        assertTrue(dualInput.hasSecondaryInput());
    }

    @Test
    void supportsOptionalNoteInJsonAndInteractiveConfiguration() throws Exception {
        Path path = directory.resolve("note.json");
        String original = CONFIG_JSON
            .replaceAll("\"sostenutoNote\"\\s*:\\s*(null|[0-9]+)", "\"sostenutoNote\": null")
            .replaceAll("\"sostenutoNoteToggle\"\\s*:\\s*(true|false)", "\"sostenutoNoteToggle\": false");
        Files.writeString(path, original.replace("\"sostenutoNote\": null", "\"sostenutoNote\": 60"));
        assertEquals(60, AppConfig.load(path).sostenutoNote());
        assertFalse(AppConfig.load(path).sostenutoNoteToggle());
        Files.writeString(path, Files.readString(path).replace("\"sostenutoNoteToggle\": false", "\"sostenutoNoteToggle\": true"));
        assertTrue(AppConfig.load(path).sostenutoNoteToggle());
        assertTrue(AppConfig.parse("Keys, Out, false, 0, 0, note:60, toggle:true").sostenutoNoteToggle());
        assertFalse(AppConfig.parse("Keys, Out, false, 0, 0, note:60, toggle:false").sostenutoNoteToggle());
        assertThrows(IllegalArgumentException.class,
            () -> AppConfig.parse("Keys, Out, false, 0, 0, note:60, toggle:yes"));
        assertEquals(60, AppConfig.parse("Keys, Pedal, Out, false, 0, 0, note:60").sostenutoNote());
        assertEquals(60, AppConfig.parse("Keys, Out, false, 0, 0, 22, note:60").sostenutoNote());
        assertThrows(IllegalArgumentException.class,
            () -> AppConfig.parse("Keys, Out, false, 0, 0, note:128"));
        Files.writeString(path, original.replace("\"sostenutoNote\": null", "\"sostenutoNote\": -1"));
        assertThrows(IOException.class, () -> AppConfig.load(path));
    }

    @Test
    void acceptsAllDocumentedTriggerFormsOnEitherPort() {
        for (String devices : new String[] {"Keys,Out", "Keys,Pedal,Out"}) {
            for (String trigger : new String[] {"", ",67", ",note:60", ",67,note:60"}) {
                for (String mode : new String[] {"", ",toggle:false", ",toggle:true"}) {
                    AppConfig config = AppConfig.parse(devices + ",true,1,2" + trigger + mode);
                    assertEquals(trigger.contains("67") ? 67 : 22, config.sostenutoCC());
                    assertEquals(trigger.contains("note:") ? Integer.valueOf(60) : null, config.sostenutoNote());
                    assertEquals(mode.equals(",toggle:true"), config.sostenutoNoteToggle());
                    assertEquals(devices.contains("Pedal"), config.hasSecondaryInput());
                }
            }
        }
        for (String input : new String[] {
            "Keys,Out,maybe,0,0", "Keys,Out,false,0,0,",
            "Keys,Out,false,0,0,toggle:yes", "Keys,Out,false,0,0,note:128",
            "Keys,Out,false,0,0,note:-1", "Keys,Out,false,0,0,128",
            "Keys,Out,false,0,0,toggle:true,note:60"}) {
            assertThrows(RuntimeException.class, () -> AppConfig.parse(input), input);
        }
        assertTrue(SustSosApplication.CONFIG_PROMPT.contains("note:<0..127>"));
        assertTrue(SustSosApplication.CONFIG_PROMPT.contains("toggle:<true/false>"));
        String summary = AppConfig.parse("Keys,Out,false,0,0,note:60,toggle:true").summary();
        assertTrue(summary.contains("Note 60 (toggle)"));
        assertFalse(summary.contains("sostCcIn"));
    }

    @Test
    void loadsExplicitlySelectedConfiguration() throws Exception {
        Path selected = directory.resolve("chosen.json");
        Files.writeString(selected, CONFIG_JSON);
        AppConfig defaults = AppConfig.load(selected);

        assertEquals("Hammer 88 Pro", defaults.primaryInputName());
        assertEquals("LPProMK3 MIDI", defaults.secondaryInputName());
        assertEquals("postSSB", defaults.outputName());
        assertEquals(22, defaults.sostenutoCC());
    }
}
