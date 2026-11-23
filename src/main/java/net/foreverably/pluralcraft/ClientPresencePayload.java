package net.foreverably.pluralcraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientPresencePayload(String playerUuid, String alterId, String alterName) implements CustomPacketPayload
{

    public static final String CHAT_MARKER_PREFIX = "\uE000pluralcraft:";
    public static final String CHAT_MARKER_SUFFIX = "\uE001";
    public static final Type<ClientPresencePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(PluralCraft.MOD_ID, "client_presence")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientPresencePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.playerUuid());
                        buffer.writeUtf(payload.alterId());
                        buffer.writeUtf(payload.alterName());
                    },
                    buffer -> new ClientPresencePayload(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf()
                    )
            );

    public static String chatMarker(String alterId, String alterName)
    {
        if (alterId == null || alterId.isBlank()
                || alterName == null || alterName.isBlank())
        {
            return CHAT_MARKER_PREFIX + "none" + CHAT_MARKER_SUFFIX;
        }
        String encodedName = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alterName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return CHAT_MARKER_PREFIX + alterId + ":" + encodedName + CHAT_MARKER_SUFFIX;
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
