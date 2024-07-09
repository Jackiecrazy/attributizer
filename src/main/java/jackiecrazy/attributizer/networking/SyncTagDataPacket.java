package jackiecrazy.attributizer.networking;

import io.netty.buffer.ByteBuf;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncTagDataPacket(int mapIndex,
                                Map<TagKey<Item>, Map<Holder<Attribute>, List<AttributeModifier>>> map) implements CustomPacketPayload {
    public static final StreamCodec<ByteBuf, TagKey<Item>> TAGS = ByteBufCodecs.STRING_UTF8
            .map(a->TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.parse(a)), a->a.location().toString());
    public static final CustomPacketPayload.Type<SyncTagDataPacket> MAIN = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Attributizer.MODID, "hand_tag"));
    static StreamCodec<RegistryFriendlyByteBuf, Map<TagKey<Item>, Map<Holder<Attribute>, List<AttributeModifier>>>> ITEM = ByteBufCodecs.map(HashMap::new, TAGS, ByteBufCodecs.map(HashMap::new, Attribute.STREAM_CODEC, ByteBufCodecs.collection(ArrayList::new, AttributeModifier.STREAM_CODEC)));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTagDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncTagDataPacket::mapIndex,
            ITEM,
            SyncTagDataPacket::map,
            SyncTagDataPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return MAIN;
    }

    public static class ClientPayloadHandler {

        public static void handleData(final SyncTagDataPacket data, final IPayloadContext context) {
            context.enqueueWork(() -> {
                switch (data.mapIndex) {
                    case 0 -> MainHandAttributizer.clientTagOverride(data.map);
                    case 1 -> OffhandAttributizer.clientTagOverride(data.map);
                }
            }).exceptionally(e -> {
                // Handle exception
                context.disconnect(Component.translatable("attributizer.networking.failed", e.getMessage()));
                return null;
            });
        }
    }
}
