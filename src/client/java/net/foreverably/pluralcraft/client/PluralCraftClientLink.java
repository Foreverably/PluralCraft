package net.foreverably.pluralcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient;
import net.foreverably.pluralcraft.client.api.PluralCraftOAuthClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
final class PluralCraftClientLink
{

	private static final Logger LOGGER = LoggerFactory.getLogger("pluralcraft-client");
	private static final PluralCraftOAuthClient OAUTH_CLIENT = new PluralCraftOAuthClient();
	private static final PluralBuddyApiClient API_CLIENT = new PluralBuddyApiClient();
	private static final AtomicBoolean LINK_IN_PROGRESS = new AtomicBoolean();

	private static boolean credentialStoreContainsToken;
	private static boolean credentialStoreUnavailable;
	private static boolean snapshotPersisted;

	private static String persistedRefreshToken;


	private PluralCraftClientLink()
	{
	}

	static PluralCraftOAuthClient.Tokens getSessionTokens()
	{
		return PluralCraftClient.sessionTokens;
	}

	static void startLinking(FabricClientCommandSource source)
	{
		if (!LINK_IN_PROGRESS.compareAndSet(false, true))
		{
			source.sendFeedback(Component.literal("PluralCraft account linking is already in progress."));
			return;
		}

		PluralCraftClient.sessionTokens = null;
		credentialStoreUnavailable = false;
		source.sendFeedback(
				Component.literal("PluralCraft ")
						.withStyle(ChatFormatting.DARK_AQUA)
						.append(Component.literal("is connecting")
								.withStyle(ChatFormatting.YELLOW))
						.append(Component.literal(" to PluralBuddy...")
								.withStyle(ChatFormatting.GRAY))
		);
		OAUTH_CLIENT.start()
				.thenCompose(session -> {
					openAuthorizationPage(session.authorizationUri(), source);
					return OAUTH_CLIENT.poll(session);
				})
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
					PluralCraftClient.sessionTokens = result.tokens();
					PluralCraftClient.accessExpiresAtMillis = System.currentTimeMillis()
							+ TimeUnit.SECONDS.toMillis(result.tokens().expiresIn());
					PluralCraftClient.linkedAutoproxy = result.autoproxy();
					PluralCraftClient.linkedAlters = result.alters();
					PluralCraftClientProxy.initializeActiveAlter();
					PluralCraftClient.sendPresence(PluralCraftClientProxy.activeAlter());
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
				return;
			}
		}
		catch (IOException | UnsupportedOperationException | SecurityException exception)
		{
			LOGGER.info("Could not open the PluralBuddy authorization page automatically.");
		}

		Minecraft.getInstance().execute(() -> source.sendFeedback(
				Component.literal("Open this URL to authorize PluralCraft: ")
						.append(Component.literal(authorizationUri.toString())
								.withStyle(style -> style
										.withColor(ChatFormatting.AQUA)
										.withUnderlined(true)
										.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(authorizationUri))
										.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
												Component.literal("Click to open in your browser")
										))
								))
		));
	}

	static void refreshLink(FabricClientCommandSource source)
	{
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
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

					PluralCraftClient.linkedAutoproxy = result.autoproxy();
					PluralCraftClient.linkedAlters = result.alters();
					PluralCraftClientProxy.initializeActiveAlter();
					PluralBuddyApiClient.Alter active = PluralCraftClientProxy.activeAlter();
					PluralCraftClient.sendPresence(active);
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
		).thenRun(PluralCraftClientLink::refreshWithoutFeedback);
	}

	private static CompletableFuture<PluralCraftOAuthClient.Tokens> ensureCurrentTokens()
	{
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
		if (tokens == null)
		{
			return CompletableFuture.failedFuture(new IllegalStateException("Not linked."));
		}

		if (PluralCraftClient.accessExpiresAtMillis > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1))
		{
			return CompletableFuture.completedFuture(tokens);
		}
		if (tokens.refreshToken() == null || tokens.refreshToken().isBlank())
		{
			return CompletableFuture.completedFuture(tokens);
		}

		return OAUTH_CLIENT.refresh(tokens.refreshToken()).thenApply(refreshed -> {
			PluralCraftOAuthClient.Tokens currentTokens = retainRefreshToken(refreshed, tokens.refreshToken());
			PluralCraftClient.sessionTokens = currentTokens;
			PluralCraftClient.accessExpiresAtMillis = System.currentTimeMillis()
					+ TimeUnit.SECONDS.toMillis(currentTokens.expiresIn());
			persistLink();
			return currentTokens;
		});
	}

	private static void refreshWithoutFeedback()
	{
		if (PluralCraftClient.sessionTokens == null)
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
						PluralCraftClient.linkedAutoproxy = result.autoproxy();
						PluralCraftClient.linkedAlters = result.alters();
						PluralCraftClientProxy.initializeActiveAlter();
						PluralCraftClient.sendPresence(PluralCraftClientProxy.activeAlter());
						persistLink();
					}
					scheduleRefresh();
				});
	}

	private static Path persistedLinkPath()
	{
		return FabricLoader.getInstance().getConfigDir()
				.resolve("pluralcraft")
				.resolve("link.json");
	}

	private static Path persistedLinkTemporaryPath()
	{
		Path path = persistedLinkPath();
		return path.resolveSibling(path.getFileName() + ".tmp");
	}

	static synchronized void persistLink()
	{
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
		if (tokens == null)
		{
			return;
		}

		String refreshToken = tokens.refreshToken();
		if (refreshToken != null
				&& !refreshToken.isBlank()
				&& !refreshToken.equals(persistedRefreshToken)
				&& !credentialStoreUnavailable)
		{
			try
			{
				PluralCraftCredentialStore.saveRefreshToken(refreshToken);
				persistedRefreshToken = refreshToken;
				credentialStoreContainsToken = true;
			}
			catch (PluralCraftCredentialStore.CredentialStoreException exception)
			{
				credentialStoreUnavailable = true;
				LOGGER.warn("Could not save the PluralCraft refresh token to the OS credential store. "
						+ "The link will not be restored after a restart.", exception);
			}
		}

		boolean tokenStored = refreshToken != null
				&& refreshToken.equals(persistedRefreshToken)
				&& credentialStoreContainsToken;
		snapshotPersisted = writePersistedSnapshot(tokenStored);
	}

	private static boolean writePersistedSnapshot(boolean tokenStored)
	{
		try
		{
			JsonObject root = new JsonObject();
			root.addProperty("credential_store", tokenStored);
			root.addProperty("latched_alter_id", PluralCraftClient.latchedAlterId);

			JsonArray autoproxy = createAutoproxy();
			root.add("autoproxy", autoproxy);

			JsonArray alters = new JsonArray();
			for (PluralBuddyApiClient.Alter alter : PluralCraftClient.linkedAlters)
			{
				JsonObject object = new JsonObject();
				object.addProperty("alter_id", alter.alterId());
				object.addProperty("username", alter.username());
				object.addProperty("display_name", alter.displayName());
				JsonArray tags = new JsonArray();
				for (PluralBuddyApiClient.ProxyTag tag : alter.proxyTags())
				{
					JsonObject tagObject = new JsonObject();
					tagObject.addProperty("prefix", tag.prefix());
					tagObject.addProperty("suffix", tag.suffix());
					tagObject.addProperty("id", tag.id());
					tags.add(tagObject);
				}
				object.add("proxy_tags", tags);
				alters.add(object);
			}
			root.add("alters", alters);

			Path path = persistedLinkPath();
			Files.createDirectories(path.getParent());
			Path temporary = persistedLinkTemporaryPath();
			Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
			Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			return true;
		}
		catch (IOException | SecurityException | UnsupportedOperationException exception)
		{
			LOGGER.warn("Could not persist the PluralCraft link snapshot.", exception);
			return false;
		}
		finally
		{
			try
			{
				Files.deleteIfExists(persistedLinkTemporaryPath());
			}
			catch (IOException | SecurityException exception)
			{
				LOGGER.warn("Could not remove the temporary PluralCraft link snapshot.", exception);
			}
		}
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
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
		return credentialStoreContainsToken
				&& snapshotPersisted
				&& tokens != null
				&& tokens.refreshToken() != null
				&& tokens.refreshToken().equals(persistedRefreshToken);
	}

	private static @NonNull JsonArray createAutoproxy()
	{
		JsonArray autoproxy = new JsonArray();
		for (PluralBuddyApiClient.Autoproxy entry : PluralCraftClient.linkedAutoproxy)
		{
			JsonObject object = new JsonObject();
			object.addProperty("mode", entry.autoproxyMode());
			object.addProperty("alter", entry.autoproxyAlter());
			object.addProperty("server_id", entry.serverId());
			object.addProperty("last_latch_timestamp", entry.lastLatchTimestamp());
			autoproxy.add(object);
		}
		return autoproxy;
	}

	static synchronized boolean deletePersistedLink()
	{
		boolean removed = true;
		try
		{
			Files.deleteIfExists(persistedLinkPath());
			Files.deleteIfExists(persistedLinkTemporaryPath());
			snapshotPersisted = false;
		}
		catch (IOException | SecurityException exception)
		{
			LOGGER.warn("Could not remove the persisted PluralCraft link snapshot.", exception);
			removed = false;
		}

		if (credentialStoreContainsToken)
		{
			try
			{
				PluralCraftCredentialStore.deleteRefreshToken();
				persistedRefreshToken = null;
				credentialStoreContainsToken = false;
			}
			catch (PluralCraftCredentialStore.CredentialStoreException exception)
			{
				LOGGER.warn("Could not remove the PluralCraft refresh token from the OS credential store.",
						exception);
				removed = false;
			}
		}
		return removed;
	}

	static synchronized void loadPersistedLink()
	{
		Path path = persistedLinkPath();
		try
		{
			Files.deleteIfExists(persistedLinkTemporaryPath());
		}
		catch (IOException | SecurityException exception)
		{
			LOGGER.warn("Could not remove a temporary PluralCraft link snapshot.", exception);
		}

		if (!Files.isRegularFile(path))
		{
			return;
		}

		try
		{
			JsonObject root = JsonParser.parseString(
					Files.readString(path, StandardCharsets.UTF_8)
			).getAsJsonObject();
			String accessToken = optionalPersistedString(root, "access_token");
			String legacyRefreshToken = optionalPersistedString(root, "refresh_token");
			JsonElement expiresElement = root.get("expires_at_millis");
			PluralCraftClient.accessExpiresAtMillis = expiresElement != null && expiresElement.isJsonPrimitive()
					&& expiresElement.getAsJsonPrimitive().isNumber()
					? expiresElement.getAsLong()
					: 0;
			PluralCraftClient.latchedAlterId = optionalPersistedString(root, "latched_alter_id");

			PluralCraftClient.linkedAutoproxy = parsePersistedAutoproxy(root);
			PluralCraftClient.linkedAlters = parsePersistedAlters(root);
			snapshotPersisted = true;

			if (legacyRefreshToken != null)
			{
				saveMigratedRefreshToken(legacyRefreshToken);
			}

			if (accessToken != null
					&& !accessToken.isBlank()
					&& PluralCraftClient.accessExpiresAtMillis > System.currentTimeMillis())
			{
				PluralCraftClient.sessionTokens = new PluralCraftOAuthClient.Tokens(
						accessToken,
						(int) Math.max(1,
								(PluralCraftClient.accessExpiresAtMillis - System.currentTimeMillis()) / 1000),
						legacyRefreshToken
				);
				persistLink();
				restorePersistedLink();
				return;
			}

			if (legacyRefreshToken != null)
			{
				snapshotPersisted = writePersistedSnapshot(credentialStoreContainsToken);
				refreshAndRestore(legacyRefreshToken);
				return;
			}

			if (root.has("credential_store")
					&& root.get("credential_store").isJsonPrimitive()
					&& root.getAsJsonPrimitive("credential_store").getAsBoolean())
			{
				credentialStoreContainsToken = true;
				String refreshToken = PluralCraftCredentialStore.readRefreshToken();
				if (refreshToken == null || refreshToken.isBlank())
				{
					throw new IllegalStateException("The OS credential store returned an empty refresh token.");
				}
				persistedRefreshToken = refreshToken;
				refreshAndRestore(refreshToken);
			}
			if (accessToken != null)
			{
				snapshotPersisted = writePersistedSnapshot(credentialStoreContainsToken);
			}
		}
		catch (PluralCraftCredentialStore.CredentialStoreException exception)
		{
			credentialStoreUnavailable = true;
			LOGGER.warn("Could not read the PluralCraft refresh token from the OS credential store. "
					+ "Use /pluralcraft link to connect again.", exception);
		}
		catch (RuntimeException | IOException exception)
		{
			LOGGER.warn("Could not read the persisted PluralCraft link snapshot.", exception);
		}
	}

	private static void saveMigratedRefreshToken(String refreshToken)
	{
		try
		{
			PluralCraftCredentialStore.saveRefreshToken(refreshToken);
			persistedRefreshToken = refreshToken;
			credentialStoreContainsToken = true;
		}
		catch (PluralCraftCredentialStore.CredentialStoreException exception)
		{
			credentialStoreUnavailable = true;
			credentialStoreContainsToken = false;
			LOGGER.warn("Could not migrate the legacy plaintext refresh token to the OS credential store. "
							+ "The token will be removed from the link snapshot and the link will not survive a restart.",
					exception);
		}
	}

	private static void refreshAndRestore(String refreshToken)
	{
		OAUTH_CLIENT.refresh(refreshToken).whenComplete((tokens, error) -> {
			if (error != null)
			{
				LOGGER.warn("Could not refresh the persisted PluralCraft link.");
				PluralCraftClient.sessionTokens = null;
				return;
			}

			PluralCraftOAuthClient.Tokens currentTokens = retainRefreshToken(tokens, refreshToken);
			PluralCraftClient.sessionTokens = currentTokens;
			PluralCraftClient.accessExpiresAtMillis = System.currentTimeMillis()
					+ TimeUnit.SECONDS.toMillis(currentTokens.expiresIn());
			persistLink();
			restorePersistedLink();
		});
	}

	private static String optionalPersistedString(JsonObject object, String field)
	{
		JsonElement value = object.get(field);
		if (value == null || value.isJsonNull())
		{
			return null;
		}
		String result = value.getAsString();
		return result == null || result.isBlank() ? null : result;
	}

	private static List<PluralBuddyApiClient.Autoproxy> parsePersistedAutoproxy(JsonObject root)
	{
		JsonArray entries = root.has("autoproxy") && root.get("autoproxy").isJsonArray()
				? root.getAsJsonArray("autoproxy")
				: new JsonArray();
		List<PluralBuddyApiClient.Autoproxy> result = new java.util.ArrayList<>();
		for (JsonElement element : entries)
		{
			if (!element.isJsonObject()) continue;
			JsonObject object = element.getAsJsonObject();
			result.add(new PluralBuddyApiClient.Autoproxy(
					object.has("mode") ? object.get("mode").getAsString() : "off",
					optionalPersistedString(object, "alter"),
					object.has("server_id") ? object.get("server_id").getAsString() : "",
					optionalPersistedString(object, "last_latch_timestamp")
			));
		}
		return List.copyOf(result);
	}

	private static List<PluralBuddyApiClient.Alter> parsePersistedAlters(JsonObject root)
	{
		JsonArray entries = root.has("alters") && root.get("alters").isJsonArray()
				? root.getAsJsonArray("alters")
				: new JsonArray();
		List<PluralBuddyApiClient.Alter> result = new java.util.ArrayList<>();
		for (JsonElement element : entries)
		{
			if (!element.isJsonObject()) continue;
			JsonObject object = element.getAsJsonObject();
			JsonArray tags = object.has("proxy_tags") && object.get("proxy_tags").isJsonArray()
					? object.getAsJsonArray("proxy_tags")
					: new JsonArray();
			List<PluralBuddyApiClient.ProxyTag> proxyTags = new java.util.ArrayList<>();
			for (JsonElement tagElement : tags)
			{
				if (!tagElement.isJsonObject()) continue;
				JsonObject tag = tagElement.getAsJsonObject();
				proxyTags.add(new PluralBuddyApiClient.ProxyTag(
						optionalPersistedString(tag, "prefix") == null ? "" : tag.get("prefix").getAsString(),
						optionalPersistedString(tag, "suffix") == null ? "" : tag.get("suffix").getAsString(),
						optionalPersistedString(tag, "id") == null ? "" : tag.get("id").getAsString()
				));
			}
			result.add(new PluralBuddyApiClient.Alter(
					object.get("alter_id").getAsString(),
					object.get("username").getAsString(),
					object.get("display_name").getAsString(),
					List.copyOf(proxyTags)
			));
		}
		return List.copyOf(result);
	}

	private static void restorePersistedLink()
	{
		PluralCraftClientProxy.initializeActiveAlter();
		PluralCraftClient.sendPresence(PluralCraftClientProxy.activeAlter());
		scheduleRefresh();

		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
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
							PluralCraftClient.linkedAutoproxy = result.autoproxy();
							PluralCraftClient.linkedAlters = result.alters();
							PluralCraftClientProxy.initializeActiveAlter();
							PluralCraftClient.sendPresence(PluralCraftClientProxy.activeAlter());
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
