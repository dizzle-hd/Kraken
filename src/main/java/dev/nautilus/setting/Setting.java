package dev.nautilus.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** Base class for every configurable value of a module. */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private BooleanSupplier visibility = () -> true;
    private Runnable onChange = () -> {};
    protected T value;
    protected final T defaultValue;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }

    public void set(T value) {
        if (value == null || value.equals(this.value)) return;
        this.value = value;
        onChange.run();
    }

    public void reset() { set(defaultValue); }

    public boolean isVisible() { return visibility.getAsBoolean(); }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
        this.visibility = supplier;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Runnable runnable) {
        this.onChange = runnable;
        return (S) this;
    }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement element);
}
