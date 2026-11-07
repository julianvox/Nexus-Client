package dev.nexus.module;

public enum Category {
    COMBAT("Savaş"),
    MOVEMENT("Hareket"),
    RENDER("Görsel"),
    WORLD("Dünya"),
    PLAYER("Oyuncu"),
    MISC("Diğer");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
