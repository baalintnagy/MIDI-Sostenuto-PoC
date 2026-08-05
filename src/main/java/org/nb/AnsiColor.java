package org.nb;

public enum AnsiColor {

    RED(31),
    GREEN(32),
    BLUE(34),

    WHITE(37),
    GRAY(30),

    CYAN(36),
    YELLOW(33),
    MAGENTA(35);

    public static final String RESET = "\u001B[0m";

    private static final int BRIGHT_OFFSET = 60;

    private final int code;

    AnsiColor(int code) {
        this.code = code;
    }

    public String dark() {
        return escapeSequence(code);
    }

    public String bright() {
        return escapeSequence(code + BRIGHT_OFFSET);
    }

    private static String escapeSequence(int colorCode) {
        return "\u001B[%dm".formatted(colorCode);
    }
}
