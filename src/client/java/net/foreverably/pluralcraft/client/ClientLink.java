package net.foreverably.pluralcraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient;
import net.foreverably.pluralcraft.client.api.PluralCraftOAuthClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
final class ClientLink
{

	private static final Logger LOGGER = LoggerFactory.getLogger("pluralcraft-client");
	private static final PluralCraftOAuthClient OAUTH_CLIENT = new PluralCraftOAuthClient();
	private static final PluralBuddyApiClient API_CLIENT = new PluralBuddyApiClient();
	private static final AtomicBoolean LINK_IN_PROGRESS = new AtomicBoolean();

	private ClientLink()
	{
	}

	static void startLinking(FabricClientCommandSource source)
	{
		startLinking(source, true);
	}

	static void startOAuthLinking(FabricClientCommandSource source)
	{
		startLinking(source, false);
	}

	private static void startLinking(FabricClientCommandSource source, boolean useDeviceFlow)
	{
		if (!LINK_IN_PROGRESS.compareAndSet(false, true))
		{
			source.sendFeedback(Component.literal("PluralCraft account linking is already in progress."));
			return;
		}

		Client.sessionTokens = null;
		ClientLinkPersistence.resetCredentialStoreUnavailable();
		source.sendFeedback(
				Component.literal("PluralCraft ")
						.withStyle(ChatFormatting.DARK_AQUA)
						.append(Component.literal("is starting authorization")
								.withStyle(ChatFormatting.YELLOW))
						.append(Component.literal(" to PluralBuddy...")
								.withStyle(ChatFormatting.GRAY))
		);
		CompletableFuture<PluralCraftOAuthClient.Tokens> authorization = useDeviceFlow
				? OAUTH_CLIENT.startDeviceFlow().thenCompose(session -> {
			Minecraft.getInstance().execute(() -> showDeviceCode(session, source));
			return OAUTH_CLIENT.pollDeviceFlow(session);
		})
				: OAUTH_CLIENT.start().thenCompose(session -> {
			openAuthorizationPage(session.authorizationUri(), source);
			return OAUTH_CLIENT.poll(session);
		});
		authorization
				.thenCompose(tokens -> API_CLIENT.getSystem(tokens.accessToken())
						.thenCombine(
								API_CLIENT.getAlters(tokens.accessToken()),
								(system, alters) -> new LinkResult(
										tokens,
										system.systemAutoproxy(),
										alters,
										alters.size(),
										system.systemAutoproxy().size()
								)
						))
				.thenApply(result -> {
					Client.sessionTokens = result.tokens();
					Client.accessExpiresAtMillis = System.currentTimeMillis()
							+ TimeUnit.SECONDS.toMillis(result.tokens().expiresIn());
					Client.linkedAutoproxy = result.autoproxy();
					Client.linkedAlters = result.alters();
					ClientProxy.initializeActiveAlter();
					Client.sendPresence(ClientProxy.activeAlter());
					persistLink();
					scheduleRefresh();
					return result;
				})
				.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
					LINK_IN_PROGRESS.set(false);
					if (error != null)
					{
						PluralCraftOAuthClient.OAuthException oauthError =
								PluralCraftOAuthClient.OAuthException.unwrap(error);
						LOGGER.warn("PluralBuddy linking failed: {}", oauthError.code(), oauthError);
						String errorDetail = oauthError.description() == null
								? oauthError.code()
								: oauthError.code() + ": " + oauthError.description();
						source.sendError(
								Component.literal("PluralCraft ")
										.withStyle(ChatFormatting.DARK_AQUA)
										.append(Component.literal("connection failed")
												.withStyle(ChatFormatting.RED))
										.append(Component.literal(": " + errorDetail)
												.withStyle(ChatFormatting.GRAY))
						);
						return;
					}

					source.sendFeedback(
							Component.literal("PluralCraft ")
									.withStyle(ChatFormatting.DARK_AQUA)
									.append(Component.literal("linked")
											.withStyle(ChatFormatting.GREEN))
									.append(Component.literal(" to PluralBuddy. ")
											.withStyle(ChatFormatting.GRAY))
									.append(Component.literal(String.valueOf(result.alterCount()))
											.withStyle(ChatFormatting.AQUA))
									.append(Component.literal(" alters, ")
											.withStyle(ChatFormatting.GRAY))
									.append(Component.literal(String.valueOf(result.autoproxyCount()))
											.withStyle(ChatFormatting.AQUA))
									.append(Component.literal(" auto-proxy settings.")
											.withStyle(ChatFormatting.GRAY))
					);
					if (!hasPersistedRefreshToken())
					{
						source.sendError(Component.literal(
								"The link is active for this session, but secure token storage or snapshot "
										+ "persistence failed. It may not be restored after a restart."
						));
					}
				}));
	}

	private static void openAuthorizationPage(
			URI authorizationUri,
			FabricClientCommandSource source
	)
	{
		try
		{
			if (Desktop.isDesktopSupported()
					&& Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			{
				Desktop.getDesktop().browse(authorizationUri);
			}
		}
		catch (IOException | UnsupportedOperationException | SecurityException exception)
		{
			LOGGER.info("Could not open the PluralBuddy authorization page automatically.");
		}

	}

	private static void showDeviceCode(
			PluralCraftOAuthClient.DeviceSession session,
			FabricClientCommandSource source
	)
	{
		source.sendFeedback(
				Component.literal("PluralBuddy device code: ")
						.withStyle(ChatFormatting.GRAY)
						.append(Component.literal(session.userCode())
								.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
		);
		source.sendFeedback(
				Component.literal("Approve it here: ")
						.withStyle(ChatFormatting.GRAY)
						.append(Component.literal(session.verificationUri().toString())
								.withStyle(style -> style
										.withColor(ChatFormatting.AQUA)
										.withUnderlined(true)
										.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
												session.verificationUri()
										))
										.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
												Component.literal("Open PluralBuddy device authorization")
										))
								))
		);
		openAuthorizationPage(session.verificationUri(), source);
	}

	static void refreshLink(FabricClientCommandSource source)
	{
		PluralCraftOAuthClient.Tokens tokens = Client.sessionTokens;
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
			return;
		}

		ensureCurrentTokens().thenCompose(currentTokens ->
						API_CLIENT.getSystem(currentTokens.accessToken())
								.thenCombine(
										API_CLIENT.getAlters(currentTokens.accessToken()),
										(system, alters) -> new LinkResult(
												currentTokens,
												system.systemAutoproxy(),
												alters,
												alters.size(),
												system.systemAutoproxy().size()
										)
								))
				.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
					if (error != null)
					{
						source.sendError(Component.literal("Could not refresh PluralBuddy data."));
						return;
					}

					Client.linkedAutoproxy = result.autoproxy();
					Client.linkedAlters = result.alters();
					ClientProxy.initializeActiveAlter();
					PluralBuddyApiClient.Alter active = ClientProxy.activeAlter();
					Client.sendPresence(active);
					persistLink();
					source.sendFeedback(Component.literal(
							active == null
									? "PluralCraft refreshed! No alter is currently active."
									: "PluralCraft refreshed! Fronting as " + active.displayName() + "."
					));
				}));
	}

	private static void scheduleRefresh()
	{
		CompletableFuture.runAsync(
				() -> {
				},
				CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS)
		).thenRun(ClientLink::refreshWithoutFeedback);
	}

	private static CompletableFuture<PluralCraftOAuthClient.Tokens> ensureCurrentTokens()
	{
		PluralCraftOAuthClient.Tokens tokens = Client.sessionTokens;
		if (tokens == null)
		{
			return CompletableFuture.failedFuture(new IllegalStateException("Not linked."));
		}

		if (Client.accessExpiresAtMillis > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1))
		{
			return CompletableFuture.completedFuture(tokens);
		}
		if (tokens.refreshToken() == null || tokens.refreshToken().isBlank())
		{
			return CompletableFuture.completedFuture(tokens);
		}

		return OAUTH_CLIENT.refresh(tokens.refreshToken()).thenApply(refreshed -> {
			PluralCraftOAuthClient.Tokens currentTokens = retainRefreshToken(refreshed, tokens.refreshToken());
			Client.sessionTokens = currentTokens;
			Client.accessExpiresAtMillis = System.currentTimeMillis()
					+ TimeUnit.SECONDS.toMillis(currentTokens.expiresIn());
			persistLink();
			return currentTokens;
		});
	}

	private static void refreshWithoutFeedback()
	{
		if (Client.sessionTokens == null)
		{
			return;
		}

		ensureCurrentTokens().thenCompose(currentTokens ->
						API_CLIENT.getSystem(currentTokens.accessToken())
								.thenCombine(API_CLIENT.getAlters(currentTokens.accessToken()), (system, alters) ->
										new LinkResult(
												currentTokens,
												system.systemAutoproxy(),
												alters,
												alters.size(),
												system.systemAutoproxy().size()
										)
								))
				.whenComplete((result, error) -> {
					if (error == null)
					{
						Client.linkedAutoproxy = result.autoproxy();
						Client.linkedAlters = result.alters();
						ClientProxy.initializeActiveAlter();
						Client.sendPresence(ClientProxy.activeAlter());
						persistLink();
					}
					scheduleRefresh();
				});
	}

	static synchronized void persistLink()
	{
		ClientLinkPersistence.persist();
	}

	private static PluralCraftOAuthClient.Tokens retainRefreshToken(
			PluralCraftOAuthClient.Tokens refreshed,
			String previousRefreshToken
	)
	{
		return refreshed.refreshToken() == null || refreshed.refreshToken().isBlank()
				? new PluralCraftOAuthClient.Tokens(
				refreshed.accessToken(),
				refreshed.expiresIn(),
				previousRefreshToken
		)
				: refreshed;
	}

	static synchronized boolean hasPersistedRefreshToken()
	{
		return ClientLinkPersistence.hasPersistedRefreshToken();
	}

	static synchronized boolean deletePersistedLink()
	{
		return ClientLinkPersistence.deletePersistedLink();
	}

	static synchronized void loadPersistedLink()
	{
		ClientLinkPersistence.PersistedLink persistedLink =
				ClientLinkPersistence.load();

		if (persistedLink == null)
		{
			return;
		}

		String accessToken = persistedLink.accessToken();
		if (accessToken != null
				&& !accessToken.isBlank()
				&& persistedLink.accessExpiresAtMillis() > System.currentTimeMillis())
		{
			Client.sessionTokens = new PluralCraftOAuthClient.Tokens(
					accessToken,
					(int) Math.max(1,
							(persistedLink.accessExpiresAtMillis() - System.currentTimeMillis()) / 1000),
					persistedLink.refreshToken()
			);
			persistLink();
			restorePersistedLink();
			return;
		}

		if (persistedLink.refreshToken() != null)
		{
			refreshAndRestore(persistedLink.refreshToken());
		}
	}

	private static void refreshAndRestore(String refreshToken)
	{
		OAUTH_CLIENT.refresh(refreshToken).whenComplete((tokens, error) -> {
			if (error != null)
			{
				LOGGER.warn("Could not refresh the persisted PluralCraft link.");
				Client.sessionTokens = null;
				return;
			}

			PluralCraftOAuthClient.Tokens currentTokens = retainRefreshToken(tokens, refreshToken);
			Client.sessionTokens = currentTokens;
			Client.accessExpiresAtMillis = System.currentTimeMillis()
					+ TimeUnit.SECONDS.toMillis(currentTokens.expiresIn());
			persistLink();
			restorePersistedLink();
		});
	}

	private static void restorePersistedLink()
	{
		ClientProxy.initializeActiveAlter();
		Client.sendPresence(ClientProxy.activeAlter());
		scheduleRefresh();

		PluralCraftOAuthClient.Tokens tokens = Client.sessionTokens;
		if (tokens != null)
		{
			API_CLIENT.getSystem(tokens.accessToken())
					.thenCombine(API_CLIENT.getAlters(tokens.accessToken()), (system, alters) ->
							new LinkResult(
									tokens,
									system.systemAutoproxy(),
									alters,
									alters.size(),
									system.systemAutoproxy().size()
							)
					)
					.whenComplete((result, error) -> {
						if (error == null)
						{
							Client.linkedAutoproxy = result.autoproxy();
							Client.linkedAlters = result.alters();
							ClientProxy.initializeActiveAlter();
							Client.sendPresence(ClientProxy.activeAlter());
							persistLink();
						}
					});
		}
	}

	private record LinkResult(
			PluralCraftOAuthClient.Tokens tokens,
			List<PluralBuddyApiClient.Autoproxy> autoproxy,
			List<PluralBuddyApiClient.Alter> alters,
			int alterCount,
			int autoproxyCount
	)
	{

	}
}
