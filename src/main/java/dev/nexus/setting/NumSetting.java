package dev.nexus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumSetting extends Setting {
    public final double min, max, step;
    private double value;
    private final double def;

    public NumSetting(String name, double min, double max, double step, double def) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = def;
        this.def = def;
    }

    public double def() { return def; }
    public double get() { return value; }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return (float) value; }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        value = Math.max(min, Math.min(max, v));
    }

    public double ratio() { return max == min ? 0 : (value - min) / (max - min); }

    @Override public void reset() { value = def; }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { try { set(e.getAsDouble()); } catch (Exception ignored) {} }
    @Override public String display() {
        if (step >= 1) return String.valueOf((int) Math.round(value));
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    @Override public void setFromString(String s) { try { set(Double.parseDouble(s.replace(',', '.'))); } catch (Exception ignored) {} }
}
