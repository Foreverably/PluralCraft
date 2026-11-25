package net.foreverably.pluralcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient;
import net.foreverably.pluralcraft.client.api.PluralCraftOAuthClient;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
final class PluralCraftClientLinkPersistence
{

	private static final Logger LOGGER = LoggerFactory.getLogger("pluralcraft-client");

	private static boolean credentialStoreContainsToken;
	private static boolean credentialStoreUnavailable;
	private static boolean snapshotPersisted;

	private static String persistedRefreshToken;

	private PluralCraftClientLinkPersistence()
	{
	}

	static synchronized void resetCredentialStoreUnavailable()
	{
		credentialStoreUnavailable = false;
	}

	static synchronized void persist()
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

	static synchronized boolean hasPersistedRefreshToken()
	{
		PluralCraftOAuthClient.Tokens tokens = PluralCraftClient.sessionTokens;
		return credentialStoreContainsToken
				&& snapshotPersisted
				&& tokens != null
				&& tokens.refreshToken() != null
				&& tokens.refreshToken().equals(persistedRefreshToken);
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

	static synchronized PersistedLink load()
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
			return null;
		}

		try
		{
			JsonObject root = JsonParser.parseString(
					Files.readString(path, StandardCharsets.UTF_8)
			).getAsJsonObject();

			String accessToken = optionalPersistedString(root, "access_token");
			String refreshToken = optionalPersistedString(root, "refresh_token");

			boolean hasLegacyRefreshToken = refreshToken != null;

			JsonElement expiresElement = root.get("expires_at_millis");

			long accessExpiresAtMillis = expiresElement != null && expiresElement.isJsonPrimitive()
					&& expiresElement.getAsJsonPrimitive().isNumber()
					? expiresElement.getAsLong()
					: 0;

			PluralCraftClient.accessExpiresAtMillis = accessExpiresAtMillis;
			PluralCraftClient.latchedAlterId = optionalPersistedString(root, "latched_alter_id");

			PluralCraftClient.linkedAutoproxy = parsePersistedAutoproxy(root);
			PluralCraftClient.linkedAlters = parsePersistedAlters(root);
			snapshotPersisted = true;

			if (refreshToken != null)
			{
				saveMigratedRefreshToken(refreshToken);
			}

			boolean hasValidAccessToken = accessToken != null
					&& !accessToken.isBlank()
					&& accessExpiresAtMillis > System.currentTimeMillis();
			if (!hasValidAccessToken && refreshToken == null
					&& root.has("credential_store")
					&& root.get("credential_store").isJsonPrimitive()
					&& root.getAsJsonPrimitive("credential_store").getAsBoolean())
			{
				credentialStoreContainsToken = true;
				refreshToken = PluralCraftCredentialStore.readRefreshToken();
				if (refreshToken == null || refreshToken.isBlank())
				{
					throw new IllegalStateException("The OS credential store returned an empty refresh token.");
				}
				persistedRefreshToken = refreshToken;
			}

			if (!hasValidAccessToken && (hasLegacyRefreshToken || accessToken != null))
			{
				snapshotPersisted = writePersistedSnapshot(credentialStoreContainsToken);
			}
			return new PersistedLink(accessToken, refreshToken, accessExpiresAtMillis);
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
		return null;
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
			Path temporary = persistedLinkTemporaryPath();

			Files.createDirectories(path.getParent());


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

		List<PluralBuddyApiClient.Autoproxy> result = new ArrayList<>();

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
		List<PluralBuddyApiClient.Alter> result = new ArrayList<>();
		for (JsonElement element : entries)
		{
			if (!element.isJsonObject()) continue;
			JsonObject object = element.getAsJsonObject();
			JsonArray tags = object.has("proxy_tags") && object.get("proxy_tags").isJsonArray()
					? object.getAsJsonArray("proxy_tags")
					: new JsonArray();
			List<PluralBuddyApiClient.ProxyTag> proxyTags = new ArrayList<>();
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

	@NonNull
	private static JsonArray createAutoproxy()
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

	record PersistedLink(String accessToken, String refreshToken, long accessExpiresAtMillis)
	{

	}
}
