package jackiecrazy.attributizer;

import com.google.common.collect.Maps;
import com.google.gson.*;
import jackiecrazy.attributizer.networking.SyncItemDataPacket;
import jackiecrazy.attributizer.networking.SyncTagDataPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class OffhandAttributizer extends SimpleJsonResourceReloadListener {
    public static final Map<Item, Map<Holder<Attribute>, List<AttributeModifier>>> MAP = new HashMap<>();
    public static final Map<Item, TagKey<Item>> CACHEMAP = new HashMap<>();
    public static final Map<TagKey<Item>, Map<Holder<Attribute>, List<AttributeModifier>>> ARCHETYPES = new HashMap<>();
    public static Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

    public OffhandAttributizer() {
        super(GSON, "attributizer/offhand");
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new OffhandAttributizer());
    }

    public static void clientDataOverride(Map<Item, Map<Holder<Attribute>, List<AttributeModifier>>> server) {
        MAP.putAll(server);
    }

    public static void clientTagOverride(Map<TagKey<Item>, Map<Holder<Attribute>, List<AttributeModifier>>> server) {
        ARCHETYPES.putAll(server);
    }

    public static void sendItemData(ServerPlayer p) {
        //duplicated removed automatically
        Set<String> paths = MAP.keySet().stream().map(a -> BuiltInRegistries.ITEM.getKey(a).getNamespace()).collect(Collectors.toSet());
        for (String namespace : paths)
            PacketDistributor.sendToPlayer(p, new SyncItemDataPacket(1, Maps.filterEntries(MAP, a -> BuiltInRegistries.ITEM.getKey(a.getKey()).getNamespace().equals(namespace))));
        PacketDistributor.sendToPlayer(p, new SyncTagDataPacket(1, ARCHETYPES));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager rm, ProfilerFiller profiler) {
        MAP.clear();
        ARCHETYPES.clear();
        CACHEMAP.clear();
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
                ResourceLocation i = ResourceLocation.parse(name);
                item = BuiltInRegistries.ITEM.get(i);
                if (item == Items.AIR && !isTag) {
                    //Attributizer.LOGGER.debug(name + " is not a valid registered item!");
                    return;
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement e : array) {
                    try {
                        JsonObject obj = e.getAsJsonObject();
                        final ResourceLocation attribute = ResourceLocation.parse(obj.get("attribute").getAsString());
                        Optional<Holder.Reference<Attribute>> opt = BuiltInRegistries.ATTRIBUTE.getHolder(attribute);
                        if (opt.isEmpty()) {
                            Attributizer.LOGGER.debug(attribute + " is not a registered attribute!");
                            continue;
                        }
                        Holder<Attribute> a=opt.get();

                        double modify = obj.get("modify").getAsDouble();
                        String type = obj.get("operation").getAsString();
                        ResourceLocation uid;
                        try {
                            uid= ResourceLocation.parse(obj.get("resource_location").getAsString());
                        } catch (Exception ignored) {
                            //have to grab the uuid haiyaaa
                            uid = Attributizer.MODIFIERS[5];
                        }

                        AttributeModifier am = new AttributeModifier(uid, modify, OperationBridge.valueOf(type).translate());
                        if(isTag){
                            final TagKey<Item> tag = ItemTags.create(i);
                            ARCHETYPES.putIfAbsent(tag, new HashMap<>());
                            Map<Holder<Attribute>, List<AttributeModifier>> sub = ARCHETYPES.get(tag);
                            sub.putIfAbsent(a, new ArrayList<>());
                            sub.get(a).add(am);
                            ARCHETYPES.put(tag, sub);
                        }else {
                            MAP.putIfAbsent(item, new HashMap<>());
                            Map<Holder<Attribute>, List<AttributeModifier>> sub = MAP.get(item);
                            sub.putIfAbsent(a, new ArrayList<>());
                            sub.get(a).add(am);
                            MAP.put(item, sub);
                        }
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