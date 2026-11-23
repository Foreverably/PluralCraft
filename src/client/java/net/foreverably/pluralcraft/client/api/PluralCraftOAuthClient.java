package net.foreverably.pluralcraft.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.foreverably.pluralcraft.client.api.PluralBuddyApiClient.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class PluralCraftOAuthClient
{

    private static final String WEBSITE_BASE_URL = "https://pluralcraft.forwynn.net";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static AuthorizationSession parseAuthorizationSession(JsonElement response)
    {
        if (!response.isJsonObject())
        {
            throw new OAuthException("invalid_website_response");
        }
 
        JsonObject object = response.getAsJsonObject();
        String sessionId = requiredString(object, "session_id");
        String authorizationUrl = requiredString(object, "authorization_url");
        int expiresIn = requiredPositiveInt(object, "expires_in");
        int interval = requiredPositiveInt(object, "interval");
        URI uri;
        try
        {
            uri = URI.create(authorizationUrl);
        }
        catch (IllegalArgumentException exception)
        {
            throw new OAuthException("invalid_authorization_url", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"pluralbuddy.app".equalsIgnoreCase(uri.getHost()))
        {
            throw new OAuthException("invalid_authorization_url");
        }

        return new AuthorizationSession(sessionId, uri, expiresIn, interval);
    }

    private static PollResult parsePollResult(JsonElement response)
    {
        if (!response.isJsonObject())
        {
            throw new OAuthException("invalid_website_response");
        }

        JsonObject object = response.getAsJsonObject();
        String status = requiredString(object, "status");
        if (status.equals("pending"))
        {
            return new PollResult(status, null, null, null);
        }
        if (status.equals("failed"))
        {
            return new PollResult(
                    status,
                    null,
                    optionalString(object, "error"),
                    optionalErrorDescription(object)
            );
        }
        if (!status.equals("authorized"))
        {
            throw new OAuthException("invalid_website_response");
        }

        JsonElement tokensElement = object.get("tokens");
        if (tokensElement == null || !tokensElement.isJsonObject())
        {
            throw new OAuthException("invalid_website_response");
        }

        JsonObject tokens = tokensElement.getAsJsonObject();
        return new PollResult(status, new Tokens(
                requiredString(tokens, "access_token"),
                requiredPositiveInt(tokens, "expires_in"),
                optionalString(tokens, "refresh_token")
        ), null, null);
    }

    private static String optionalErrorDescription(JsonObject object)
    {
        String value = optionalString(object, "error_description");
        if (value == null)
        {
            return null;
        }

        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isEmpty())
        {
            return null;
        }
        return sanitized.substring(0, Math.min(200, sanitized.length()));
    }

    private static String requiredString(JsonObject object, String field)
    {
        String value = optionalString(object, field);
        if (value == null || value.isBlank())
        {
            throw new OAuthException("invalid_website_response");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String field)
    {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int requiredPositiveInt(JsonObject object, String field)
    {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            throw new OAuthException("invalid_website_response");
        }
        int result = value.getAsInt();
        if (result <= 0)
        {
            throw new OAuthException("invalid_website_response");
        }
        return result;
    }

    public CompletableFuture<AuthorizationSession> start()
    {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(WEBSITE_BASE_URL + "/api/pluralbuddy/oauth/start")
                )
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        return send(request).thenApply(PluralCraftOAuthClient::parseAuthorizationSession);
    }

    public CompletableFuture<Tokens> refresh(String refreshToken)
    {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("refresh_token", refreshToken);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(WEBSITE_BASE_URL + "/api/pluralbuddy/oauth/refresh")
                )
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return send(request).thenApply(response -> {
            JsonObject root = response.getAsJsonObject();
            JsonElement tokensElement = root.get("tokens");
            if (tokensElement == null || !tokensElement.isJsonObject())
            {
                throw new OAuthException("invalid_website_response");
            }

            JsonObject tokens = tokensElement.getAsJsonObject();
            return new Tokens(
                    requiredString(tokens, "access_token"),
                    requiredPositiveInt(tokens, "expires_in"),
                    optionalString(tokens, "refresh_token")
            );
        });
    }

    public CompletableFuture<Tokens> poll(AuthorizationSession session)
    {
        long expiresAt = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(session.expiresIn());
        return pollSession(session, expiresAt);
    }

    private CompletableFuture<Tokens> pollSession(AuthorizationSession session, long expiresAt)
    {
        if (System.nanoTime() >= expiresAt)
        {
            return CompletableFuture.failedFuture(new OAuthException("authorization_expired"));
        }

        return CompletableFuture.supplyAsync(
                        () -> null,
                        CompletableFuture.delayedExecutor(session.interval(), TimeUnit.SECONDS)
                )
                .thenCompose(ignored -> pollOnce(session.sessionId()))
                .thenCompose(result -> {
                    if (result.status().equals("authorized"))
                    {
                        return CompletableFuture.completedFuture(result.tokens());
                    }
                    if (result.status().equals("failed"))
                    {
                        return CompletableFuture.failedFuture(new OAuthException(
                                result.error(),
                                result.errorDescription()
                        ));
                    }
                    return pollSession(session, expiresAt);
                });
    }

    private CompletableFuture<PollResult> pollOnce(String sessionId)
    {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("session_id", sessionId);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(WEBSITE_BASE_URL + "/api/pluralbuddy/oauth/poll")
                )
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return send(request).thenApply(PluralCraftOAuthClient::parsePollResult);
    }

    private CompletableFuture<JsonElement> send(HttpRequest request)
    {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonElement body;
                    try
                    {
                        body = JsonParser.parseString(response.body());
                    }
                    catch (RuntimeException exception)
                    {
                        throw new OAuthException("invalid_website_response", exception);
                    }

                    if (response.statusCode() < 200 || response.statusCode() >= 300)
                    {
                        String error = body.isJsonObject()
                                ? optionalString(body.getAsJsonObject(), "error")
                                : null;
                        throw new OAuthException(error == null
                                ? "website_request_failed"
                                : error);
                    }
                    return body;
                });
    }

    public record AuthorizationSession(String sessionId, URI authorizationUri, int expiresIn, int interval)
    {

    }

    public record Tokens(String accessToken, int expiresIn, String refreshToken)
    {

    }

    private record PollResult(
            String status,
            Tokens tokens,
            String error,
            String errorDescription
    )
    {

    }

    public static final class OAuthException extends RuntimeException
    {

        private final String code;
        private final String description;

        private OAuthException(String code)
        {
            super(code);
            this.code = code;
            this.description = null;
        }

        private OAuthException(String code, String description)
        {
            super(code);
            this.code = code;
            this.description = description;
        }

        private OAuthException(String code, Throwable cause)
        {
            super(code, cause);
            this.code = code;
            this.description = null;
        }

        public static OAuthException unwrap(Throwable error)
        {
            Throwable current = error;
            while ((current instanceof CompletionException || current instanceof ExecutionException)
                    && current.getCause() != null)
            {
                current = current.getCause();
            }
            if (current instanceof OAuthException oauthException)
            {
                return oauthException;
            }
            if (current instanceof ApiException apiException)
            {
                return new OAuthException(
                        "pluralbuddy_api_request_failed",
                        "PluralBuddy returned HTTP status " + apiException.statusCode() + "."
                );
            }
            if (current instanceof HttpTimeoutException)
            {
                return new OAuthException("request_timed_out", current);
            }
            if (current instanceof IOException)
            {
                return new OAuthException("network_error", current);
            }
            return new OAuthException("connection_failed", current);
        }

        public String code()
        {
            return code;
        }

        public String description()
        {
            return description;
        }
    }
}
