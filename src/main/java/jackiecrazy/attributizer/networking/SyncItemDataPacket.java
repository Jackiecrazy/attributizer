package jackiecrazy.attributizer.networking;

import jackiecrazy.attributizer.ArmorAttributizer;
import jackiecrazy.attributizer.Attributizer;
import jackiecrazy.attributizer.MainHandAttributizer;
import jackiecrazy.attributizer.OffhandAttributizer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record SyncItemDataPacket(int mapIndex,
                                 Map<Item, Map<Holder<Attribute>, List<AttributeModifier>>> map) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncItemDataPacket> MAIN = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Attributizer.MODID, "hand_data"));
    static StreamCodec<RegistryFriendlyByteBuf, Map<Item, Map<Holder<Attribute>, List<AttributeModifier>>>> ITEM = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(BuiltInRegistries.ITEM.key()), ByteBufCodecs.map(HashMap::new, Attribute.STREAM_CODEC, ByteBufCodecs.collection(ArrayList::new, AttributeModifier.STREAM_CODEC)));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncItemDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncItemDataPacket::mapIndex,
            ITEM,
            SyncItemDataPacket::map,
            SyncItemDataPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return MAIN;
    }

    public static class ClientPayloadHandler {

        public static void handleData(final SyncItemDataPacket data, final IPayloadContext context) {
            context.enqueueWork(() -> {
                switch (data.mapIndex) {
                    case 0 -> MainHandAttributizer.clientDataOverride(data.map);
                    case 1 -> OffhandAttributizer.clientDataOverride(data.map);
                    case 2 -> ArmorAttributizer.clientDataOverride(data.map);
                }
            }).exceptionally(e -> {
                // Handle exception
                context.disconnect(Component.translatable("attributizer.networking.failed", e.getMessage()));
                return null;
            });
        }
    }
}
