package net.foreverably.pluralcraft.client;

import com.github.javakeyring.Keyring;
import net.fabricmc.loader.api.FabricLoader;
import net.foreverably.pluralcraft.client.constants.PluralCraftCredentialStoreConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CredentialStore
{

	private static final PluralCraftCredentialStoreConfig CONFIG = new PluralCraftCredentialStoreConfig();

	private CredentialStore()
	{
	}

	static String readRefreshToken() throws CredentialStoreException
	{
		return withKeyring(keyring -> keyring.getPassword(
				CONFIG.getServiceName(),
				account()
		));
	}

	static void saveRefreshToken(String refreshToken) throws CredentialStoreException
	{
		withKeyring(keyring -> {
			keyring.setPassword(CONFIG.getServiceName(), account(), refreshToken);
			return null;
		});
	}

	static void deleteRefreshToken() throws CredentialStoreException
	{
		withKeyring(keyring -> {
			keyring.deletePassword(CONFIG.getServiceName(), account());
			return null;
		});
	}

	private static String account()
	{
		String configPath = FabricLoader.getInstance().getConfigDir()
				.toAbsolutePath()
				.normalize()
				.toString();
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(configPath.getBytes(StandardCharsets.UTF_8));
			return CONFIG.getAccountName()
					+ " " + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private static <T> T withKeyring(KeyringOperation<T> operation) throws CredentialStoreException
	{
		try (Keyring keyring = Keyring.create())
		{
			return operation.apply(keyring);
		}
		catch (Exception | LinkageError exception)
		{
			throw new CredentialStoreException(exception);
		}
	}

	@FunctionalInterface
	private interface KeyringOperation<T>
	{

		T apply(Keyring keyring) throws Exception;
	}

	static final class CredentialStoreException extends Exception
	{

		private CredentialStoreException(Throwable cause)
		{
			super(cause);
		}
	}
}
