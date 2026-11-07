package dev.nexus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting {
    private boolean value;
    private final boolean def;

    public BoolSetting(String name, boolean def) {
        super(name);
        this.value = def;
        this.def = def;
    }

    public boolean get() { return value; }
    public void set(boolean v) { value = v; }
    public void toggle() { value = !value; }

    @Override public void reset() { value = def; }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { try { value = e.getAsBoolean(); } catch (Exception ignored) {} }
    @Override public String display() { return value ? "ON" : "OFF"; }
    @Override public void setFromString(String s) { value = s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equals("1"); }
}
