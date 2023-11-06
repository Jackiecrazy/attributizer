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

public class SyncTagDataPacket {
    private static final FriendlyByteBuf.Writer<TagKey<Item>> item = (f, item) -> f.writeResourceLocation(item.location());
    private static final FriendlyByteBuf.Writer<Map<Attribute, List<AttributeModifier>>> info = (f, info) -> f.writeMap(info, (ff, a) -> ff.writeResourceLocation(ForgeRegistries.ATTRIBUTES.getKey(a)), (ff, b) -> ff.writeCollection(b, (fbb, am) -> {
        fbb.writeUUID(am.getId());
        fbb.writeDouble(am.getAmount());
        fbb.writeByte(am.getOperation().toValue());
    }));

    private static final FriendlyByteBuf.Reader<TagKey<Item>> ritem = f -> ItemTags.create(f.readResourceLocation());;
    private static final FriendlyByteBuf.Reader<Map<Attribute, List<AttributeModifier>>> rinfo = (f) -> f.readMap((ff) -> ForgeRegistries.ATTRIBUTES.getValue(ff.readResourceLocation()), (ff) -> ff.readList((p_179457_) -> new AttributeModifier(p_179457_.readUUID(), "Unknown synced attribute modifier", p_179457_.readDouble(), AttributeModifier.Operation.fromValue(p_179457_.readByte()))));
    private final Map<TagKey<Item>, Map<Attribute, List<AttributeModifier>>> map;
    private final int type;

    public SyncTagDataPacket(int type, Map<TagKey<Item>, Map<Attribute, List<AttributeModifier>>> map) {
        this.type=type;
        this.map = map;
    }

    public static class Encoder implements BiConsumer<SyncTagDataPacket, FriendlyByteBuf> {

        @Override
        public void accept(SyncTagDataPacket packet, FriendlyByteBuf packetBuffer) {
            packetBuffer.writeInt(packet.type);
            packetBuffer.writeMap(packet.map, item, info);
        }
    }

    public static class Decoder implements Function<FriendlyByteBuf, SyncTagDataPacket> {

        @Override
        public SyncTagDataPacket apply(FriendlyByteBuf packetBuffer) {
            return new SyncTagDataPacket(packetBuffer.readInt(), packetBuffer.readMap(ritem, rinfo));
        }
    }

    public static class Handler implements BiConsumer<SyncTagDataPacket, Supplier<NetworkEvent.Context>> {

        @Override
        public void accept(SyncTagDataPacket updateClientPacket, Supplier<NetworkEvent.Context> contextSupplier) {

            //prevent client overriding server
            if (contextSupplier.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT)
                contextSupplier.get().enqueueWork(() -> {
                    switch (updateClientPacket.type) {
                        case 0 -> MainHandAttributizer.clientTagOverride(updateClientPacket.map);
                        case 1 -> OffhandAttributizer.clientTagOverride(updateClientPacket.map);
                    }
                });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
