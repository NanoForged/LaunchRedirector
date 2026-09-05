package io.github.nanoforged.launchredirector.api.model;

import org.jetbrains.annotations.NotNull;

public record Resolution(int width, int height) {

    public static Resolution parse(String str) {
        if (str == null || str.isBlank()) {
            return new Resolution(1920, 1080); // 默认值
        }
        String[] parts = str.split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid resolution format: " + str + ". Expected 'WxH'");
        }
        return new Resolution(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }


    public static Resolution of(int width, int height) {
        return new Resolution(width, height);
    }


    public static Resolution of(String w, String h) {
        if (w == null || h == null || w.isBlank() || h.isBlank()) {
            return new Resolution(1920, 1080);
        }
        return of(Integer.parseInt(w), Integer.parseInt(h));
    }

    @Override
    public @NotNull String toString() {
        return width + "x" + height;
    }
}