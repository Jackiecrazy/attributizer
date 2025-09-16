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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SyncArmorTagDataPacket {
    private static final FriendlyByteBuf.Writer<TagKey<Item>> item = (f, item) -> f.writeResourceLocation(item.location());
    private static final FriendlyByteBuf.Writer<List<ItemAttributeMod[]>> info = (f, info) -> f.writeCollection(info, (ff, aa) -> {
        for(ItemAttributeMod a:aa) {
            f.writeResourceLocation(ForgeRegistries.ATTRIBUTES.getKey(a.attribute));
            f.writeUUID(a.uuid);
            f.writeDouble(a.modify);
            f.writeInt(a.operation.ordinal());
        }
    });

    private static final FriendlyByteBuf.Reader<TagKey<Item>> ritem = f -> ItemTags.create(f.readResourceLocation());

    private static final FriendlyByteBuf.Reader<List<ItemAttributeMod[]>> rinfo = (f) -> f.readList(
            (ff) -> {
                ItemAttributeMod[] ret = new ItemAttributeMod[4];
                for (int x = 0; x < ret.length; x++) {
                    ret[x] = new ItemAttributeMod(ForgeRegistries.ATTRIBUTES.getValue(ff.readResourceLocation()), ff.readUUID(), ff.readDouble(), ItemAttributeMod.Operation.values()[ff.readInt()]);
                }
                return ret;
            }
    );
    private final Map<TagKey<Item>, List<ItemAttributeMod[]>> map;

    public SyncArmorTagDataPacket(Map<TagKey<Item>, List<ItemAttributeMod[]>> map) {
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
