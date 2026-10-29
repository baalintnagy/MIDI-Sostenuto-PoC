package org.nb;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationMenuTest {
    @TempDir Path directory;

    private Path config(String name) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, AppConfigTest.CONFIG_JSON);
        return file;
    }

    @Test
    void discoversSortedJsonFilesWithWorkingDirectoryPrecedence() throws Exception {
        Path packaged = Files.createDirectory(directory.resolve("app"));
        Path z = config("z.JSON");
        Path a = config("a.json");
        Files.writeString(directory.resolve("ignore.txt"), "");
        Files.createDirectory(directory.resolve("folder.json"));
        Files.writeString(packaged.resolve("a.json"), "duplicate");
        Path b = Files.writeString(packaged.resolve("b.json"), "{}");
        assertEquals(List.of(a, b, z), ConfigurationMenu.discover(List.of(directory, packaged)));
    }

    @Test
    void selectsNumberAfterInvalidSelectionsAndInvalidFile() throws Exception {
        Path broken = Files.writeString(directory.resolve("broken.json"), "{}");
        Path valid = config("valid.json");
        var bytes = new ByteArrayOutputStream();
        AppConfig selected = ConfigurationMenu.select(
            new BufferedReader(new StringReader("text\n\n-1\n3\n1\n2\n")),
            new PrintStream(bytes), List.of(broken, valid), () -> fail("Unexpected manual entry"));
        assertEquals(AppConfig.load(valid), selected);
        assertTrue(bytes.toString().contains("0. Other..."));
        assertTrue(bytes.toString().contains("2. valid.json"));
        assertTrue(bytes.toString().contains("Cannot load configuration"));
    }

    @Test
    void otherRepeatsDevicesThenShowsManualPromptEvenWithoutJsonFiles() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = new PrintStream(bytes);
        var calls = new AtomicInteger();
        AppConfig selected = ConfigurationMenu.select(
            new BufferedReader(new StringReader("0\nKeys,Pedals,Out,false,0,0,toggle:true,note:60\n")),
            output, List.of(), () -> { calls.incrementAndGet(); output.println("MIDI PORTS"); });
        assertEquals(1, calls.get());
        assertEquals(60, selected.sostenutoNote());
        assertTrue(selected.sostenutoToggle());
        assertTrue(bytes.toString().indexOf("MIDI PORTS") < bytes.toString().indexOf("Enter config:"));
    }

    @Test
    void manualBlankAndInvalidInputRetryWithoutLoadingAFile() throws Exception {
        var bytes = new ByteArrayOutputStream();
        AppConfig selected = ConfigurationMenu.select(
            new BufferedReader(new StringReader("0\n\ninvalid\nKeys,Out,false,0,0,22\n")),
            new PrintStream(bytes), List.of(config("available.json")), () -> {});
        assertEquals("Keys", selected.primaryInputName());
        assertEquals(22, selected.sostenutoCC());
        assertTrue(bytes.toString().contains("Invalid configuration:"));
        assertFalse(bytes.toString().contains("sustsos.json"));
        assertFalse(bytes.toString().toLowerCase().contains("default"));
        assertTrue(bytes.toString().contains("0,0,22"));
        assertTrue(bytes.toString().contains("note:78"));
    }

    @Test
    void eofStopsAtMenuAndManualPrompt() {
        for (String input : new String[] {"", "0\n"}) {
            assertThrows(IOException.class, () -> ConfigurationMenu.select(
                new BufferedReader(new StringReader(input)),
                new PrintStream(new ByteArrayOutputStream()), List.of(), () -> {}));
        }
    }
}
