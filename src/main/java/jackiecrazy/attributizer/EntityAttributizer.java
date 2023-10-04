package jackiecrazy.attributizer;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class EntityAttributizer extends SimpleJsonResourceReloadListener {
    public static final UUID MODIFIER = UUID.fromString("a516026a-bee2-4014-bcb6-b6a5776663da");
    public static final Map<Attribute, List<AttributeMod>> GLOBALMAP = new HashMap<>();
    public static final Map<EntityType<?>, Map<Attribute, List<AttributeMod>>> MAP = new HashMap<>();
    public static final Map<TagKey<EntityType<?>>, Map<Attribute, List<AttributeMod>>> ARCHETYPES = new HashMap<>();
    public static Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

    public EntityAttributizer() {
        super(GSON, "attributizer/entity");
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new EntityAttributizer());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager rm, ProfilerFiller profiler) {
        MAP.clear();
        object.forEach((key, value) -> {
            JsonObject file = value.getAsJsonObject();
            file.entrySet().forEach(entry -> {
                String name = entry.getKey();
                boolean isTag=false;
                if (name.startsWith("#")) {//tag
                    isTag = true;
                    name = name.substring(1);
                    if (!name.contains(":"))
                        name = "attributizer:" + name;
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement e : array) {
                    try {
                        JsonObject obj = e.getAsJsonObject();
                        final ResourceLocation attribute = new ResourceLocation(obj.get("attribute").getAsString());
                        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(attribute);
                        if (a == null) {
                            continue;
                        }

                        UUID uid;
                        try {
                            final String u = obj.get("uuid").getAsString();
                            uid = UUID.fromString(u);
                        } catch (Exception ignored) {
                            //default uuid
                            uid = MODIFIER;
                        }
                        double min=0, max=0;
                        if (obj.has("min")) {
                            min = obj.get("min").getAsDouble();
                            max = obj.get("max").getAsDouble();
                        } else if (obj.has("modify")) {
                            min = max = obj.get("modify").getAsDouble();
                        }
                        String type = obj.get("operation").getAsString();
                        AttributeMod am = new AttributeMod(uid, min, max, Operation.valueOf(type));
                        if(name.equals("all")){
                            GLOBALMAP.putIfAbsent(a, new ArrayList<>());
                            GLOBALMAP.get(a).add(am);
                        }
                        else if(isTag){
                            TagKey<EntityType<?>> tag= TagKey.create(Registry.ENTITY_TYPE_REGISTRY, new ResourceLocation(name));
                            ARCHETYPES.putIfAbsent(tag, new HashMap<>());
                            Map<Attribute, List<AttributeMod>> sub = ARCHETYPES.get(tag);
                            sub.putIfAbsent(a, new ArrayList<>());
                            sub.get(a).add(am);
                            ARCHETYPES.put(tag, sub);
                        }
                        else {
                            ResourceLocation i = new ResourceLocation(name);
                            EntityType<?> mob = ForgeRegistries.ENTITY_TYPES.getValue(i);
                            if (mob != null) {
                                MAP.putIfAbsent(mob, new HashMap<>());
                                Map<Attribute, List<AttributeMod>> sub = MAP.get(mob);
                                sub.putIfAbsent(a, new ArrayList<>());
                                sub.get(a).add(am);
                                MAP.put(mob, sub);
                            }
                        }

                    } catch (Exception x) {
                        Attributizer.LOGGER.error("incomplete or malformed json under " + name + "!");
                        x.printStackTrace();
                    }
                }
            });
        });
    }

    public enum Operation{
        ADDITION,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL,
        SET_BASE;
    }

    public static class AttributeMod {
        public UUID uid;
        public double min;
        public double scale;
        public Operation operation;

        public AttributeMod(UUID uid, double min, double max, Operation operation) {
            this.uid = uid;
            this.min = min;
            this.scale = max - min;
            this.operation = operation;
        }

        public void applyModifier(AttributeInstance i) {
            final double random = min + Math.random() * scale;
            if (Objects.requireNonNull(operation) == Operation.SET_BASE) {
                i.setBaseValue(random);
            } else if(i.getModifier(uid)==null){
                i.addPermanentModifier(new AttributeModifier(uid, "attributizer change", random, AttributeModifier.Operation.fromValue(operation.ordinal())));
            }

        }
    }
}