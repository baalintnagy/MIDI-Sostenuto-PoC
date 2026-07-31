package org.nb;

import java.util.function.IntConsumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

final class WinMMInput implements AutoCloseable {

    private static final int CALLBACK_FUNCTION = 0x30000;
    private static final int MIDI_INPUT_DATA = 0x3C3;

    private final WinMM winMM;
    private final WinMM.MidiInputCallback nativeCallback;
    private final Pointer handle;

    WinMMInput(WinMM winMM, int deviceId, IntConsumer messageHandler) {
        this.winMM = winMM;
        nativeCallback = (inputHandle, message, instance, param1, param2) -> {
            if (message == MIDI_INPUT_DATA) {
                messageHandler.accept((int) Pointer.nativeValue(param1));
            }
        };

        PointerByReference handleReference = new PointerByReference();
        winMM.midiInOpen(handleReference, deviceId, nativeCallback, null, CALLBACK_FUNCTION);
        handle = handleReference.getValue();
    }

    void start() {
        winMM.midiInStart(handle);
    }

    @Override
    public void close() {
        winMM.midiInStop(handle);
        winMM.midiInClose(handle);
    }
}
