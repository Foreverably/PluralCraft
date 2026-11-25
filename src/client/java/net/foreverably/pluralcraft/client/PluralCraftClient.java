package net.foreverably.pluralcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.foreverably.pluralcraft.ClientPresencePayload;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient;
import net.foreverably.pluralcraft.client.api.PluralCraftOAuthClient;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PluralCraftClient implements ClientModInitializer
{

	static final Map<String, ClientPresencePayload> FRONTING = new ConcurrentHashMap<>();

	static volatile PluralCraftOAuthClient.Tokens sessionTokens;
	static volatile List<PluralBuddyApiClient.Alter> linkedAlters = List.of();
	static volatile List<PluralBuddyApiClient.Autoproxy> linkedAutoproxy = List.of();
	static volatile String latchedAlterId;

	static volatile long accessExpiresAtMillis;

	public static PluralCraftOAuthClient.Tokens getSessionTokens()
	{
		return PluralCraftClientLink.getSessionTokens();
	}

	static void sendPresence(PluralBuddyApiClient.Alter alter)
	{
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !ClientPlayNetworking.canSend(ClientPresencePayload.TYPE))
		{
			return;
		}

		String alterId = alter == null ? "" : alter.alterId();
		String alterName = alter == null ? "" : alter.displayName();
		ClientPlayNetworking.send(new ClientPresencePayload(
				minecraft.player.getUUID().toString(),
				alterId,
				alterName
		));
	}

	@Override
	public void onInitializeClient()
	{
		PluralCraftClientLink.loadPersistedLink();

		ClientPlayNetworking.registerGlobalReceiver(ClientPresencePayload.TYPE, (payload, context) -> {
			if (payload.alterId().isBlank())
			{
				FRONTING.remove(payload.playerUuid());
			}
			else
			{
				FRONTING.put(payload.playerUuid(), payload);
			}
		});

		ClientSendMessageEvents.MODIFY_CHAT.register(PluralCraftClientProxy::modifyProxyMessage);
		PluralCraftClientCommands.register();
	}
}
