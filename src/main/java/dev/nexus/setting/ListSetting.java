package dev.nexus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nexus.util.Names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Onay kutulu çoklu seçim listesi (örn. hangi canlılara saldırılsın). */
public class ListSetting extends Setting {
    public static final class Option {
        public final String id, label, group;
        public boolean on;
        final boolean def;

        Option(String id, String label, String group, boolean on) {
            this.id = id;
            this.label = label;
            this.group = group;
            this.on = on;
            this.def = on;
        }
    }

    public final List<Option> options = new ArrayList<>();
    private final Map<String, Option> byId = new HashMap<>();
    /** Menüde liste açık mı (kalıcı değil). */
    public boolean expanded;

    public ListSetting(String name) {
        super(name);
    }

    public ListSetting add(String id, String label, String group, boolean on) {
        Option o = new Option(id, label, group, on);
        options.add(o);
        byId.put(id, o);
        return this;
    }

    public boolean has(String id) { return byId.containsKey(id); }

    public boolean isOn(String id) {
        Option o = byId.get(id);
        return o != null && o.on;
    }

    public int count() {
        int n = 0;
        for (Option o : options) if (o.on) n++;
        return n;
    }

    public Set<String> groups() {
        Set<String> g = new LinkedHashSet<>();
        for (Option o : options) g.add(o.group);
        return g;
    }

    public List<Option> inGroup(String group) {
        List<Option> l = new ArrayList<>();
        for (Option o : options) if (o.group.equals(group)) l.add(o);
        return l;
    }

    public void setGroup(String group, boolean v) {
        for (Option o : options) if (o.group.equals(group)) o.on = v;
    }

    public void setAll(boolean v) {
        for (Option o : options) o.on = v;
    }

    @Override public void reset() { for (Option o : options) o.on = o.def; }

    @Override public JsonElement save() {
        JsonObject j = new JsonObject();
        for (Option o : options) j.add(o.id, new JsonPrimitive(o.on));
        return j;
    }

    @Override public void load(JsonElement e) {
        try {
            JsonObject j = e.getAsJsonObject();
            for (Option o : options) if (j.has(o.id)) o.on = j.get(o.id).getAsBoolean();
        } catch (Exception ignored) {}
    }

    @Override public String display() { return count() + "/" + options.size(); }

    /** Komut için: "hepsi", "hicbiri" ya da bir canlının adı (aç/kapat). */
    @Override public void setFromString(String s) {
        String k = Names.norm(s);
        if (k.equals("hepsi") || k.equals("all")) { setAll(true); return; }
        if (k.equals("hicbiri") || k.equals("none")) { setAll(false); return; }
        for (Option o : options) {
            if (Names.norm(o.id).equals(k) || Names.norm(o.label).equals(k)) {
                o.on = !o.on;
                return;
            }
        }
    }
}
