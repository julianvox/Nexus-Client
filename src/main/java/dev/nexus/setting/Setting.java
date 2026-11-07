package dev.nexus.setting;

import com.google.gson.JsonElement;

public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement e);

    public abstract String display();

    public abstract void setFromString(String s);

    public abstract void reset();
}
