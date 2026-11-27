package net.foreverably.pluralcraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.foreverably.pluralcraft.ClientPresencePayload;
import net.foreverably.pluralcraft.client.api.pluralbuddy.PluralBuddyApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
final class ClientProxy
{

	private ClientProxy()
	{
	}

	static void initializeActiveAlter()
	{
		PluralBuddyApiClient.Autoproxy effective = effectiveAutoproxy();
		String mode = effective.autoproxyMode().toLowerCase();

		if (mode.contains("alter") || mode.contains("latch"))
		{
			if (effective.autoproxyAlter() != null)
			{
				Client.latchedAlterId = effective.autoproxyAlter();
			}
			return;
		}
		if (mode.contains("off"))
		{
			Client.latchedAlterId = null;
		}
	}

	private static String autoproxyMode()
	{
		if (Client.linkedAutoproxy.isEmpty())
		{
			return "off";
		}
		return effectiveAutoproxy().autoproxyMode().toLowerCase();
	}

	private static PluralBuddyApiClient.Autoproxy effectiveAutoproxy()
	{

		// Probably the worst way to do this but if it works it works.

		for (PluralBuddyApiClient.Autoproxy entry : Client.linkedAutoproxy)
		{
			String mode = entry.autoproxyMode().toLowerCase();
			if (mode.contains("alter") && entry.autoproxyAlter() != null)
			{
				return entry;
			}
		}
		for (PluralBuddyApiClient.Autoproxy entry : Client.linkedAutoproxy)
		{
			String mode = entry.autoproxyMode().toLowerCase();
			if (mode.contains("latch"))
			{
				return entry;
			}
		}
		for (PluralBuddyApiClient.Autoproxy entry : Client.linkedAutoproxy)
		{
			String mode = entry.autoproxyMode().toLowerCase();
			if (("@global".equals(entry.serverId()) && (mode.contains("alter") || mode.contains("latch")))
					|| "@global".equals(entry.serverId()) && mode.contains("off"))
			{
				return entry;
			}
		}
		for (PluralBuddyApiClient.Autoproxy entry : Client.linkedAutoproxy)
		{
			if ("@global".equals(entry.serverId()))
			{
				return entry;
			}
		}
		return Client.linkedAutoproxy.getFirst();
	}

	static PluralBuddyApiClient.Alter activeAlter()
	{
		PluralBuddyApiClient.Autoproxy effective = effectiveAutoproxy();
		String mode = effective.autoproxyMode().toLowerCase();
		if (mode.contains("alter"))
		{
			return effective.autoproxyAlter() == null
					? null
					: findAlter(effective.autoproxyAlter());
		}
		if (mode.contains("latch") || mode.contains("off"))
		{
			return Client.latchedAlterId == null
					? null
					: findAlter(Client.latchedAlterId);
		}
		return null;
	}

	private static PluralBuddyApiClient.Alter findAlter(String alterId)
	{
		for (PluralBuddyApiClient.Alter alter : Client.linkedAlters)
		{
			if (alter.alterId().equals(alterId))
			{
				return alter;
			}
		}
		return null;
	}

	static void showAutoproxy(FabricClientCommandSource source)
	{
		if (Client.linkedAutoproxy.isEmpty())
		{
			source.sendFeedback(
					Component.literal("PluralCraft ")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal("has no auto-proxy entries.")
									.withStyle(ChatFormatting.YELLOW))
			);
			return;
		}

		PluralBuddyApiClient.Autoproxy effective = effectiveAutoproxy();
		source.sendFeedback(
				Component.literal("Resolved auto-proxy: ")
						.withStyle(ChatFormatting.DARK_AQUA)
						.append(Component.literal(effective.autoproxyMode())
								.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
		);

		PluralBuddyApiClient.Alter active = activeAlter();
		Component activeName;
		if (active != null)
		{
			activeName = supportAlterLabel(active, effective, ChatFormatting.GREEN);
		}
		else if (effective.autoproxyAlter() != null)
		{
			activeName = supportAlterLabel(null, effective, ChatFormatting.YELLOW);
		}
		else
		{
			activeName = Component.literal("none")
					.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
		}
		source.sendFeedback(
				Component.literal("Active: ")
						.withStyle(ChatFormatting.GRAY)
						.append(activeName)
		);

		source.sendFeedback(
				Component.literal("Listed Alters (hover a name for details):")
						.withStyle(ChatFormatting.DARK_AQUA)
		);
		if (Client.linkedAlters.isEmpty())
		{
			source.sendFeedback(Component.literal("  No alters were fetched.")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}

		for (PluralBuddyApiClient.Alter alter : Client.linkedAlters)
		{
			boolean isActive = active != null && active.alterId().equals(alter.alterId());
			source.sendFeedback(
					Component.literal("  ")
							.append(supportAlterLabel(
									alter,
									effective,
									isActive ? ChatFormatting.GREEN : ChatFormatting.WHITE
							))
							.append(Component.literal(isActive ? "  [Active]" : "")
									.withStyle(ChatFormatting.GREEN))
							.append(Component.literal("  [Auto-Proxy: " + effective.autoproxyMode() + "]")
									.withStyle(ChatFormatting.AQUA))
			);
		}
	}

	private static Component supportAlterLabel(
			PluralBuddyApiClient.Alter alter,
			PluralBuddyApiClient.Autoproxy entry,
			ChatFormatting color
	)
	{
		String label = alter == null
				? "Unknown alter"
				: alter.displayName();
		MutableComponent component = Component.literal(label)
				.withStyle(color, ChatFormatting.UNDERLINE);
		Component tooltip = alter == null
				? buildMissingAlterTooltip(entry)
				: buildAlterSupportTooltip(alter, entry);
		return component.withStyle(style -> style.withHoverEvent(
				new net.minecraft.network.chat.HoverEvent.ShowText(tooltip)
		));
	}

	private static Component buildMissingAlterTooltip(PluralBuddyApiClient.Autoproxy entry)
	{
		return Component.literal("Alter details unavailable")
				.withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("\nAlter ID: ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(entry.autoproxyAlter())
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\nThis alter was not found in the fetched alters.")
						.withStyle(ChatFormatting.YELLOW))
				.append(Component.literal("\nAuto-proxy: ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(entry.autoproxyMode())
						.withStyle(ChatFormatting.AQUA));
	}

	private static Component buildAlterSupportTooltip(
			PluralBuddyApiClient.Alter alter,
			PluralBuddyApiClient.Autoproxy entry
	)
	{
		MutableComponent tooltip = Component.literal(alter.displayName())
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal("\nAlter ID: ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(alter.alterId())
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\nUsername: ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(alter.username())
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("\nAuto-proxy: ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(entry.autoproxyMode())
						.withStyle(ChatFormatting.AQUA))
				.append(Component.literal("\nProxy tags: ")
						.withStyle(ChatFormatting.GRAY));

		if (alter.proxyTags().isEmpty())
		{
			tooltip = tooltip.append(Component.literal("none")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		else
		{
			for (int index = 0; index < alter.proxyTags().size(); index++)
			{
				PluralBuddyApiClient.ProxyTag tag = alter.proxyTags().get(index);
				if (index > 0)
				{
					tooltip = tooltip.append(Component.literal(", ")
							.withStyle(ChatFormatting.GRAY));
				}
				String prefix = tag.prefix().isBlank() ? "" : tag.prefix();
				String suffix = tag.suffix().isBlank() ? "" : tag.suffix();
				tooltip = tooltip.append(Component.literal(
						prefix + (prefix.isEmpty() || suffix.isEmpty() ? "" : "...") + suffix
				).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		return tooltip;
	}

	static void showAlters(FabricClientCommandSource source)
	{
		if (Client.sessionTokens == null)
		{
			source.sendFeedback(
					Component.literal("PluralCraft ")
							.withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal("is not linked")
									.withStyle(ChatFormatting.YELLOW))
							.append(Component.literal(". Run /pluralcraft link first.")
									.withStyle(ChatFormatting.GRAY))
			);
			return;
		}

		source.sendFeedback(
				Component.literal("PluralCraft alters")
						.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
						.append(Component.literal(" (" + Client.linkedAlters.size() + ")")
								.withStyle(ChatFormatting.GRAY))
		);

		PluralBuddyApiClient.Alter active = activeAlter();
		for (PluralBuddyApiClient.Alter alter : Client.linkedAlters)
		{
			boolean isActive = active != null && active.alterId().equals(alter.alterId());
			MutableComponent tags = Component.literal("none")
					.withStyle(ChatFormatting.DARK_GRAY);
			if (!alter.proxyTags().isEmpty())
			{
				tags = Component.empty();
				for (int index = 0; index < alter.proxyTags().size(); index++)
				{
					PluralBuddyApiClient.ProxyTag tag = alter.proxyTags().get(index);

					String prefix = tag.prefix().isBlank() ? "" : tag.prefix();
					String suffix = tag.suffix().isBlank() ? "" : tag.suffix();

					if (index > 0)
					{
						tags = tags.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
					}
					tags = tags.append(Component.literal(
							prefix + (prefix.isEmpty() || suffix.isEmpty() ? "" : "...") + suffix
					).withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}

			source.sendFeedback(
					Component.literal(isActive ? "[Fronting] " : "[Alter] ")
							.withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
							.append(Component.literal(alter.displayName())
									.withStyle(isActive ? ChatFormatting.AQUA : ChatFormatting.WHITE,
											isActive ? ChatFormatting.BOLD : ChatFormatting.RESET))
							.append(Component.literal("  Tags: ").withStyle(ChatFormatting.GRAY))
							.append(tags)
			);
		}

		if (Client.linkedAlters.isEmpty())
		{
			source.sendFeedback(
					Component.literal("No alters were found.")
							.withStyle(ChatFormatting.YELLOW)
			);
		}
	}

	static String modifyProxyMessage(String message)
	{
		if (Client.sessionTokens == null || Client.linkedAlters.isEmpty())
		{
			return message;
		}

		String mode = autoproxyMode();
		if (mode.contains("alter"))
		{
			PluralBuddyApiClient.Alter active = activeAlter();
			if (active == null)
			{
				Client.sendPresence(null);
				ClientLink.persistLink();
				return message;
			}
			Client.sendPresence(active);
			ClientLink.persistLink();
			return ClientPresencePayload.chatMarker(active.alterId(), active.displayName()) + message;
		}

		for (PluralBuddyApiClient.Alter alter : Client.linkedAlters)
		{
			for (PluralBuddyApiClient.ProxyTag tag : alter.proxyTags())
			{
				if (!tag.prefix().isBlank() && message.startsWith(tag.prefix()))
				{
					String modified = message.substring(tag.prefix().length());

					Client.latchedAlterId = alter.alterId();
					Client.sendPresence(alter);

					ClientLink.persistLink();

					return ClientPresencePayload.chatMarker(alter.alterId(), alter.displayName()) + modified.trim();
				}
				if (!tag.suffix().isBlank() && message.endsWith(tag.suffix()))
				{
					String modified = message.substring(0, message.length() - tag.suffix().length());

					Client.latchedAlterId = alter.alterId();
					Client.sendPresence(alter);

					ClientLink.persistLink();

					return ClientPresencePayload.chatMarker(alter.alterId(), alter.displayName()) + modified.trim();
				}
			}
		}

		if (mode.contains("latch"))
		{
			PluralBuddyApiClient.Alter active = activeAlter();
			if (active != null)
			{
				Client.sendPresence(active);
				ClientLink.persistLink();

				return ClientPresencePayload.chatMarker(active.alterId(), active.displayName()) + message;
			}
		}

		Client.latchedAlterId = null;
		Client.sendPresence(null);
		ClientLink.persistLink();

		return ClientPresencePayload.chatMarker("", "") + message;
	}
}
