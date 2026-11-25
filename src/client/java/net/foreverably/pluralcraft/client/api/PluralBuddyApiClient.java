package net.foreverably.pluralcraft.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.foreverably.pluralcraft.client.constants.PluralBuddyApiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PluralBuddyApiClient
{

	private final HttpClient httpClient;

	public PluralBuddyApiClient()
	{
		this(HttpClient.newBuilder()
				.connectTimeout(PluralBuddyApiConfig.REQUEST_TIMEOUT)
				.build());
	}

	PluralBuddyApiClient(HttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	private static SystemData parseSystem(JsonElement response)
	{
		JsonObject root = requireObject(response, "system response");
		JsonObject data = requireObject(root.get("data"), "system response data");
		JsonArray autoproxyEntries = requireArray(data.get("systemAutoproxy"), "systemAutoproxy");
		List<Autoproxy> autoproxy = new ArrayList<>(autoproxyEntries.size());

		for (JsonElement entry : autoproxyEntries)
		{
			JsonObject object = requireObject(entry, "systemAutoproxy entry");
			autoproxy.add(new Autoproxy(
					requiredString(object, "autoproxyMode"),
					optionalString(object, "autoproxyAlter"),
					requiredString(object, "serverId"),
					optionalString(object, "lastLatchTimestamp")
			));
		}

		return new SystemData(List.copyOf(autoproxy));
	}

	private static List<Alter> parseAlters(JsonElement response)
	{
		if (response == null || !response.isJsonArray())
		{
			throw new IllegalStateException("PluralBuddy returned an invalid alters response.");
		}

		JsonArray entries = response.getAsJsonArray();
		List<Alter> alters = new ArrayList<>(entries.size());

		for (JsonElement entry : entries)
		{
			JsonObject object = requireObject(entry, "alter");
			JsonArray proxyTags = object.has("proxyTags") && !object.get("proxyTags").isJsonNull()
					? requireArray(object.get("proxyTags"), "proxyTags")
					: new JsonArray();
			List<ProxyTag> tags = new ArrayList<>(proxyTags.size());

			for (JsonElement tag : proxyTags)
			{
				JsonObject tagObject = requireObject(tag, "proxy tag");
				String prefix = optionalString(tagObject, "prefix");
				String suffix = optionalString(tagObject, "suffix");
				tags.add(new ProxyTag(
						prefix == null ? "" : prefix,
						suffix == null ? "" : suffix,
						requiredString(tagObject, "id")
				));
			}

			alters.add(new Alter(
					requiredString(object, "alterId"),
					requiredString(object, "username"),
					requiredString(object, "displayName"),
					List.copyOf(tags)
			));
		}

		return List.copyOf(alters);
	}

	private static JsonObject requireObject(JsonElement element, String field)
	{
		if (element == null || !element.isJsonObject())
		{
			throw new IllegalStateException("PluralBuddy response is missing " + field + ".");
		}
		return element.getAsJsonObject();
	}

	private static JsonArray requireArray(JsonElement element, String field)
	{
		if (element == null || !element.isJsonArray())
		{
			throw new IllegalStateException("PluralBuddy response is missing " + field + ".");
		}
		return element.getAsJsonArray();
	}

	private static String requiredString(JsonObject object, String field)
	{
		String value = optionalString(object, field);
		if (value == null)
		{
			throw new IllegalStateException("PluralBuddy response is missing " + field + ".");
		}
		return value;
	}

	private static String optionalString(JsonObject object, String field)
	{
		JsonElement value = object.get(field);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	public CompletableFuture<SystemData> getSystem(String accessToken)
	{
		return get("/v1/users/@me/system", accessToken)
				.thenApply(PluralBuddyApiClient::parseSystem);
	}

	public CompletableFuture<List<Alter>> getAlters(String accessToken)
	{
		return getAlterPage(accessToken, 0, new ArrayList<>());
	}

	private CompletableFuture<List<Alter>> getAlterPage(
			String accessToken,
			int skip,
			List<Alter> alters
	)
	{
		String path = "/v1/users/@me/system/list-alters?max="
				+ PluralBuddyApiConfig.ALTER_PAGE_SIZE + "&skip=" + skip;

		return get(path, accessToken).thenCompose(response -> {
			List<Alter> page = parseAlters(response);
			alters.addAll(page);

			if (page.size() < PluralBuddyApiConfig.ALTER_PAGE_SIZE)
			{
				return CompletableFuture.completedFuture(List.copyOf(alters));
			}

			return getAlterPage(accessToken, skip + page.size(), alters);
		});
	}

	private CompletableFuture<JsonElement> get(String path, String accessToken)
	{
		if (accessToken == null || accessToken.isBlank())
		{
			return CompletableFuture.failedFuture(
					new IllegalArgumentException("A PluralBuddy access token is required.")
			);
		}

		HttpRequest request = HttpRequest.newBuilder(
						URI.create(PluralBuddyApiConfig.API_BASE_URL + path)
				)
				.timeout(PluralBuddyApiConfig.REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() < 200 || response.statusCode() >= 300)
					{
						throw new ApiException(response.statusCode());
					}

					return JsonParser.parseString(response.body());
				});
	}

	public record SystemData(List<Autoproxy> systemAutoproxy)
	{

	}

	public record Autoproxy(
			String autoproxyMode,
			String autoproxyAlter,
			String serverId,
			String lastLatchTimestamp
	)
	{

	}

	public record Alter(String alterId, String username, String displayName, List<ProxyTag> proxyTags)
	{

	}

	public record ProxyTag(String prefix, String suffix, String id)
	{

	}

	public static final class ApiException extends RuntimeException
	{

		private final int statusCode;

		private ApiException(int statusCode)
		{
			super("PluralBuddy API request failed with HTTP status " + statusCode + ".");
			this.statusCode = statusCode;
		}

		public int statusCode()
		{
			return statusCode;
		}
	}
}
