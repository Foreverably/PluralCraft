package net.foreverably.pluralcraft.client.constants;

import java.time.Duration;

public final class PluralCraftOAuthConfig
{

	/**
	 * Base URL of the Website to use. Used in conjunction with the OAuth endpoints
	 */
	private static final String WEBSITE_BASE_URL = "https://pluralcraft.forwynn.net";
	/**
	 * Request timeout duration
	 */
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	public PluralCraftOAuthConfig()
	{
	}

	public String getBaseURL()
	{
		return WEBSITE_BASE_URL;
	}

	public Duration getRequestTimeout()
	{
		return REQUEST_TIMEOUT;
	}

}
 