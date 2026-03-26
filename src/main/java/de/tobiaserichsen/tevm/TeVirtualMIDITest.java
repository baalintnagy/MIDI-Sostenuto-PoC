package de.tobiaserichsen.tevm;


/* teVirtualMIDI Java interface
 *
 * Copyright 2009-2019, Tobias Erichsen
 * All rights reserved, unauthorized usage & distribution is prohibited.
 *
 *
 * File: TeVirtualMIDITest.java
 *
 * This file contains a sample using the TeVirtualMIDI-class, which
 * implements a simple loopback-MIDI-port.
 */

import java.util.Scanner;

import de.tobiaserichsen.tevm.TeVirtualMIDI;
import de.tobiaserichsen.tevm.TeVirtualMIDIException;

public class TeVirtualMIDITest extends Thread {

	TeVirtualMIDI port;

	public TeVirtualMIDITest ( String name ) {

		this.port = new TeVirtualMIDI( name );

	}

	public TeVirtualMIDITest ( String name, int maxSysexLength, int flags, String manuId, String prodId ) {

		this.port = new TeVirtualMIDI( name, maxSysexLength, flags, manuId, prodId );

	}

	public void shutdown() {

		this.port.shutdown();

	}

	static public String byteArrayToString( byte[] b ) {

		StringBuffer sb = new StringBuffer();

		for ( int i = 0; i < b.length; i++ ) {

			if ( i > 0 ) {

				sb.append( ':' );

			}

			sb.append( Integer.toString( ( b[i] & 0xff ) + 0x100, 16 ).substring( 1 ) );

		}

		return sb.toString();

	}


	@Override
	public void run() {

		byte[] command;

		try {

			while( true ) {

				command = this.port.getCommand();

				this.port.sendCommand( command );

				System.out.println( "command: " + byteArrayToString( command ) );

			}
		} catch ( TeVirtualMIDIException e ) {

			System.out.println( "thread aborting: " + e );

			return;

		}

	}

	
	public static void main( String[] args ) {

		System.out.println( "teVirtualMIDI Java loopback sample" );
		System.out.println( "using dll-version:    " + TeVirtualMIDI.getVersionString() );
		System.out.println( "using driver-version: " + TeVirtualMIDI.getDriverVersionString() );

		TeVirtualMIDI.logging( TeVirtualMIDI.TE_VM_LOGGING_MISC | TeVirtualMIDI.TE_VM_LOGGING_RX | TeVirtualMIDI.TE_VM_LOGGING_TX );

		TeVirtualMIDITest test = new TeVirtualMIDITest( "Java loopback" );
//		below a more complex example on how to create a port that overrides the manufacturer & product guids
//		The guids below are just a sample and should be exchanged with new ones created individually.
//		TeVirtualMIDITest test = new TeVirtualMIDITest( "Java loopback", 65535, TeVirtualMIDI.TE_VM_FLAGS_PARSE_RX, "{aa4e075f-3504-4aab-9b06-9a4104a91cf0}", "{bb4e075f-3504-4aab-9b06-9a4104a91cf0}"  ); 
		test.start();

		System.out.println( "Virtual port created - press enter to close port again" );
		Scanner scanner = new Scanner(System.in);
		scanner.nextLine();
		test.shutdown();

		System.out.println( "Virtual port closed - press enter to terminate application" );
		scanner.nextLine();
		scanner.close();

	}

}
