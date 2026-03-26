package de.tobiaserichsen.tevm;
/* teVirtualMIDI Java interface
 *
 * Copyright 2009-2019, Tobias Erichsen
 * All rights reserved, unauthorized usage & distribution is prohibited.
 *
 *
 * File: TeVirtualMIDIException.java
 *
 * This file implements the exceptions that may occur when using the
 * the TeVirtualMIDI-class.  
 */


@SuppressWarnings("serial")
public class TeVirtualMIDIException extends RuntimeException {

        private static final int ERROR_PATH_NOT_FOUND    = 3;
        private static final int ERROR_INVALID_HANDLE    = 6;
        private static final int ERROR_TOO_MANY_CMDS     = 56;
        private static final int ERROR_TOO_MANY_SESS     = 69;
        private static final int ERROR_INVALID_PARAMETER = 87;
        private static final int ERROR_INVALID_NAME      = 123;
        private static final int ERROR_MOD_NOT_FOUND     = 126;
	private static final int ERROR_BAD_ARGUMENTS	 = 160;
        private static final int ERROR_ALREADY_EXISTS    = 183;
        private static final int ERROR_OLD_WIN_VERSION   = 1150;
        private static final int ERROR_REVISION_MISMATCH = 1306;
        private static final int ERROR_ALIAS_EXISTS      = 1379;

        public TeVirtualMIDIException( int code ) {

		super( reasonCodeToString( code ) );

		this.reason = reasonCodeToString( code );

		this.reasonCode = code;

	}


	public int getReasonCode( ) {

		return this.reasonCode;

	}


	public String getReason( ) {

		return this.reason;

	}


	@Override
	public String toString() {

		return this.reason + " ( " + this.reasonCode + " )";

	}


	private String reason;
	private int reasonCode;


	private static String reasonCodeToString( int reasonCode ) {

		switch( reasonCode ) {

			case ERROR_OLD_WIN_VERSION:
				return "Your Windows-version is too old for dynamic MIDI-port creation.";

			case ERROR_INVALID_NAME:
				return "You need to specify at least 1 character as MIDI-portname!";

			case ERROR_ALREADY_EXISTS:
				return "The name for the MIDI-port you specified is already in use!";

			case ERROR_ALIAS_EXISTS:
				return "The name for the MIDI-port you specified is already in use!";

			case ERROR_PATH_NOT_FOUND:
				return "Possibly the teVirtualMIDI-driver has not been installed!";

			case ERROR_MOD_NOT_FOUND:
				return "The teVirtualMIDIxx.dll could not be loaded!";

			case ERROR_REVISION_MISMATCH:
				return "The teVirtualMIDIxx.dll and teVirtualMIDI.sys driver differ in version!";

			case ERROR_TOO_MANY_SESS:
				return "Maximum number of ports reached";
			
			case ERROR_INVALID_PARAMETER:
				return "Parameter was invalid";

			case ERROR_INVALID_HANDLE:
				return "Port not enabled";

			case ERROR_TOO_MANY_CMDS:
				return "MIDI-command too large";

			case ERROR_BAD_ARGUMENTS:
				return "Invalid flags specified";

			default:
				return "Unspecified virtualMIDI-error: "+reasonCode;

			}

        }

	public static void ThrowExceptionForReasonCode( int reasonCode ) {
		throw new TeVirtualMIDIException( reasonCode );
	}
}
