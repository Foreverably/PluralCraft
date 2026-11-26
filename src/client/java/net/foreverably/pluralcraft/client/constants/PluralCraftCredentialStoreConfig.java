package net.foreverably.pluralcraft.client.constants;

public final class PluralCraftCredentialStoreConfig
{

	/**
	 * The service name for the credential store
	 */
	private static final String SERVICE = "PluralCraft";
	/**
	 * The account name for the credential store
	 */
	private static final String ACCOUNT = "PluralBuddy OAuth refresh token";

	public PluralCraftCredentialStoreConfig()
	{
	}

	public String getServiceName()
	{
		return SERVICE;
	}

	public String getAccountName()
	{
		return ACCOUNT;
	}
}
 