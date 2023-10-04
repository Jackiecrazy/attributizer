package jackiecrazy.attributizer.networking;

import jackiecrazy.attributizer.ArmorAttributizer;
import jackiecrazy.attributizer.MainHandAttributizer;
import jackiecrazy.attributizer.OffhandAttributizer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SyncArmorTagDataPacket {
    private static final FriendlyByteBuf.Writer<TagKey<Item>> item = (f, item) -> f.writeResourceLocation(item.location());
    private static final FriendlyByteBuf.Writer<Map<Attribute, List<AttributeModifier[]>>> info = (f, info) -> f.writeMap(info, (ff, a) -> ff.writeResourceLocation(ForgeRegistries.ATTRIBUTES.getKey(a)), (ff, b) -> ff.writeCollection(b, (fbb, al) -> {
        for (AttributeModifier am : al) {
            fbb.writeUUID(am.getId());
            fbb.writeDouble(am.getAmount());
            fbb.writeByte(am.getOperation().toValue());
        }
    }));

    private static final FriendlyByteBuf.Reader<TagKey<Item>> ritem = f -> ItemTags.create(f.readResourceLocation());
    ;
    private static final FriendlyByteBuf.Reader<Map<Attribute, List<AttributeModifier[]>>> rinfo = (f) -> f.readMap((ff) -> ForgeRegistries.ATTRIBUTES.getValue(ff.readResourceLocation()), (ff) -> ff.readList((p_179457_) -> {
        AttributeModifier[] ret = new AttributeModifier[4];
        for (int x = 0; x < ret.length; x++) {
            ret[x] = new AttributeModifier(p_179457_.readUUID(), "Unknown synced attribute modifier", p_179457_.readDouble(), AttributeModifier.Operation.fromValue(p_179457_.readByte()));
        }
        return ret;
    }));
    private final Map<TagKey<Item>, Map<Attribute, List<AttributeModifier[]>>> map;

    public SyncArmorTagDataPacket(Map<TagKey<Item>, Map<Attribute, List<AttributeModifier[]>>> map) {
        this.map = map;
    }

    public static class Encoder implements BiConsumer<SyncArmorTagDataPacket, FriendlyByteBuf> {

        @Override
        public void accept(SyncArmorTagDataPacket packet, FriendlyByteBuf packetBuffer) {
            packetBuffer.writeMap(packet.map, item, info);
        }
    }

    public static class Decoder implements Function<FriendlyByteBuf, SyncArmorTagDataPacket> {

        @Override
        public SyncArmorTagDataPacket apply(FriendlyByteBuf packetBuffer) {
            return new SyncArmorTagDataPacket(packetBuffer.readMap(ritem, rinfo));
        }
    }

    public static class Handler implements BiConsumer<SyncArmorTagDataPacket, Supplier<NetworkEvent.Context>> {

        @Override
        public void accept(SyncArmorTagDataPacket updateClientPacket, Supplier<NetworkEvent.Context> contextSupplier) {

            //prevent client overriding server
            if (contextSupplier.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT)
                contextSupplier.get().enqueueWork(() -> {
                    ArmorAttributizer.clientTagOverride(updateClientPacket.map);
                });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
