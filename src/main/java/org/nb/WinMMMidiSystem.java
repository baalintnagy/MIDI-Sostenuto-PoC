package org.nb;

import java.io.PrintStream;
import java.util.function.IntConsumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

final class WinMMMidiSystem {

    private final WinMM winMM;

    WinMMMidiSystem() {
        this(WinMM.INSTANCE);
    }

    WinMMMidiSystem(WinMM winMM) {
        this.winMM = winMM;
    }

    void printDevices(PrintStream output) {
        output.println("=== WinMM MIDI Inputs ===");
        int inputCount = winMM.midiInGetNumDevs();
        for (int deviceId = 0; deviceId < inputCount; deviceId++) {
            WinMM.MidiInputCapabilities capabilities = inputCapabilities(deviceId);
            output.printf("  [%d] %s%n", deviceId, capabilities.name());
        }

        output.println();
        output.println("=== WinMM MIDI Outputs ===");
        int outputCount = winMM.midiOutGetNumDevs();
        for (int deviceId = 0; deviceId < outputCount; deviceId++) {
            WinMM.MidiOutputCapabilities capabilities = outputCapabilities(deviceId);
            output.printf("  [%d] %s%n", deviceId, capabilities.name());
        }
        output.println();
    }

    WinMMInput openInput(String name, IntConsumer messageHandler) {
        return new WinMMInput(winMM, findInput(name), messageHandler);
    }

    WinMMReceiver openOutput(String name) {
        PointerByReference handleReference = new PointerByReference();
        winMM.midiOutOpen(handleReference, findOutput(name), null, null, 0);
        Pointer handle = handleReference.getValue();
        return new WinMMReceiver(winMM, handle);
    }

    private int findInput(String name) {
        int deviceCount = winMM.midiInGetNumDevs();
        for (int deviceId = 0; deviceId < deviceCount; deviceId++) {
            if (inputCapabilities(deviceId).name().equalsIgnoreCase(name)) {
                return deviceId;
            }
        }
        throw new IllegalStateException("MIDI input not found: " + name);
    }

    private int findOutput(String name) {
        int deviceCount = winMM.midiOutGetNumDevs();
        for (int deviceId = 0; deviceId < deviceCount; deviceId++) {
            if (outputCapabilities(deviceId).name().equalsIgnoreCase(name)) {
                return deviceId;
            }
        }
        throw new IllegalStateException("MIDI output not found: " + name);
    }

    private WinMM.MidiInputCapabilities inputCapabilities(int deviceId) {
        WinMM.MidiInputCapabilities capabilities = new WinMM.MidiInputCapabilities();
        winMM.midiInGetDevCapsA(deviceId, capabilities, capabilities.size());
        return capabilities;
    }

    private WinMM.MidiOutputCapabilities outputCapabilities(int deviceId) {
        WinMM.MidiOutputCapabilities capabilities = new WinMM.MidiOutputCapabilities();
        winMM.midiOutGetDevCapsA(deviceId, capabilities, capabilities.size());
        return capabilities;
    }
}
