package net.foreverably.pluralcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluralCraft implements ModInitializer {
	public static final String MOD_ID = "pluralcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(ClientPresencePayload.TYPE, ClientPresencePayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> {
			if (!ServerPlayNetworking.canSend(connection, ClientPresencePayload.TYPE)) {
				connection.disconnect(Component.literal(
						"PluralCraft is required by this server. Install the PluralCraft client mod to join."
				).withStyle(ChatFormatting.BLUE));
			}
		});

		LOGGER.info("PluralCraft initialized!");
	}
}
