package dev.nexus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting extends Setting {
    public final String[] modes;
    private int index;
    private int defIndex;

    public ModeSetting(String name, String def, String... modes) {
        super(name);
        this.modes = modes;
        for (int i = 0; i < modes.length; i++) if (modes[i].equalsIgnoreCase(def)) { index = i; defIndex = i; }
    }

    public String get() { return modes[index]; }
    public boolean is(String m) { return modes[index].equalsIgnoreCase(m); }
    public void next() { index = (index + 1) % modes.length; }
    public void prev() { index = (index - 1 + modes.length) % modes.length; }

    @Override public void reset() { index = defIndex; }
    @Override public JsonElement save() { return new JsonPrimitive(modes[index]); }
    @Override public void load(JsonElement e) { try { setFromString(e.getAsString()); } catch (Exception ignored) {} }
    @Override public String display() { return modes[index]; }
    @Override public void setFromString(String s) {
        for (int i = 0; i < modes.length; i++) if (modes[i].equalsIgnoreCase(s)) { index = i; return; }
    }
}
