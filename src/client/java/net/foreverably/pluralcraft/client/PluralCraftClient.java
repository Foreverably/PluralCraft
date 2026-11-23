package net.foreverably.pluralcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.foreverably.pluralcraft.ClientPresencePayload;

public final class PluralCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ClientPresencePayload.TYPE, (payload, context) -> {
		});
	}
}
