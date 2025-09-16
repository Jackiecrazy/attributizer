package jackiecrazy.attributizer.networking;

import jackiecrazy.attributizer.ArmorAttributizer;
import jackiecrazy.attributizer.ItemAttributeMod;
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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SyncItemDataPacket {
    private static final FriendlyByteBuf.Writer<Item> item = (f, item) -> f.writeResourceLocation(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)));
    private static final FriendlyByteBuf.Writer<List<ItemAttributeMod>> info = (f, info) -> f.writeCollection(info, (ff, a) -> {
        f.writeResourceLocation(ForgeRegistries.ATTRIBUTES.getKey(a.attribute));
        f.writeUUID(a.uuid);
        f.writeDouble(a.modify);
        f.writeInt(a.operation.ordinal());
    });

    private static final FriendlyByteBuf.Reader<Item> ritem = friendlyByteBuf -> ForgeRegistries.ITEMS.getValue(friendlyByteBuf.readResourceLocation());
    private static final FriendlyByteBuf.Reader<List<ItemAttributeMod>> rinfo = (f) -> f.readList(
            (ff) -> new ItemAttributeMod(ForgeRegistries.ATTRIBUTES.getValue(ff.readResourceLocation()), ff.readUUID(), ff.readDouble(), ItemAttributeMod.Operation.values()[ff.readInt()])
    );

    private final Map<Item, List<ItemAttributeMod>> map;
    private final int type;

    public SyncItemDataPacket(int type, Map<Item, List<ItemAttributeMod>> map) {
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
