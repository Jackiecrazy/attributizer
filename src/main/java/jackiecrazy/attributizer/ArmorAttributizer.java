package jackiecrazy.attributizer;

import com.google.common.collect.Maps;
import com.google.gson.*;
import jackiecrazy.attributizer.networking.AttributeChannel;
import jackiecrazy.attributizer.networking.SyncArmorTagDataPacket;
import jackiecrazy.attributizer.networking.SyncItemDataPacket;
import jackiecrazy.attributizer.networking.SyncTagDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

public class ArmorAttributizer extends SimpleJsonResourceReloadListener {
    public static final UUID[] MODIFIERS = {
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553da"),
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553db"),
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553dc"),
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553dd"),
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553de"),
            UUID.fromString("a516026a-bee2-4014-bcb6-b6a5775553df")
    };
    public static final Map<Item, Map<Attribute, List<AttributeModifier>>> MAP = new HashMap<>();
    public static final Map<Item, TagKey<Item>> CACHEMAP = new HashMap<>();
    public static final Map<TagKey<Item>, Map<Attribute, List<AttributeModifier[]>>> ARCHETYPES = new HashMap<>();
    public static Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

    public static void clientDataOverride(Map<Item, Map<Attribute, List<AttributeModifier>>> server) {
        MAP.putAll(server);
    }

    public static void clientTagOverride(Map<TagKey<Item>, Map<Attribute, List<AttributeModifier[]>>> server) {
        ARCHETYPES.putAll(server);
    }

    public static void sendItemData(ServerPlayer p) {
        //duplicated removed automatically
        Set<String> paths = MAP.keySet().stream().map(a -> ForgeRegistries.ITEMS.getKey(a).getNamespace()).collect(Collectors.toSet());
        for (String namespace : paths)
            AttributeChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new SyncItemDataPacket(2, Maps.filterEntries(MAP, a -> ForgeRegistries.ITEMS.getKey(a.getKey()).getNamespace().equals(namespace))));
        //CombatChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new SyncItemDataPacket(new HashMap<>(combatList)));
        AttributeChannel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new SyncArmorTagDataPacket(ARCHETYPES));
    }

    public ArmorAttributizer() {
        super(GSON, "attributizer/armor");
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new ArmorAttributizer());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager rm, ProfilerFiller profiler) {
        MAP.clear();
        CACHEMAP.clear();
        ARCHETYPES.clear();
        object.forEach((key, value) -> {
            JsonObject file = value.getAsJsonObject();
            file.entrySet().forEach(entry -> {
                boolean isTag = false;
                String name = entry.getKey();
                Item item = null;
                if (name.startsWith("#")) {//tag
                    isTag = true;
                    name = name.substring(1);
                    if (!name.contains(":"))
                        name = "attributizer:" + name;
                }
                ResourceLocation i = new ResourceLocation(name);
                item = ForgeRegistries.ITEMS.getValue(i);
                if ((item == null || item == Items.AIR) && !isTag) {
                    Attributizer.LOGGER.debug(name + " is not a registered item!");
                    return;
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement e : array) {
                    //item
                    try {
                        JsonObject obj = e.getAsJsonObject();
                        final ResourceLocation attribute = new ResourceLocation(obj.get("attribute").getAsString());
                        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(attribute);
                        if (a == null) {
                            Attributizer.LOGGER.debug(attribute + " is not a registered attribute!");
                            continue;
                        }

                        double modify = obj.get("modify").getAsDouble();
                        String type = obj.get("operation").getAsString();
                        //tags
                        if (isTag) {
                            AttributeModifier[] insert = new AttributeModifier[MODIFIERS.length];
                            //have to do it for every uuid haiyaa
                            for (int b = 0; b < MODIFIERS.length; b++) {
                                AttributeModifier am = new AttributeModifier(MODIFIERS[b], "attributizer change", modify, AttributeModifier.Operation.valueOf(type));
                                insert[b] = am;
                            }
                            final TagKey<Item> tag = ItemTags.create(i);
                            ARCHETYPES.putIfAbsent(tag, new HashMap<>());
                            Map<Attribute, List<AttributeModifier[]>> sub = ARCHETYPES.get(tag);
                            sub.putIfAbsent(a, new ArrayList<>());
                            sub.get(a).add(insert);
                            ARCHETYPES.put(tag, sub);
                        }
                        //grab uuid
                        UUID uid;
                        try {
                            final String u = obj.get("uuid").getAsString();
                            uid = UUID.fromString(u);
                        } catch (Exception ignored) {
                            //have to grab the uuid haiyaaa
                            if (item instanceof ArmorItem armor) {
                                uid = MODIFIERS[armor.getSlot().getIndex()];
                            } else if (item instanceof ShieldItem) uid = MODIFIERS[5];
                            else uid = MODIFIERS[4];
                        }
                        AttributeModifier am = new AttributeModifier(uid, "attributizer change", modify, AttributeModifier.Operation.valueOf(type));

                        MAP.putIfAbsent(item, new HashMap<>());
                        Map<Attribute, List<AttributeModifier>> sub = MAP.get(item);
                        sub.putIfAbsent(a, new ArrayList<>());
                        sub.get(a).add(am);
                        MAP.put(item, sub);
                    } catch (Exception x) {
                        Attributizer.LOGGER.error("incomplete or malformed json under " + name + "!");
                        x.printStackTrace();
                    }
                }
            });
        });
    }

    public static class AttributeMod {
        public UUID uid;
        public double mod;
        public ResourceLocation attr;


    }
}