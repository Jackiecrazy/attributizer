package jackiecrazy.attributizer.networking;

import jackiecrazy.attributizer.ArmorAttributizer;
import jackiecrazy.attributizer.MainHandAttributizer;
import jackiecrazy.attributizer.OffhandAttributizer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SyncItemDataPacket {
    private static final FriendlyByteBuf.Writer<Item> item = (f, item) -> f.writeResourceLocation(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)));
    private static final FriendlyByteBuf.Writer<Map<Attribute, List<AttributeModifier>>> info = (f, info) -> f.writeMap(info, (ff, a) -> ff.writeResourceLocation(ForgeRegistries.ATTRIBUTES.getKey(a)), (ff, b) -> ff.writeCollection(b, (fbb, am) -> {
        fbb.writeUUID(am.getId());
        fbb.writeDouble(am.getAmount());
        fbb.writeByte(am.getOperation().toValue());
    }));

    private static final FriendlyByteBuf.Reader<Item> ritem = friendlyByteBuf -> ForgeRegistries.ITEMS.getValue(friendlyByteBuf.readResourceLocation());
    private static final FriendlyByteBuf.Reader<Map<Attribute, List<AttributeModifier>>> rinfo = (f) -> f.readMap((ff) -> ForgeRegistries.ATTRIBUTES.getValue(ff.readResourceLocation()), (ff) -> ff.readList((p_179457_) -> new AttributeModifier(p_179457_.readUUID(), "Unknown synced attribute modifier", p_179457_.readDouble(), AttributeModifier.Operation.fromValue(p_179457_.readByte()))));
    private final Map<Item, Map<Attribute, List<AttributeModifier>>> map;
    private final int type;

    public SyncItemDataPacket(int type, Map<Item, Map<Attribute, List<AttributeModifier>>> map) {
        this.type = type;
        this.map = map;
    }

    public static class Encoder implements BiConsumer<SyncItemDataPacket, FriendlyByteBuf> {

        @Override
        public void accept(SyncItemDataPacket packet, FriendlyByteBuf packetBuffer) {
            packetBuffer.writeInt(packet.type);
            packetBuffer.writeMap(packet.map, item, info);
        }
    }

    public static class Decoder implements Function<FriendlyByteBuf, SyncItemDataPacket> {

        @Override
        public SyncItemDataPacket apply(FriendlyByteBuf packetBuffer) {
            return new SyncItemDataPacket(packetBuffer.readInt(), packetBuffer.readMap(ritem, rinfo));
        }
    }

    public static class Handler implements BiConsumer<SyncItemDataPacket, Supplier<NetworkEvent.Context>> {

        @Override
        public void accept(SyncItemDataPacket updateClientPacket, Supplier<NetworkEvent.Context> contextSupplier) {

            //prevent client overriding server
            if (contextSupplier.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT)
                contextSupplier.get().enqueueWork(() -> {
                    switch (updateClientPacket.type) {
                        case 0 -> MainHandAttributizer.clientDataOverride(updateClientPacket.map);
                        case 1 -> OffhandAttributizer.clientDataOverride(updateClientPacket.map);
                        case 2 -> ArmorAttributizer.clientDataOverride(updateClientPacket.map);
                    }
                });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
