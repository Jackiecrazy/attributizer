package jackiecrazy.attributizer;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

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
    public static Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

    public ArmorAttributizer() {
        super(GSON, "attributizer/armor");
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new ArmorAttributizer());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager rm, ProfilerFiller profiler) {
        MAP.clear();
        object.forEach((key, value) -> {
            JsonObject file = value.getAsJsonObject();
            file.entrySet().forEach(entry -> {
                final String name = entry.getKey();
                ResourceLocation i = new ResourceLocation(name);
                Item item = ForgeRegistries.ITEMS.getValue(i);
                if (item == null || item == Items.AIR) {
                    Attributizer.LOGGER.debug(name + " is not a registered item!");
                    return;
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement e : array) {
                    try {
                        JsonObject obj = e.getAsJsonObject();
                        final ResourceLocation attribute = new ResourceLocation(obj.get("attribute").getAsString());
                        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(attribute);
                        if (a == null) {
                            Attributizer.LOGGER.debug(attribute + " is not a registered attribute!");
                            continue;
                        }

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
                        double modify = obj.get("modify").getAsDouble();
                        String type = obj.get("operation").getAsString();
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