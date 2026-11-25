package net.foreverably.pluralcraft.client.constants;

import java.time.Duration;

public final class PluralBuddyApiConfig
{

	public static final String API_BASE_URL = "https://pluralbuddy.app/api";
	public static final int ALTER_PAGE_SIZE = 30;
	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	private PluralBuddyApiConfig()
	{
	}
}
