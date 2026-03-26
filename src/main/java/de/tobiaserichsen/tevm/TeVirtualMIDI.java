package de.tobiaserichsen.tevm;
/* teVirtualMIDI Java interface - v1.3.0.43
 *
 * Copyright 2009-2019, Tobias Erichsen
 * All rights reserved, unauthorized usage & distribution is prohibited.
 *
 * For technical or commercial requests contact: info <at> tobias-erichsen <dot> de
 *
 * teVirtualMIDI.sys is a kernel-mode device-driver which can be used to dynamically create & destroy
 * midiports on Windows (XP to Windows 8, 32bit & 64bit).  The "back-end" of teVirtualMIDI can be used
 * to create & destroy such ports and receive and transmit data from/to those created ports.
 *
 * File: TeVirtualMIDI.java
 *
 * This file implements the Java-class-wrapper for the teVirtualMIDI-driver.
 * This class encapsualtes the native C-type interface which is integrated
 * in the teVirtualMIDI32.dll and the teVirtualMIDI64.dll.
 */

import de.tobiaserichsen.tevm.TeVirtualMIDIException;

public class TeVirtualMIDI {

	/* default size of sysex-buffer */
	private static final int TE_VM_DEFAULT_SYSEX_SIZE = 65535;

	/* TE_VM_LOGGING_MISC - log internal stuff (port enable, disable...) */
	public static final int TE_VM_LOGGING_MISC = 1;
	/* TE_VM_LOGGING_RX - log data received from the driver */
	public static final int TE_VM_LOGGING_RX = 2;
	/* TE_VM_LOGGING_TX - log data sent to the driver */
	public static final int TE_VM_LOGGING_TX = 4;

	/* TE_VM_FLAGS_PARSE_RX - parse incoming data into single, valid MIDI-commands */
	public static final int TE_VM_FLAGS_PARSE_RX = 1;
	/* TE_VM_FLAGS_PARSE_TX - parse outgoing data into single, valid MIDI-commands */
	public static final int TE_VM_FLAGS_PARSE_TX = 2;
	/* TE_VM_FLAGS_INSTANTIATE_RX_ONLY - Only the "midi-out" part of the port is created */
	public static final int TE_VM_FLAGS_INSTANTIATE_RX_ONLY = 4;
	/* TE_VM_FLAGS_INSTANTIATE_TX_ONLY - Only the "midi-in" part of the port is created */
	public static final int TE_VM_FLAGS_INSTANTIATE_TX_ONLY = 8;
	/* TE_VM_FLAGS_INSTANTIATE_BOTH - a bidirectional port is created */
	public static final int TE_VM_FLAGS_INSTANTIATE_BOTH = 12;


	public TeVirtualMIDI( String portName, int maxSysexLength, int flags, String manuId, String prodId ) {

		this.isOpen = false;

		int error = nativePortCreateEx( portName, maxSysexLength, flags, manuId, prodId );

		if ( 0 != error ) {

			TeVirtualMIDIException.ThrowExceptionForReasonCode( error );

		}

		this.isOpen = true;

	}

	public TeVirtualMIDI( String portName, int maxSysexLength, int flags ) {

		this.isOpen = false;

		int error = nativePortCreate( portName, maxSysexLength, flags );

		if ( 0 != error ) {

			TeVirtualMIDIException.ThrowExceptionForReasonCode( error );

		}

		this.isOpen = true;

	}


	public TeVirtualMIDI( String portName, int maxSysexLength ) {

		this( portName, maxSysexLength, TE_VM_FLAGS_PARSE_RX );

	}


	public TeVirtualMIDI( String portName ) {

		this( portName, TE_VM_DEFAULT_SYSEX_SIZE, TE_VM_FLAGS_PARSE_RX );

	}


	public void shutdown( ) {

		int error = nativePortShutdown( this.handle );

		if ( 0 != error ) {

			TeVirtualMIDIException.ThrowExceptionForReasonCode( error );

		}

	}


	public void sendCommand( byte[] command ) {

		if ( command.length == 0 ) {

			return;

		}

		int error = nativeSendCommand( this.handle, command );

		if ( 0 != error ) {

			TeVirtualMIDIException.ThrowExceptionForReasonCode( error );

		}

	}


	public byte[] getCommand( ) {

		this.getError = 0;

		byte[] result = nativeGetCommand( this.handle );

		if ( result.length == 0 ) {

			if ( 0 != this.getError ) {

				TeVirtualMIDIException.ThrowExceptionForReasonCode( this.getError );

			}

		}

		return result;

	}

	public long[] getProcesses( ) {

		this.getError = 0;

		long[] result = nativeGetProcesses( this.handle );

		if ( 0 != this.getError ) {

			TeVirtualMIDIException.ThrowExceptionForReasonCode( this.getError );

		}

		return result;

	}

	public native static int getVersionMajor();
	public native static int getVersionMinor();
	public native static int getVersionRelease();
	public native static int getVersionBuild();
	public native static String getVersionString();
	
	public native static int getDriverVersionMajor();
	public native static int getDriverVersionMinor();
	public native static int getDriverVersionRelease();
	public native static int getDriverVersionBuild();
	public native static String getDriverVersionString();

	public native static int logging( int flags );


	private native int nativePortCreate( String portName, int maxSysexSize, int flags );
	private native int nativePortCreateEx( String portName, int maxSysexSize, int flags, String manuId, String prodId );
	private native int nativePortClose( long pointerReference );
	private native int nativePortShutdown( long pointerReference );
	private native int nativeSendCommand( long pointerReference, byte[] command );
	private native byte[] nativeGetCommand( long pointerReference );
	private native long[] nativeGetProcesses( long pointerReference );


	@Override
	protected void finalize() throws Throwable {

		try {

			if ( this.isOpen ) {

				nativePortClose( this.handle );

				this.isOpen = false;

			}

		} finally {

			super.finalize();

		}

	}


	private boolean isOpen;


 	static {
   		try {

			System.loadLibrary( "teVirtualMIDI" );

		} catch( UnsatisfiedLinkError ignored ) {

    			try {

				System.loadLibrary( "teVirtualMIDI32" );

			} catch( UnsatisfiedLinkError ignored2 ) {

				System.loadLibrary( "teVirtualMIDI64" );

			}
		}

	}


	// Never, ever change the following part of the class, since it is
	// used from within the native-JNI-DLL to store the object pointer
	// to the native teVirtualMIDI instance!
	private long handle;
	private int getError;

}
