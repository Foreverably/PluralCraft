package net.foreverably.pluralcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PluralCraft implements ModInitializer
{

	public static final String MOD_ID = "pluralcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Map<UUID, ClientPresencePayload> FRONTING = new ConcurrentHashMap<>();

	@Override
	public void onInitialize()
	{
		PayloadTypeRegistry.clientboundPlay().register(ClientPresencePayload.TYPE, ClientPresencePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientPresencePayload.TYPE, ClientPresencePayload.CODEC);

		ServerMessageDecoratorEvent.EVENT.register(
				ServerMessageDecoratorEvent.CONTENT_PHASE,
				(sender, message) -> {
					if (sender == null)
					{
						return message;
					}

					String text = message.getString();
					if (text.startsWith(ClientPresencePayload.CHAT_MARKER_PREFIX))
					{
						int markerEnd = text.indexOf(ClientPresencePayload.CHAT_MARKER_SUFFIX);
						if (markerEnd >= 0)
						{
							String marker = text.substring(
									ClientPresencePayload.CHAT_MARKER_PREFIX.length(),
									markerEnd
							);
							String remaining = text.substring(
									markerEnd + ClientPresencePayload.CHAT_MARKER_SUFFIX.length()
							);

							if ("none".equals(marker))
							{
								return Component.literal(remaining).withStyle(ChatFormatting.WHITE);
							}

							int separator = marker.indexOf(':');
							if (separator > 0)
							{
								String markerAlterId = marker.substring(0, separator);
								if (markerAlterId.isBlank())
								{
									return Component.literal(remaining).withStyle(ChatFormatting.WHITE);
								}
								String alterNameEncoded = marker.substring(separator + 1);
								try
								{
									String alterName = new String(
											java.util.Base64.getUrlDecoder().decode(alterNameEncoded),
											java.nio.charset.StandardCharsets.UTF_8
									);
									return Component.literal("[")
											.withStyle(ChatFormatting.DARK_AQUA)
											.append(Component.literal(alterName)
													.withStyle(ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD))
											.append(Component.literal("] ")
													.withStyle(ChatFormatting.DARK_AQUA))
											.append(Component.literal(remaining).withStyle(ChatFormatting.WHITE));
								}
								catch (IllegalArgumentException exception)
								{
									return Component.literal(remaining).withStyle(ChatFormatting.WHITE);
								}
							}

							ClientPresencePayload presence = FRONTING.get(sender.getUUID());
							if (presence != null && marker.equals(presence.alterId()))
							{
								return Component.literal("[")
										.withStyle(ChatFormatting.DARK_AQUA)
										.append(Component.literal(presence.alterName())
												.withStyle(ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD))
										.append(Component.literal("] ")
												.withStyle(ChatFormatting.DARK_AQUA))
										.append(Component.literal(remaining).withStyle(ChatFormatting.WHITE));
							}
							return Component.literal(remaining).withStyle(ChatFormatting.WHITE);
						}
					}

					ClientPresencePayload presence = FRONTING.get(sender.getUUID());
					if (presence == null
							|| presence.alterName().isBlank()
							|| presence.playerUuid().isBlank())
					{
						return message;
					}

					return Component.literal("[")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal(presence.alterName())
									.withStyle(ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD))
							.append(Component.literal("] ")
									.withStyle(ChatFormatting.DARK_AQUA))
							.append(Component.literal(message.getString()).withStyle(ChatFormatting.WHITE));
				}
		);

		ServerPlayNetworking.registerGlobalReceiver(ClientPresencePayload.TYPE, (payload, context) -> {
			UUID playerUuid = context.player().getUUID();
			if (payload.alterId().isBlank())
			{
				FRONTING.remove(playerUuid);
			}
			else
			{
				FRONTING.put(playerUuid, payload);
			}

			for (ServerPlayer player : context.server().getPlayerList().getPlayers())
			{
				if (ServerPlayNetworking.canSend(player, ClientPresencePayload.TYPE))
				{
					ServerPlayNetworking.send(player, payload);
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> {
			if (!ServerPlayNetworking.canSend(connection, ClientPresencePayload.TYPE))
			{
				connection.disconnect(Component.literal(
						"PluralCraft is required by this server. Install the PluralCraft client mod to join. You can find PluralCraft at https://pluralcraft.forwynn.net/"
				).withStyle(ChatFormatting.BLUE));
				return;
			}

			for (ClientPresencePayload payload : FRONTING.values())
			{
				sender.sendPacket(payload);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> {
			FRONTING.remove(connection.getPlayer().getUUID());
		});

		LOGGER.info("PluralCraft is initialized!");
	}
}
