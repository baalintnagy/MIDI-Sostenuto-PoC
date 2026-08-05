package org.nb;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

interface WinMM extends Library {

    WinMM INSTANCE = Native.load("winmm", WinMM.class);

    int midiInGetNumDevs();

    int midiInGetDevCapsA(int deviceId, MidiInputCapabilities capabilities, int size);

    int midiInOpen(
        PointerByReference handle,
        int deviceId,
        MidiInputCallback callback,
        Pointer instance,
        int flags
    );

    int midiInStart(Pointer handle);

    int midiInStop(Pointer handle);

    int midiInClose(Pointer handle);

    int midiOutGetNumDevs();

    int midiOutGetDevCapsA(int deviceId, MidiOutputCapabilities capabilities, int size);

    int midiOutOpen(
        PointerByReference handle,
        int deviceId,
        Pointer callback,
        Pointer instance,
        int flags
    );

    int midiOutShortMsg(Pointer handle, int message);

    int midiOutClose(Pointer handle);

    interface MidiInputCallback extends Callback {
        void invoke(Pointer handle, int message, Pointer instance, Pointer param1, Pointer param2);
    }

    @Structure.FieldOrder({"manufacturerId", "productId", "driverVersion", "productName"})
    class MidiInputCapabilities extends Structure {
        public short manufacturerId;
        public short productId;
        public int driverVersion;
        public byte[] productName = new byte[32];

        String name() {
            return Native.toString(productName);
        }
    }

    @Structure.FieldOrder({
        "manufacturerId",
        "productId",
        "driverVersion",
        "productName",
        "technology",
        "voices",
        "notes",
        "channelMask",
        "support"
    })
    class MidiOutputCapabilities extends Structure {
        public short manufacturerId;
        public short productId;
        public int driverVersion;
        public byte[] productName = new byte[32];
        public short technology;
        public short voices;
        public short notes;
        public short channelMask;
        public int support;

        String name() {
            return Native.toString(productName);
        }
    }
}
