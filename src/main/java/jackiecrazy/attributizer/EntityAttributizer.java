package jackiecrazy.attributizer;

import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.injection.At;

import java.util.*;

public class EntityAttributizer extends SimpleJsonResourceReloadListener {
    private static final ResourceLocation DEFAULT=ResourceLocation.fromNamespaceAndPath(Attributizer.MODID, "mob_change");
    public static final UUID MODIFIER = UUID.fromString("a516026a-bee2-4014-bcb6-b6a5776663da");
    public static final Map<Holder<Attribute>, List<AttributeMod>> GLOBALMAP = new HashMap<>();
    public static final Map<EntityType<?>, Map<Holder<Attribute>, List<AttributeMod>>> MAP = new HashMap<>();
    public static final Map<TagKey<EntityType<?>>, Map<Holder<Attribute>, List<AttributeMod>>> ARCHETYPES = new HashMap<>();
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
                        final ResourceLocation attribute = ResourceLocation.parse(obj.get("attribute").getAsString());
                        Optional<Holder.Reference<Attribute>> opt = BuiltInRegistries.ATTRIBUTE.getHolder(attribute);
                        if (opt.isEmpty()) {
                            Attributizer.LOGGER.debug("{} is not a registered attribute!", attribute);
                            continue;
                        }
                        Holder<Attribute> a=opt.get();

                        ResourceLocation uid;
                        try {
                            uid= ResourceLocation.parse(obj.get("resource_location").getAsString());
                        } catch (Exception ignored) {
                            //have to grab the uuid haiyaaa
                            uid = Attributizer.MODIFIERS[4];
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
                            TagKey<EntityType<?>> tag= TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(name));
                            ARCHETYPES.putIfAbsent(tag, new HashMap<>());
                            Map<Holder<Attribute>, List<AttributeMod>> sub = ARCHETYPES.get(tag);
                            sub.putIfAbsent(a, new ArrayList<>());
                            sub.get(a).add(am);
                            ARCHETYPES.put(tag, sub);
                        }
                        else {
                            ResourceLocation i = ResourceLocation.parse(name);
                            if (BuiltInRegistries.ENTITY_TYPE.containsKey(i)) {
                                EntityType<?> mob = BuiltInRegistries.ENTITY_TYPE.get(i);
                                MAP.putIfAbsent(mob, new HashMap<>());
                                Map<Holder<Attribute>, List<AttributeMod>> sub = MAP.get(mob);
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
        ADD_VALUE,
        ADD_MULTIPLIED_BASE,
        ADD_MULTIPLIED_TOTAL,
        ADDITION,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL,
        SET_BASE;
    }

    public static class AttributeMod {
        public ResourceLocation uid;
        public double min;
        public double scale;
        public Operation operation;

        public AttributeMod(ResourceLocation uid, double min, double max, Operation operation) {
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
                i.addPermanentModifier(new AttributeModifier(uid, random, OperationBridge.valueOf(operation.name()).translate()));
            }

        }
    }
}