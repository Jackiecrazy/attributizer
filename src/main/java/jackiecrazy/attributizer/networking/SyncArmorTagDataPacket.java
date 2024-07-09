package jackiecrazy.attributizer.networking;

import io.netty.buffer.ByteBuf;
import jackiecrazy.attributizer.ArmorAttributizer;
import jackiecrazy.attributizer.Attributizer;
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

public record SyncArmorTagDataPacket (Map<TagKey<Item>, Map<Holder<Attribute>, List<List<AttributeModifier>>>> map) implements CustomPacketPayload {
    public static final StreamCodec<ByteBuf, TagKey<Item>> TAGS = ByteBufCodecs.STRING_UTF8
            .map(a->TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.parse(a)), a->a.location().toString());
    public static final CustomPacketPayload.Type<SyncArmorTagDataPacket> MAIN = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Attributizer.MODID, "armor_tag"));
    static StreamCodec<RegistryFriendlyByteBuf, Map<TagKey<Item>, Map<Holder<Attribute>, List<List<AttributeModifier>>>>> ITEM = ByteBufCodecs.map(HashMap::new, TAGS, ByteBufCodecs.map(HashMap::new, Attribute.STREAM_CODEC, ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.collection(ArrayList::new, AttributeModifier.STREAM_CODEC))));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncArmorTagDataPacket> STREAM_CODEC = StreamCodec.composite(
            ITEM,
            SyncArmorTagDataPacket::map,
            SyncArmorTagDataPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return MAIN;
    }

    public static class ClientPayloadHandler {

        public static void handleData(final SyncArmorTagDataPacket data, final IPayloadContext context) {
            context.enqueueWork(() -> {
                ArmorAttributizer.clientTagOverride(data.map);
            }).exceptionally(e -> {
                // Handle exception
                context.disconnect(Component.translatable("attributizer.networking.failed", e.getMessage()));
                return null;
            });
        }
    }

}
