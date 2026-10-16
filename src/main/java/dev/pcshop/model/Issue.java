package dev.pcshop.model;

import dev.pcshop.enums.Slot;

import java.util.Set;

// Проблема совместимости
public record Issue(Level level, Set<Slot> slots, String text) {

    public enum Level { ERROR, WARNING, MISSING }

    public static Issue error(String text, Slot... slots) {
        return new Issue(Level.ERROR, Set.of(slots), text);
    }

    public static Issue warning(String text, Slot... slots) {
        return new Issue(Level.WARNING, Set.of(slots), text);
    }

    public static Issue missing(String text, Slot slot) {
        return new Issue(Level.MISSING, Set.of(slot), text);
    }

    public boolean isError() {
        return level == Level.ERROR;
    }

    public boolean isWarning() {
        return level == Level.WARNING;
    }

    public boolean isMissing() {
        return level == Level.MISSING;
    }
}
