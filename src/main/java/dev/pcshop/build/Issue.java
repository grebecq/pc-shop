package dev.pcshop.build;

import java.util.Set;

/**
 * Замечание к сборке.
 * ERROR — так собрать нельзя, WARNING — соберётся, но есть нюанс, MISSING — не хватает детали.
 * slots — какие детали затронуты: по ним понимаем, почему конкретный товар не подходит.
 */
public record Issue(Level level, Set<Slot> slots, String text) {

    public enum Level { ERROR, WARNING, MISSING }

    static Issue error(String text, Slot... slots) {
        return new Issue(Level.ERROR, Set.of(slots), text);
    }

    static Issue warning(String text, Slot... slots) {
        return new Issue(Level.WARNING, Set.of(slots), text);
    }

    static Issue missing(String text, Slot slot) {
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
