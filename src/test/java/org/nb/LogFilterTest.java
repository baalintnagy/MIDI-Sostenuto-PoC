package org.nb;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFilterTest {
    @TempDir Path directory;

    private AppConfig load(String filter) throws IOException {
        String base = AppConfigTest.CONFIG_JSON.stripTrailing();
        Path file = directory.resolve("config.json");
        Files.writeString(file, base.substring(0, base.length() - 1) + ",\"logFilter\":" + filter + "}");
        return AppConfig.load(file);
    }

    private PackedMidiMessage message(int status, int data1, int data2) {
        return new PackedMidiMessage(status | (data1 << 8) | (data2 << 16));
    }

    @Test
    void loadsRulesAndCombinesAllFieldsWithinEachRule() throws Exception {
        LogFilter filter = load("""
            {"blacklist":[{"type":"CLOCK"},{"type":"CC","cc":11,"channel":2,"port":"Pedals"}]}
            """).logFilter();
        assertTrue(filter.blocks(message(0xF8, 0, 0), "Keys"));
        assertTrue(filter.blocks(message(0xB1, 11, 127), "Pedals"));
        assertFalse(filter.blocks(message(0xB0, 11, 127), "Pedals"));
        assertFalse(filter.blocks(message(0xB1, 10, 127), "Pedals"));
        assertFalse(filter.blocks(message(0xB1, 11, 127), "Keys"));
        assertFalse(filter.blocks(message(0xFA, 0, 0), "Pedals"));
    }

    @Test
    void noteAliasAndReleaseSemantics() throws Exception {
        LogFilter both = load("{\"blacklist\":[{\"type\":\"NOTE\",\"note\":78}]}").logFilter();
        assertTrue(both.blocks(message(0x90, 78, 1), "Keys"));
        assertTrue(both.blocks(message(0x80, 78, 100), "Keys"));
        assertTrue(both.blocks(message(0x90, 78, 0), "Keys"));
        assertFalse(both.blocks(message(0x90, 77, 1), "Keys"));
        LogFilter off = load("{\"blacklist\":[{\"type\":\"NOTE_OFF\"}]}").logFilter();
        assertTrue(off.blocks(message(0x90, 78, 0), "Keys"));
        assertFalse(off.blocks(message(0x90, 78, 1), "Keys"));
        LogFilter on = load("{\"blacklist\":[{\"type\":\"NOTE_ON\"}]}").logFilter();
        assertFalse(on.blocks(message(0x90, 78, 0), "Keys"));
    }

    @Test
    void emptyOrOmittedFilterAllowsEverythingAndOtherMatchesUndefinedStatus() throws Exception {
        for (LogFilter filter : List.of(load("null").logFilter(), load("{\"blacklist\":[]}").logFilter(),
                AppConfig.parse("Keys,Out,false,0,0,22").logFilter())) {
            assertFalse(filter.blocks(message(0xF8, 0, 0), "Keys"));
        }
        LogFilter other = load("{\"blacklist\":[{\"type\":\"OTHER\"}]}").logFilter();
        assertTrue(other.blocks(message(0xF9, 0, 0), "Keys"));
        assertFalse(other.blocks(message(0xF8, 0, 0), "Keys"));
    }

    @Test
    void rejectsInvalidRulesRatherThanSilentlyIgnoringThem() {
        for (String rule : new String[] {"{}", "null", "{\"type\":\"clock\"}",
                "{\"type\":\"CC\",\"cc\":128}", "{\"type\":\"CC\",\"cc\":-1}",
                "{\"type\":\"NOTE\",\"note\":128}", "{\"type\":\"NOTE\",\"channel\":0}",
                "{\"type\":\"CC\",\"channel\":17}", "{\"type\":\"CLOCK\",\"channel\":1}",
                "{\"type\":\"NOTE_ON\",\"cc\":1}", "{\"type\":\"CC\",\"note\":1}",
                "{\"type\":\"CC\",\"port\":\" \"}", "{\"type\":\"CC\",\"typo\":1}",
                "{\"type\":\"CC\",\"cc\":\"1\"}"}) {
            assertThrows(IOException.class, () -> load("{\"blacklist\":[" + rule + "]}"), rule);
        }
        assertThrows(IOException.class, () -> load("{}"));
        assertThrows(IOException.class, () -> load("{\"blacklist\":null}"));
    }
}
