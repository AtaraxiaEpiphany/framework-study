package com.hmdp.utils;

/**
 * FG for foreground.
 * BG for Background.
 */
public enum PrintColor {
    FG_Black("\33[30m"),
    FG_RED("\33[31m"),
    FG_GREEN("\33[32m"),
    FG_YELLOW("\33[33m"),
    FG_BLUE("\33[34m"),
    FG_MAGENTA("\33[35m"),
    FG_CYAN("\33[36m"),
    FG_WHITE("\33[37m"),

    BG_Black("\33[40m"),
    BG_RED("\33[41m"),
    BG_GREEN("\33[42m"),
    BG_YELLOW("\33[43m"),
    BG_BLUE("\33[44m"),
    BG_MAGENTA("\33[45m"),
    BG_CYAN("\33[46m"),
    BG_WHITE("\33[47m"),

    NONE("\33[m");
    private final String code;

    PrintColor(String code) {
        this.code = code;
    }

    public void printWithColor(String text) {
        System.out.println(this.code + text + NONE.code);
    }
}
