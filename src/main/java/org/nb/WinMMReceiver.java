package org.nb;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.sun.jna.Pointer;

final class WinMMReceiver implements Receiver {

    private final WinMM winMM;
    private final Pointer handle;

    WinMMReceiver(WinMM winMM, Pointer handle) {
        this.winMM = winMM;
        this.handle = handle;
    }

    @Override
    public void send(MidiMessage message, long timestamp) {
        if (message instanceof ShortMessage shortMessage) {
            winMM.midiOutShortMsg(handle, PackedMidiMessage.from(shortMessage).value());
        }
    }

    @Override
    public void close() {
        winMM.midiOutClose(handle);
    }
}
