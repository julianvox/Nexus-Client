package dev.nexus;

import dev.nexus.command.Commands;
import dev.nexus.config.ConfigManager;
import dev.nexus.gui.ClickGui;
import dev.nexus.hud.Hud;
import dev.nexus.module.ModuleManager;
import dev.nexus.util.Helper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexusClient implements ClientModInitializer {
    public static final String MOD_ID = "nexus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static KeyMapping OPEN_KEY;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        // ESC > Seçenekler > Kontroller > Tuş Atamaları altında "Nexus" kategorileri
        KeyMapping.Category general = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nexus.open_gui", 229, general)); // Sağ Shift
        ModuleManager.registerKeys();

        ConfigManager.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.consumeClick()) {
                if (client.player != null && Helper.screen() == null) Helper.openScreen(new ClickGui());
            }
            ModuleManager.tick();
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> !Commands.handle(msg));

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                Hud::render);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());

        LOGGER.info("Nexus yüklendi: {} modül", ModuleManager.MODULES.size());
    }
}
