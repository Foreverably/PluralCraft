package net.foreverably.pluralcraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient;
import net.foreverably.pluralcraft.client.api.PluralCraftOAuthClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

@Environment(EnvType.CLIENT)
final class PluralCraftClientCommands
{

	private PluralCraftClientCommands()
	{
	}

	static void register()
	{
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(ClientCommands.literal("pluralcraft")
						.then(ClientCommands.literal("link").executes(context -> {
							PluralCraftClientLink.startLinking(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("status").executes(context -> {
							sendStatus(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("alters").executes(context -> {
							PluralCraftClientProxy.showAlters(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("ap").executes(context -> {
							PluralCraftClientProxy.showAutoproxy(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("unlink").executes(context -> {
							unlink(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("refresh").executes(context -> {
							PluralCraftClientLink.refreshLink(context.getSource());
							return 1;
						}))
				)
		);
	}

	private static void sendStatus(FabricClientCommandSource source)
	{
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
		PluralBuddyApiClient.Alter active = PluralCraftClientProxy.activeAlter();
		if (tokens == null)
		{
			source.sendFeedback(
					Component.literal("PluralCraft ")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal("is not linked")
									.withStyle(ChatFormatting.YELLOW))
							.append(Component.literal(" to PluralBuddy.")
									.withStyle(ChatFormatting.GRAY))
			);
		}
		else
		{
			source.sendFeedback(
					active == null
							? Component.literal("PluralCraft ")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal("is linked")
									.withStyle(ChatFormatting.GREEN))
							.append(Component.literal(" - no alter is active.")
									.withStyle(ChatFormatting.GRAY))
							: Component.literal("PluralCraft ")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal("is fronting as ")
									.withStyle(ChatFormatting.GRAY))
							.append(Component.literal(active.displayName())
									.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
							.append(Component.literal(".")
									.withStyle(ChatFormatting.GRAY))
			);
		}
	}

	private static void unlink(FabricClientCommandSource source)
	{
		PluralCraftClient.sessionTokens = null;
		PluralCraftClient.linkedAlters = List.of();
		PluralCraftClient.linkedAutoproxy = List.of();
		PluralCraftClient.latchedAlterId = null;
		PluralCraftClient.accessExpiresAtMillis = 0;
		boolean storageCleared = PluralCraftClientLink.deletePersistedLink();
		PluralCraftClient.sendPresence(null);
		Component message = Component.literal("PluralCraft ")
				.withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal("has been unlinked")
						.withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" from PluralBuddy.")
						.withStyle(ChatFormatting.GRAY));
		if (!storageCleared)
		{
			message = message.copy().append(Component.literal(
					" The local credential or link snapshot could not be fully removed please check the game log."
			).withStyle(ChatFormatting.RED));
		}
		source.sendFeedback(message);
	}
}
