package net.foreverably.pluralcraft.client.constants;

import java.time.Duration;

public final class PluralBuddyApiConfig
{

	/**
	 * Base URL of the API to use
	 */
	private static final String API_BASE_URL = "https://pluralbuddy.app/api";
	/**
	 * The size of each page of results
	 */
	private static final int ALTER_PAGE_SIZE = 30;
	/**
	 * Request timeout duration
	 */
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	public PluralBuddyApiConfig()
	{
	}

	public String getApiBaseUrl()
	{
		return API_BASE_URL;
	}

	public int getAlterPageSize()
	{
		return ALTER_PAGE_SIZE;
	}

	public Duration getRequestTimeout()
	{
		return REQUEST_TIMEOUT;
	}
}
