package dev.nautilus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    @Override
    public void set(Double v) {
        if (v == null) return;
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) clamped = Math.round(clamped / step) * step;
        // avoid floating point noise like 3.0000000004
        clamped = Math.round(clamped * 1000.0) / 1000.0;
        super.set(clamped);
    }

    /** @param fraction 0..1 position on a slider */
    public void setFraction(double fraction) {
        set(min + (max - min) * Math.max(0, Math.min(1, fraction)));
    }

    public double getFraction() { return (value - min) / (max - min); }

    public String display() {
        if (step >= 1) return String.valueOf(getInt());
        if (step >= 0.1) return String.format(java.util.Locale.ROOT, "%.1f", value);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsDouble());
    }
}
