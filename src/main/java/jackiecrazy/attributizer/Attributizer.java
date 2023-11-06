package jackiecrazy.attributizer;

import com.mojang.logging.LogUtils;
import jackiecrazy.attributizer.networking.AttributeChannel;
import jackiecrazy.attributizer.networking.SyncArmorTagDataPacket;
import jackiecrazy.attributizer.networking.SyncItemDataPacket;
import jackiecrazy.attributizer.networking.SyncTagDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Attributizer.MODID)
public class Attributizer {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "attributizer";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public Attributizer() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
        int index = 0;
        AttributeChannel.INSTANCE.registerMessage(index++, SyncItemDataPacket.class, new SyncItemDataPacket.Encoder(), new SyncItemDataPacket.Decoder(), new SyncItemDataPacket.Handler());
        AttributeChannel.INSTANCE.registerMessage(index++, SyncTagDataPacket.class, new SyncTagDataPacket.Encoder(), new SyncTagDataPacket.Decoder(), new SyncTagDataPacket.Handler());
        AttributeChannel.INSTANCE.registerMessage(index++, SyncArmorTagDataPacket.class, new SyncArmorTagDataPacket.Encoder(), new SyncArmorTagDataPacket.Decoder(), new SyncArmorTagDataPacket.Handler());

    }

    @Mod.EventBusSubscriber
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onJsonListener(AddReloadListenerEvent event) {
            ArmorAttributizer.register(event);
            MainHandAttributizer.register(event);
            OffhandAttributizer.register(event);
            EntityAttributizer.register(event);
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) {
                MainHandAttributizer.sendItemData(sp);
                OffhandAttributizer.sendItemData(sp);
                ArmorAttributizer.sendItemData(sp);
            }
        }

        @SubscribeEvent
        public static void reload(OnDatapackSyncEvent e) {
            for (ServerPlayer sp : e.getPlayerList().getPlayers()) {
                MainHandAttributizer.sendItemData(sp);
                OffhandAttributizer.sendItemData(sp);
                ArmorAttributizer.sendItemData(sp);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void mobs(EntityJoinLevelEvent e) {
            if (e.getEntity() instanceof LivingEntity elb && !e.loadedFromDisk()) {
                if (!(elb instanceof Player))
                    EntityAttributizer.GLOBALMAP.forEach((k, v) -> v.forEach(am -> {
                        AttributeInstance ai = elb.getAttribute(k);
                        if (ai != null) {
                            am.applyModifier(ai);
                        }
                    }));
                if (EntityAttributizer.MAP.containsKey(e.getEntity().getType())) {
                    Map<Attribute, List<EntityAttributizer.AttributeMod>> map = EntityAttributizer.MAP.get(elb.getType());
                    map.forEach((k, v) -> v.forEach(am -> {
                        AttributeInstance ai = elb.getAttribute(k);
                        if (ai != null) {
                            am.applyModifier(ai);
                        }
                    }));
                }
                EntityAttributizer.ARCHETYPES.forEach((k, v) -> {
                    if (elb.getType().is(k)) {
                        v.forEach((a, b) -> b.forEach(am -> {
                            AttributeInstance ai = elb.getAttribute(a);
                            if (ai != null) {
                                am.applyModifier(ai);
                            }
                        }));
                    }
                });
            }
        }

        @SubscribeEvent
        public static void items(ItemAttributeModifierEvent e) {
            if (e.getItemStack().isEmpty()) return;
            //armor
            if ((!e.getOriginalModifiers().isEmpty())) {
                if (ArmorAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Attribute, List<AttributeModifier>> map = ArmorAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map);
                }
                //tag based armor
                else if (ArmorAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                    ArmorAttributizer.CACHEMAP.computeIfPresent(e.getItemStack().getItem(), (item, tag) -> {
                        ArmorAttributizer.ARCHETYPES.computeIfPresent(tag, (tag1, attr) -> {
                                    attr.forEach((l, m) -> m.forEach(am -> e.addModifier(l, am[e.getSlotType().getIndex()])));
                                    return attr;
                                }
                        );
                        return tag;
                    });
                else ArmorAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent(k -> {
                    if (e.getItemStack().is(k.getKey())) {
                        k.getValue().forEach((l, m) -> m.forEach(am -> e.addModifier(l, am[e.getSlotType().getIndex()])));
                        ArmorAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                    }
                });
            }
            if ((e.getSlotType() == EquipmentSlot.OFFHAND)) {
                if (OffhandAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Attribute, List<AttributeModifier>> map = OffhandAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map);
                }
                else if (OffhandAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                    apply(e,
                            OffhandAttributizer.ARCHETYPES.get(
                            OffhandAttributizer.CACHEMAP.get(
                                    e.getItemStack().getItem())));
                else OffhandAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent(k -> {
                    apply(e, k.getValue());
                    OffhandAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                });
            }
            if ((e.getSlotType() == EquipmentSlot.MAINHAND)) {
                if (MainHandAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Attribute, List<AttributeModifier>> map = MainHandAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map);
                }
                else if (MainHandAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                    apply(e, MainHandAttributizer.ARCHETYPES.get(MainHandAttributizer.CACHEMAP.get(e.getItemStack().getItem())));
                else
                    MainHandAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent((k) -> {
                        apply(e, k.getValue());
                        MainHandAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                    });
            }
        }

        private static void apply(ItemAttributeModifierEvent e, Map<Attribute, List<AttributeModifier>> map) {
            map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
        }
    }
}
