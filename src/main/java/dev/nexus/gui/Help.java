package dev.nexus.gui;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ListSetting;
import dev.nexus.setting.ModeSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.setting.Setting;

import java.util.HashMap;
import java.util.Map;

/** Menüdeki her yazının üstünde bekleyince çıkan uzun açıklamalar. */
final class Help {
    private Help() {}

    private static final Map<String, String> MODULE = new HashMap<>();
    private static final Map<String, String> SETTING = new HashMap<>();
    private static final Map<String, String> OPTION = new HashMap<>();

    static String category(Category c) {
        return switch (c) {
            case COMBAT -> "Saldırı, savunma ve dövüşle ilgili modüller: otomatik vurma, kalkan kırma, kaçınma, geri tepme...";
            case MOVEMENT -> "Hareketle ilgili modüller: uçuş, hız, zıplama, süzülme, tırmanma...";
            case RENDER -> "Ekranda bir şeyleri gösteren modüller: canlı ve maden göstericiler, çizgiler, aydınlık, bilgi ekranı.";
            case WORLD -> "Bloklarla ilgili modüller: otomatik kırma, köprü kurma, alet seçme.";
            case PLAYER -> "Oyuncunun kendisiyle ilgili modüller: otomatik yemek, zırh, totem, ölümsüzlük...";
            case MISC -> "Diğer yardımcı modüller (ör. Panik: hepsini kapatır).";
        };
    }

    static String profileTab() {
        return "Ayar profillerini kaydet ve yükle, dost listesini yönet, tüm ayarları sıfırla. Dostlara hiçbir saldırı modülü dokunmaz.";
    }

    static String module(Module m) {
        String s = MODULE.get(m.name);
        return s != null ? s : m.description;
    }

    static String keyLine(Module m) {
        String k = m.keyText();
        return "\nTuş: " + (k.isEmpty() ? "atanmamış (ESC > Kontroller > Tuş Atamaları > Nexus)" : k)
                + "\nDurum: " + (m.enabled ? "AÇIK" : "kapalı");
    }

    static String setting(Module m, Setting s) {
        String base = SETTING.get(m.name + "." + s.name);
        if (base == null) base = SETTING.get(s.name);
        if (base == null) base = s.name + " ayarı.";
        String extra;
        if (s instanceof NumSetting n) {
            extra = "\nAralık: " + fmt(n.min) + " – " + fmt(n.max) + "   Varsayılan: " + fmt(n.def()) + "   Şu an: " + n.display();
        } else if (s instanceof BoolSetting b) {
            extra = "\nŞu an: " + (b.get() ? "AÇIK" : "kapalı");
        } else if (s instanceof ModeSetting ms) {
            extra = "\nSeçenekler: " + String.join(", ", ms.modes) + "   Şu an: " + ms.get();
        } else if (s instanceof ListSetting l) {
            extra = "\n" + l.count() + " / " + l.options.size() + " seçili";
        } else {
            extra = "";
        }
        return base + extra;
    }

    static String option(Module m, Setting s, String opt) {
        String t = OPTION.get(m.name + "." + s.name + "." + opt);
        return (t != null ? t : "Bu seçeneği kullanır.") + "\nTıklayınca " + opt + " seçilir.";
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private static void mod(String n, String t) { MODULE.put(n, t); }
    private static void set(String k, String t) { SETTING.put(k, t); }
    private static void opt(String k, String t) { OPTION.put(k, t); }

    static {
        // ------------------------------------------------------------ modüller
        mod("OtoSaldırı", "Menzilindeki hedeflere otomatik vurur. Kimlere vuracağını 'Hedefler' listesinden seçersin; evcil hayvanların, isimli canlıların ve dostların otomatik korunur. 'Kritik: Zıpla' ile zıplayıp düşerken vurarak her vuruşu kritik yapar. OtoKalkanKır açıksa kalkan tutan hedefe önce baltayla vurur. 'Dönüş: Yok' iken ekranın hiç dönmez.");
        mod("OtoKalkanKır", "Hedef kalkan tutuyorsa hotbar'daki baltaya geçip vurarak kalkanı kırar, sonra otomatik olarak kılıcına döner. OtoSaldırı ve OtoTetik açıkken onlarla birlikte çalışır. Tek başına açıksa nişangahındaki oyuncuya uygular. Hotbar'da balta olmalıdır.");
        mod("NişanYardımı", "Sol tıka basarken nişangahını yakındaki hedefe doğru yumuşakça çeker. Hız yükseldikçe daha sert çeker. Kameranı gerçekten hareket ettirir.");
        mod("OtoTetik", "Nişangahın bir canlının üstüne gelince otomatik vurur. 'Hedefler' listesi ve koruma ayarları geçerlidir. Kalkan tutan hedefte OtoKalkanKır varsa onu kullanır.");
        mod("GeriTepme", "Vurulunca savrulmanı azaltır. Yatay% ve Dikey% 0 ise hiç savrulmazsın, 100 ise normal savrulursun.");
        mod("Menzil", "Canlılara vurma ve blok kırma/koyma menzilini uzatır. Sunucular uzun menzili çok kolay fark eder.");
        mod("Kritik", "Nişangahın bir canlıya bakarken sol tık basılıysa sürekli zıplar, böylece vuruşların kritik olur.");
        mod("OtoElma", "Canın ayarladığın değerin altına düşünce hotbar'daki altın elmayı yer, sonra eski eşyana döner.");
        mod("OtoKaçınma", "Hasardan kaçınmaya çalışır: lav, ateş, magma, kaktüs gibi bloklara yürümeni durdurur; sana gelen ok ve ateş toplarından yana kayar; yakındaki canavar ve oyuncuların saldırısından zigzag ile kaçar; yanan creeperdan uzaklaşır. Duvara veya uçuruma kaçmaz.");
        mod("Uçuş", "Uçmanı sağlar. 'Hız' modunda Space ile yükselir, Shift ile alçalırsın. 'Yaratıcı' modunda oyunun kendi uçuşunu açar (çift Space). Sunucularda neredeyse her zaman yasaktır.");
        mod("Hız", "Yürüme hızını arttırır. 'Sıçrama' açıksa yerdeyken sürekli zıplayarak daha da hızlanır.");
        mod("Koşu", "W'ye basınca sürekli koşar, Ctrl'ye basman gerekmez.");
        mod("DüşmeKoruma", "Yüksekten düşerken sunucuya yere basmışsın gibi bildirerek düşme hasarını engellemeye çalışır.");
        mod("Basamak", "Zıplamadan bir bloktan daha yüksek yerlere çıkmanı sağlar.");
        mod("YüksekZıplama", "Zıplama gücünü arttırır, normalden çok daha yükseğe zıplarsın.");
        mod("Süzülme", "Havadayken düşüş hızını sınırlar, yavaşça süzülürsün.");
        mod("SudaYürü", "Suyun ve lavın üzerinde yüzmeni sağlar. Shift'e basarsan batarsın.");
        mod("Örümcek", "Bir duvara doğru ilerleyince duvara tırmanmanı sağlar.");
        mod("HavadaZıpla", "Havadayken Space'e basınca tekrar zıplatır (sonsuz zıplama).");
        mod("OtoYürü", "İleri tuşuna basmadan sürekli ileri yürür.");
        mod("Aydınlık", "Karanlık yerleri aydınlık gösterir (gece görüşü etkisi).");
        mod("VarlıkGöster", "Seçili canlıları duvar arkasından gösterir: parıltı, kutu, isim, can ve mesafe yazısı. Hangi canlıların gösterileceğini 'Hedefler' listesinden seçersin.");
        mod("Çizgiler", "Nişangahtan seçili canlılara renkli çizgi çeker. Hangi canlılara çizileceğini 'Hedefler' listesinden seçersin.");
        mod("Yakınlaştır", "Görüntüyü yakınlaştırır. Görüş açısı ne kadar küçükse o kadar yakınlaşır.");
        mod("BilgiEkranı", "Ekranın köşelerinde bilgi gösterir: logo, açık modül listesi, koordinat, FPS ve hedef bilgisi.");
        mod("MadenGöster", "Çevrendeki seçili madenleri duvar arkasından RGB çerçeve, isim ve mesafe yazısıyla gösterir; nişangahtan madenlere çizgi çeker. Hangi madenlerin gösterileceğini aşağıdaki kutulardan seç.");
        mod("DepoGöster", "Çevrendeki sandık, ender sandığı, fıçı, shulker ve spawner bloklarını duvar arkasından çerçeve, isim ve mesafe yazısıyla gösterir.");
        mod("BlokKırıcı", "Çevrendeki blokları otomatik kırar. Yarıçap ve tik başına blok sayısını ayarlayabilirsin.");
        mod("OtoKöprü", "Ayağının altına otomatik blok koyarak havada köprü kurar. Hotbar'da blok olmalıdır.");
        mod("OtoAlet", "Bir bloğu kazarken hotbar'daki en uygun aleti otomatik seçer.");
        mod("OtoKazı", "Sol tık düğmesini sürekli basılı tutar, bakılan bloğu kazar.");
        mod("OtoYemek", "Açlık seviyen ayarladığın değerin altına düşünce hotbar'daki yemeği yer.");
        mod("OtoZırh", "Envanterindeki daha iyi zırh parçalarını otomatik giyer.");
        mod("OtoTotem", "Envanterindeki ölümsüzlük totemini ikinci ele (offhand) koyar.");
        mod("SandıkSoyucu", "Bir sandık açınca içindeki bütün eşyaları envanterine alır.");
        mod("OtoDoğ", "Ölünce 'yeniden doğ' düğmesine otomatik basar.");
        mod("AFKKoruma", "Belirli aralıklarla dönüp zıplayarak AFK diye atılmanı önler.");
        mod("Ölümsüzlük", "Tek oyunculu dünyada (veya LAN sahibinde) her türlü hasarı engeller. Çok oyunculu sunucularda çalışmaz.");
        mod("Panik", "Tüm modülleri tek seferde kapatır.");

        // ------------------------------------------------------------ ortak ayarlar
        set("Hedefler", "Hangi canlılara uygulanacağını seç: kutuları işaretle. Her gruptaki 'Tümü' ve 'Hiç' düğmeleriyle hepsini birden değiştirebilirsin. Listede olmayan (modla eklenmiş) canlılar 'Diğer' grubundan kontrol edilir.");
        set("EvcilleriKoru", "Açıkken evcilleştirdiğin hayvanlara (kurt, kedi, papağan, at, eşek...) hiç saldırılmaz.");
        set("İsimlileriKoru", "Açıkken isim etiketi (name tag) takılmış canlılara saldırılmaz.");
        set("RGB", "Açıkken renkler gökkuşağı gibi sürekli değişir; kapalıyken tek renk kullanılır.");
        set("Menzil", "Bu modülün etki mesafesi (blok).");
        set("Hız", "Bu modülün hızı.");
        set("Gecikme", "İşlemler arasında beklenecek süre (tik; 20 tik = 1 saniye).");
        set("Kalınlık", "Çizgi ve çerçevelerin kalınlığı (piksel).");
        set("Yazılar", "Adı ve uzaklığı yazar.");
        set("Çizgiler", "Nişangahtan hedeflere çizgi çeker.");

        // ------------------------------------------------------------ savaş
        set("OtoSaldırı.Menzil", "Hedeflerin kaç blok uzaktan saldırıya uğrayacağı. Normal oyunda vuruş menzili yaklaşık 3 bloktur; 4'ün üstü sunucularda kolay fark edilir.");
        set("OtoSaldırı.Bekleme", "Açık: saldırı çubuğu dolunca vurur (tam hasar). Kapalı: CPS ayarındaki hızda vurur.");
        set("OtoSaldırı.CPS", "Saniyede kaç vuruş yapılacağı. Sadece 'Bekleme' kapalıyken kullanılır.");
        set("OtoSaldırı.Kritik", "Zıpla: yerdeyken zıplar ve düşerken vurur, böylece vuruşların kritik olur (hasar %50 artar). Kapalı: kritik denemez.");
        set("OtoSaldırı.OtoSilah", "Elinde kılıç veya balta yoksa hotbar'daki en iyi kılıcı (yoksa baltayı) otomatik seçer.");
        set("OtoSaldırı.DuvarArkası", "Açıkken duvarın arkasındaki hedeflere de vurur. Kapalıyken sadece görebildiğin hedeflere vurur.");
        set("OtoSaldırı.Öncelik", "Birden fazla hedef varsa hangisine vurulacağı: Mesafe (en yakın), Can (en az canı olan), Açı (nişangahına en yakın).");
        set("OtoSaldırı.GörüşAçısı", "Bakış yönünün çevresinde hedef sayılacak açı (derece). 360 her yönü kapsar; küçük değer sadece baktığın yöne vurur.");
        set("OtoSaldırı.Dönüş", "Saldırırken kameranın hedefe dönüp dönmeyeceği. Yok: ekranın sabit kalır. Yumuşak: yavaşça döner. Ani: anında hedefe bakar.");
        set("OtoSaldırı.DönüşHızı", "Yumuşak dönüşte her tikte hedefe ne kadar yaklaşılacağı (yüzde).");
        opt("OtoSaldırı.Kritik.Zıpla", "Yerdeyken zıplar, düşerken vurur: her vuruş kritik olur.");
        opt("OtoSaldırı.Kritik.Kapalı", "Kritik denemez, normal vurur.");
        opt("OtoSaldırı.Dönüş.Yok", "Kamera hiç dönmez, ekranın sabit kalır.");
        opt("OtoSaldırı.Dönüş.Yumuşak", "Kamera hedefe doğru yavaşça döner.");
        opt("OtoSaldırı.Dönüş.Ani", "Kamera anında hedefe bakar.");
        opt("OtoSaldırı.Öncelik.Mesafe", "En yakındaki hedefe vurur.");
        opt("OtoSaldırı.Öncelik.Can", "En az canı olan hedefe vurur.");
        opt("OtoSaldırı.Öncelik.Açı", "Nişangahına en yakın hedefe vurur.");

        set("OtoKalkanKır.Menzil", "Kalkan kırmak için hedefin en fazla kaç blok uzakta olabileceği.");
        set("OtoKalkanKır.SadeceOyuncular", "Açıkken sadece oyuncuların kalkanını kırar.");
        set("OtoKalkanKır.KılıcaDön", "Baltayla vurduktan sonra hotbar'daki en iyi kılıca geri döner. Kapalıysa vurmadan önceki eşyana döner.");
        set("OtoKalkanKır.NişangahHedefi", "OtoSaldırı kapalıyken veya hedefi yokken nişangahındaki oyuncunun kalkanını da kırar.");
        set("OtoKalkanKır.Bekleme", "Bir kalkan kırdıktan sonra tekrar denemeden önce beklenecek süre (tik; 20 tik = 1 saniye).");

        set("NişanYardımı.Hız", "Nişanın hedefe ne kadar sert çekileceği (yüzde). Düşük değer daha doğal görünür.");
        set("NişanYardımı.Menzil", "Nişan yardımının çalışacağı en uzak mesafe (blok).");
        set("NişanYardımı.GörüşAçısı", "Sadece bu açı içindeki hedeflere yardım eder (derece).");
        set("NişanYardımı.SadeceSaldırırken", "Açıkken sadece sol tıka basılıyken çalışır.");
        set("OtoTetik.Kritik", "Açıkken sadece havada düşerken vurur, böylece vuruş kritik olur.");
        set("GeriTepme.Yatay%", "Yatay savrulmanın yüzde kaçının kalacağı. 0 = hiç savrulmazsın.");
        set("GeriTepme.Dikey%", "Dikey (havaya) savrulmanın yüzde kaçının kalacağı.");
        set("Menzil.Varlık", "Canlılara vurma menzili (blok). Normal oyunda 3.");
        set("Menzil.Blok", "Blok kırma ve koyma menzili (blok). Normal oyunda 4.5.");
        set("OtoElma.Can", "Canın bu değerin altına düşünce altın elma yer. 20 = 10 kalp, 10 = 5 kalp.");

        set("OtoKaçınma.LavdanKaç", "Lava doğru yürümeni engeller; lavın içindeysen seni en yakın güvenli yere çıkarır.");
        set("OtoKaçınma.ZararlıBlok", "Ateş, magma bloğu, kaktüs, kamp ateşi gibi zarar veren bloklara yürümeni de engeller.");
        set("OtoKaçınma.Mermiler", "Sana doğru gelen ok, trident, ateş topu gibi mermilerin yolundan yana kayar.");
        set("OtoKaçınma.Canavarlar", "Yakınındaki düşman canavarların saldırısından kaçar.");
        set("OtoKaçınma.Oyuncular", "Yakınındaki oyuncuların saldırısından kaçar.");
        set("OtoKaçınma.CreeperKaç", "Fitili yanan creeperdan tam hızla uzaklaşır.");
        set("OtoKaçınma.Menzil", "Canavar veya oyuncu bu mesafeye yaklaşınca kaçış başlar (blok).");
        set("OtoKaçınma.Hız", "Kaçış sırasındaki hareket hızı. Yüksek değerler sunucuda kolay fark edilir.");
        set("OtoKaçınma.GeriFaktör", "Kaçışın ne kadarının geriye doğru olacağı. 0 = sadece yana kayar, 1 = sadece geri gider.");
        set("OtoKaçınma.UçurumKoruma", "Kaçarken uçuruma veya boşluğa düşecek yöne gitmez.");
        set("OtoKaçınma.Zıpla", "Kaçarken ayrıca zıplar.");

        // ------------------------------------------------------------ hareket
        set("Uçuş.Mod", "Hız: kendi uçuşu (Space yüksel, Shift alçal). Yaratıcı: oyunun yaratıcı uçuşunu açar.");
        set("Uçuş.Hız", "Uçuş hızı.");
        set("Uçuş.AtılmaKoruma", "Sunucunun 'havada uçuyor' diye atmasını zorlaştırmak için ara sıra hafifçe düşer.");
        opt("Uçuş.Mod.Hız", "Kendi uçuş sistemi: Space ile yüksel, Shift ile alçal.");
        opt("Uçuş.Mod.Yaratıcı", "Oyunun kendi yaratıcı uçuşunu açar.");
        set("Hız.Hız", "Yürüme hızı (blok/tik). Normal yürüme yaklaşık 0.1, koşma 0.13.");
        set("Hız.Sıçrama", "Açıkken yerdeyken sürekli zıplar, böylece daha hızlı ilerlersin.");
        set("Basamak.Yükseklik", "Zıplamadan çıkabileceğin en yüksek blok yüksekliği (normal 0.6).");
        set("YüksekZıplama.Güç", "Zıplama gücü (normal 0.42).");
        set("Süzülme.DüşüşHızı", "Düşerken izin verilen en yüksek hız. Küçük değer = daha yavaş düşüş.");

        // ------------------------------------------------------------ görsel
        set("VarlıkGöster.Menzil", "Kaç blok uzaktaki canlıların gösterileceği.");
        set("VarlıkGöster.Parıltı", "Canlıların etrafına oyunun parıltı efektini ekler (duvardan görünür).");
        set("VarlıkGöster.Kutular", "Canlıların etrafına renkli kutu çizer.");
        set("VarlıkGöster.Yazılar", "Canlının adını, canını ve uzaklığını yazar.");
        set("Çizgiler.Menzil", "Kaç blok uzaktaki canlılara çizgi çekileceği.");
        set("Yakınlaştır.GörüşAçısı", "Yakınlaştırma sırasındaki görüş açısı. Küçük sayı = daha fazla yakınlaşma.");
        set("BilgiEkranı.Logo", "Sol üstte Nexus logosu ve aktif profil adını gösterir.");
        set("BilgiEkranı.ModülListesi", "Sağ üstte açık olan modüllerin listesini gösterir.");
        set("BilgiEkranı.Koordinat", "Sol altta bulunduğun konumu (X, Y, Z) gösterir.");
        set("BilgiEkranı.FPS", "Sol altta saniyedeki kare sayısını (FPS) gösterir.");
        set("BilgiEkranı.HedefBilgisi", "OtoSaldırı bir hedefe vururken hedefin adını ve canını gösterir.");
        for (String mn : new String[]{"MadenGöster", "DepoGöster"}) {
            set(mn + ".Menzil", "Çevrende kaç blok yarıçapta tarama yapılacağı. Büyük değer biraz FPS yer.");
            set(mn + ".Stil", "Tel: 3 boyutlu tel kafes çerçeve. Kutu: düz ekran kutusu (daha hafif).");
            set(mn + ".Yazılar", "Her bloğun üstüne adını ve uzaklığını yazar.");
            set(mn + ".Çizgiler", "Nişangahtan bloklara çizgi çeker.");
            set(mn + ".ÇizgiSayısı", "En fazla kaç bloğa çizgi çekileceği (en yakın olanlara).");
            set(mn + ".Kalınlık", "Çizgi ve çerçeve kalınlığı.");
            set(mn + ".MaksBlok", "Aynı anda en fazla kaç bloğun çizileceği. FPS düşüyorsa azalt.");
            opt(mn + ".Stil.Tel", "3 boyutlu tel kafes çerçeve çizer.");
            opt(mn + ".Stil.Kutu", "Düz bir ekran kutusu çizer (daha hafif).");
        }
        set("MadenGöster.Elmas", "Elmas madenlerini gösterir.");
        set("MadenGöster.AntikKalıntı", "Antik kalıntıyı (netherite'ın hammaddesi) gösterir.");
        set("MadenGöster.Zümrüt", "Zümrüt madenlerini gösterir.");
        set("MadenGöster.Altın", "Altın madenlerini gösterir.");
        set("MadenGöster.Demir", "Demir madenlerini gösterir.");
        set("MadenGöster.Kömür", "Kömür madenlerini gösterir.");
        set("MadenGöster.Kızıltaş", "Kızıltaş (redstone) madenlerini gösterir.");
        set("MadenGöster.Lapis", "Lapis lazuli madenlerini gösterir.");
        set("MadenGöster.Bakır", "Bakır madenlerini gösterir.");
        set("MadenGöster.Kuvars", "Nether kuvars madenlerini gösterir.");
        set("DepoGöster.Sandık", "Sandık ve tuzaklı sandıkları gösterir.");
        set("DepoGöster.EnderSandığı", "Ender sandıklarını gösterir.");
        set("DepoGöster.Fıçı", "Fıçıları gösterir.");
        set("DepoGöster.Shulker", "Shulker kutularını gösterir.");
        set("DepoGöster.Spawner", "Canavar kafeslerini (spawner) gösterir.");

        // ------------------------------------------------------------ dünya
        set("BlokKırıcı.Yarıçap", "Çevrendeki kaç blok yarıçapta kırılacağı.");
        set("BlokKırıcı.TikBaşınaBlok", "Her tikte (saniyenin 1/20'si) en fazla kaç blok kırılacağı.");
        set("BlokKırıcı.ZeminiKoru", "Açıkken ayağının altındaki blokları kırmaz.");
        set("OtoKöprü.Gecikme", "Bloklar arasında beklenecek süre (tik).");
        set("OtoKöprü.OtoSeç", "Hotbar'dan blok eşyasını otomatik seçer.");

        // ------------------------------------------------------------ oyuncu
        set("OtoYemek.Açlık", "Açlık çubuğu bu değerin altına inince yemek yer (20 = tam dolu).");
        set("OtoZırh.Gecikme", "Zırh giyme adımları arasında beklenecek süre (tik).");
        set("OtoTotem.Gecikme", "Totemi ikinci ele koymadan önce beklenecek süre (tik).");
        set("SandıkSoyucu.Gecikme", "Her eşya alma işlemi arasında beklenecek süre (tik).");
        set("SandıkSoyucu.OtoKapat", "Sandık boşalınca otomatik kapatır.");
        set("AFKKoruma.Aralık", "Kaç tikte bir dönüp zıplanacağı (20 tik = 1 saniye).");
        set("Ölümsüzlük.CanYenile", "Canını sürekli doldurur.");
        set("Ölümsüzlük.AteşiSöndür", "Üstündeki ateşi söndürür (lavdan çıkınca yanmazsın).");
        set("Ölümsüzlük.BoşlukKoruma", "Boşluğa düşersen son durduğun güvenli yere ışınlar.");
    }
}
