package net.foreverably.pluralcraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientPresencePayload() implements CustomPacketPayload {
	public static final Type<ClientPresencePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(PluralCraft.MOD_ID, "client_presence")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientPresencePayload> CODEC =
			StreamCodec.unit(new ClientPresencePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
