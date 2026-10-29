package org.nb;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record LogFilter(@JsonProperty(required = true) List<Rule> blacklist) {
    static final LogFilter NONE = new LogFilter(List.of());

    LogFilter {
        if (blacklist == null || blacklist.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("logFilter.blacklist must be an array of rules");
        }
        blacklist = List.copyOf(blacklist);
    }

    boolean blocks(PackedMidiMessage message, String port) {
        return blacklist.stream().anyMatch(rule -> rule.matches(message, port));
    }

    record Rule(@JsonProperty(required = true) String type, Integer cc, Integer note,
                Integer channel, String port) {
        Rule {
            if (type == null) throw new IllegalArgumentException("Log rule type is required");
            MidiMessageType messageType = type.equals("NOTE") ? null : MidiMessageType.valueOf(type);
            if (cc != null && (cc < 0 || cc > 127 || !type.equals("CC"))) {
                throw new IllegalArgumentException("cc must be 0..127 and is only valid for CC rules");
            }
            boolean noteType = type.equals("NOTE") || type.equals("NOTE_ON") || type.equals("NOTE_OFF");
            if (note != null && (note < 0 || note > 127 || !noteType)) {
                throw new IllegalArgumentException("note must be 0..127 and is only valid for note rules");
            }
            if (channel != null && (channel < 1 || channel > 16
                    || (messageType != null && (messageType.command() >= 0xF0 || messageType == MidiMessageType.OTHER)))) {
                throw new IllegalArgumentException("channel must be 1..16 and is only valid for channel message types");
            }
            if (port != null && port.isBlank()) throw new IllegalArgumentException("port must not be blank");
        }

        boolean matches(PackedMidiMessage message, String inputPort) {
            MidiMessageType actual = MidiMessageType.from(message);
            // MIDI represents release as either Note Off or Note On with zero velocity.
            if (actual == MidiMessageType.NOTE_ON && message.data2() == 0) actual = MidiMessageType.NOTE_OFF;
            boolean typeMatches = type.equals("NOTE")
                ? actual == MidiMessageType.NOTE_ON || actual == MidiMessageType.NOTE_OFF
                : actual.name().equals(type);
            return typeMatches
                && (cc == null || message.data1() == cc)
                && (note == null || message.data1() == note)
                && (channel == null || message.channel() + 1 == channel)
                && (port == null || port.equals(inputPort));
        }
    }
}
