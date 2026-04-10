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

    private final int code;

    AnsiColor(int code) {
        this.code = code;
    }

    public static final String RESET = "\u001B[0m";

    public String dark() {
        return "\u001B[%dm".formatted(code);
    }

    public String bright() {
        return "\u001B[%dm".formatted(code + 60);
    }

}