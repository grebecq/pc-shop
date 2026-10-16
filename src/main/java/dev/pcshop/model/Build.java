package dev.pcshop.model;

import dev.pcshop.entity.Product;
import dev.pcshop.enums.Slot;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Сборка ПК
public final class Build {

    private final Map<Slot, Product> parts;

    public Build(Map<Slot, Product> parts) {
        this.parts = parts.isEmpty() ? new EnumMap<>(Slot.class) : new EnumMap<>(parts);
    }

    public static Build empty() {
        return new Build(Map.of());
    }

    public Optional<Product> get(Slot slot) {
        return Optional.ofNullable(parts.get(slot));
    }

    public Product part(Slot slot) {
        return parts.get(slot);
    }

    public boolean has(Slot slot) {
        return parts.containsKey(slot);
    }

    public Build with(Slot slot, Product product) {
        var copy = new EnumMap<>(parts);
        copy.put(slot, product);
        return new Build(copy);
    }

    public Build without(Slot slot) {
        var copy = new EnumMap<>(parts);
        copy.remove(slot);
        return new Build(copy);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public Map<Slot, Product> parts() {
        return Collections.unmodifiableMap(parts);
    }

    public List<Product> products() {
        return List.copyOf(parts.values());
    }

    public long total() {
        return parts.values().stream().mapToLong(Product::getPrice).sum();
    }

    public Map<String, Long> ids() {
        Map<String, Long> ids = new LinkedHashMap<>();
        parts.forEach((slot, p) -> ids.put(slot.key(), p.getId()));
        return ids;
    }

    public String query() {
        return parts.entrySet().stream()
                .map(e -> e.getKey().key() + "=" + e.getValue().getId())
                .collect(Collectors.joining("&"));
    }

    public String link() {
        return isEmpty() ? "/configurator" : "/configurator?" + query();
    }

    public String linkWithout(Slot slot) {
        return without(slot).link();
    }

    public String pickLink(Slot slot) {
        return "/configurator/" + slot.key() + (isEmpty() ? "" : "?" + query());
    }

    public String linkWith(Slot slot, Product product) {
        return with(slot, product).link();
    }

    public int size() {
        return parts.size();
    }

    public Optional<Slot> nextEmptyAfter(Slot slot) {
        return Arrays.stream(Slot.values())
                .filter(s -> s.ordinal() > slot.ordinal() && !has(s))
                .findFirst();
    }

    public Optional<Slot> firstEmpty() {
        return Arrays.stream(Slot.values()).filter(s -> !has(s)).findFirst();
    }

    public String nextLink(Slot after) {
        return nextEmptyAfter(after).map(this::pickLink).orElseGet(this::link);
    }
}
