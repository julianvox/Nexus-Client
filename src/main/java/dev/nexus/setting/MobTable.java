package dev.nexus.setting;

/** Canlı listesi: kimlik (Minecraft adı) -> Türkçe ad. */
public final class MobTable {
    private MobTable() {}

    private static final String[][] HOSTILE = {
            {"zombie", "Zombi"}, {"husk", "Husk"}, {"drowned", "Boğulmuş"}, {"zombie_villager", "Zombi Köylü"},
            {"skeleton", "İskelet"}, {"stray", "Stray"}, {"bogged", "Bogged"}, {"wither_skeleton", "Wither İskeleti"},
            {"creeper", "Creeper"}, {"spider", "Örümcek"}, {"cave_spider", "Mağara Örümceği"}, {"witch", "Cadı"},
            {"slime", "Balçık"}, {"magma_cube", "Magma Küpü"}, {"ghast", "Ghast"}, {"blaze", "Blaze"},
            {"phantom", "Hayalet"}, {"pillager", "Yağmacı"}, {"vindicator", "Vindicator"}, {"evoker", "Evoker"},
            {"ravager", "Ravager"}, {"vex", "Vex"}, {"guardian", "Gardiyan"}, {"elder_guardian", "Büyük Gardiyan"},
            {"silverfish", "Gümüşbalığı"}, {"endermite", "Endermite"}, {"shulker", "Shulker"}, {"hoglin", "Hoglin"},
            {"zoglin", "Zoglin"}, {"piglin_brute", "Piglin Vahşisi"}, {"warden", "Warden"}, {"breeze", "Breeze"},
            {"creaking", "Creaking"}};

    private static final String[][] NEUTRAL = {
            {"enderman", "Enderman"}, {"zombified_piglin", "Zombileşmiş Piglin"}, {"piglin", "Piglin"},
            {"wolf", "Kurt"}, {"iron_golem", "Demir Golem"}, {"snow_golem", "Kar Golemi"}, {"bee", "Arı"},
            {"polar_bear", "Kutup Ayısı"}, {"llama", "Lama"}, {"trader_llama", "Tüccar Laması"}, {"panda", "Panda"},
            {"dolphin", "Yunus"}, {"goat", "Keçi"}, {"villager", "Köylü"}, {"wandering_trader", "Gezgin Tüccar"}};

    private static final String[][] PASSIVE = {
            {"cow", "İnek"}, {"mooshroom", "Mooshroom"}, {"pig", "Domuz"}, {"sheep", "Koyun"}, {"chicken", "Tavuk"},
            {"horse", "At"}, {"donkey", "Eşek"}, {"mule", "Katır"}, {"cat", "Kedi"}, {"ocelot", "Oselot"},
            {"parrot", "Papağan"}, {"rabbit", "Tavşan"}, {"fox", "Tilki"}, {"turtle", "Kaplumbağa"},
            {"squid", "Mürekkepbalığı"}, {"glow_squid", "Parlayan Mürekkepbalığı"}, {"cod", "Morina"},
            {"salmon", "Somon"}, {"pufferfish", "Kirpibalığı"}, {"tropical_fish", "Tropikal Balık"},
            {"axolotl", "Aksolotl"}, {"frog", "Kurbağa"}, {"bat", "Yarasa"}, {"camel", "Deve"},
            {"sniffer", "Koklayıcı"}, {"armadillo", "Armadillo"}, {"strider", "Strider"}, {"allay", "Allay"}};

    /** Hangi grupların varsayılan olarak seçili olacağını verir. */
    public static ListSetting create(String name, boolean player, boolean hostile, boolean neutral, boolean passive) {
        ListSetting l = new ListSetting(name);
        l.add("player", "Oyuncular", "Oyuncu", player);
        for (String[] m : HOSTILE) l.add(m[0], m[1], "Düşman", hostile);
        for (String[] m : NEUTRAL) l.add(m[0], m[1], "Nötr", neutral);
        for (String[] m : PASSIVE) l.add(m[0], m[1], "Hayvan", passive);
        l.add("_diger_dusman", "Listede olmayan düşmanlar", "Diğer", hostile);
        l.add("_diger_canli", "Listede olmayan canlılar", "Diğer", passive);
        return l;
    }
}
