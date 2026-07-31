package org.nb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppConfigTest {

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
    void preservesDocumentedHardwareDefaults() {
        AppConfig defaults = AppConfig.defaults();

        assertEquals("Hammer 88 Pro", defaults.primaryInputName());
        assertEquals("MIDIIN3 (Hammer 88 Pro)", defaults.secondaryInputName());
        assertEquals("postSSB", defaults.outputName());
        assertEquals(22, defaults.sostenutoController());
    }
}
