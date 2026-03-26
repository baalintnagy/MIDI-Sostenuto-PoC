package org.nb;

import de.tobiaserichsen.tevm.TeVirtualMIDI;

public class TeVMTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Driver: " + TeVirtualMIDI.getDriverVersionString());
        System.out.println("java.library.path: " + System.getProperty("java.library.path"));

        try {
            TeVirtualMIDI port = new TeVirtualMIDI("TestPort123", 65535,
                TeVirtualMIDI.TE_VM_FLAGS_PARSE_RX |
                    TeVirtualMIDI.TE_VM_FLAGS_PARSE_TX |
                TeVirtualMIDI.TE_VM_FLAGS_INSTANTIATE_BOTH);

            System.out.println("Port created OK");
            System.out.println("Processes: " + java.util.Arrays.toString(port.getProcesses()));
            System.out.println("Press Enter...");
            System.in.read();
            port.shutdown();
        } catch (Throwable t) {
            System.err.println("FAILED: " + t);
            t.printStackTrace();
        }
    }
}