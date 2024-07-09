package jackiecrazy.attributizer;

import com.mojang.logging.LogUtils;
import jackiecrazy.attributizer.networking.SyncArmorTagDataPacket;
import jackiecrazy.attributizer.networking.SyncItemDataPacket;
import jackiecrazy.attributizer.networking.SyncTagDataPacket;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Attributizer.MODID)
public class Attributizer {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "attributizer";
    public static final ResourceLocation[] MODIFIERS = {
            ResourceLocation.fromNamespaceAndPath(MODID, "head_change"),
            ResourceLocation.fromNamespaceAndPath(MODID, "chest_change"),
            ResourceLocation.fromNamespaceAndPath(MODID, "legs_change"),
            ResourceLocation.fromNamespaceAndPath(MODID, "feet_change"),
            ResourceLocation.fromNamespaceAndPath(MODID, "main_hand_change"),
            ResourceLocation.fromNamespaceAndPath(MODID, "off_hand_change"),
    };
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Map<Item, Map<Holder<Attribute>, List<AttributeModifier>>> MAP = new HashMap<>();

    public Attributizer(IEventBus bus, ModContainer container) {
        bus.addListener(this::setup);
        bus.addListener(this::registerPayloads);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SyncItemDataPacket.MAIN,
                SyncItemDataPacket.STREAM_CODEC,
                SyncItemDataPacket.ClientPayloadHandler::handleData
        );
        registrar.playToClient(
                SyncTagDataPacket.MAIN,
                SyncTagDataPacket.STREAM_CODEC,
                SyncTagDataPacket.ClientPayloadHandler::handleData
        );
        registrar.playToClient(
                SyncArmorTagDataPacket.MAIN,
                SyncArmorTagDataPacket.STREAM_CODEC,
                SyncArmorTagDataPacket.ClientPayloadHandler::handleData
        );
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
    }

    @EventBusSubscriber
    public static class ModBusEvents {
        private static void apply(ItemAttributeModifierEvent e, Map<Holder<Attribute>, List<AttributeModifier>> map, EquipmentSlot slot) {
            map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am, EquipmentSlotGroup.bySlot(slot))));
        }

        @SubscribeEvent
        public static void items(ItemAttributeModifierEvent e) {
            if (e.getItemStack().isEmpty()) return;
            //armor
            if ((!e.getDefaultModifiers().modifiers().isEmpty())) {
                if (ArmorAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Holder<Attribute>, List<AttributeModifier>> map = ArmorAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map, ArmorAttributizer.getEquipmentSlot(e.getItemStack().getItem()));
                }
                //tag based armor
                else if (ArmorAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                    ArmorAttributizer.CACHEMAP.computeIfPresent(e.getItemStack().getItem(), (item, tag) -> {
                        ArmorAttributizer.ARCHETYPES.computeIfPresent(tag, (tag1, attr) -> {
                                    attr.forEach((l, m) -> m.forEach(am -> e.addModifier(l, am.get(ArmorAttributizer.getEquipmentSlot(e.getItemStack().getItem()).getIndex()), EquipmentSlotGroup.bySlot(ArmorAttributizer.getEquipmentSlot(e.getItemStack().getItem())))));
                                    return attr;
                                }
                        );
                        return tag;
                    });
                else
                    ArmorAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent(k -> {
                        if (e.getItemStack().is(k.getKey())) {
                            k.getValue().forEach((l, m) -> m.forEach(am -> e.addModifier(l, am.get(ArmorAttributizer.getEquipmentSlot(e.getItemStack().getItem()).getIndex()), EquipmentSlotGroup.bySlot(ArmorAttributizer.getEquipmentSlot(e.getItemStack().getItem())))));
                            ArmorAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                        }
                    });
            }

            if (OffhandAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                Map<Holder<Attribute>, List<AttributeModifier>> map = OffhandAttributizer.MAP.get(e.getItemStack().getItem());
                apply(e, map, EquipmentSlot.OFFHAND);
            } else if (OffhandAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                apply(e,
                        OffhandAttributizer.ARCHETYPES.get(
                                OffhandAttributizer.CACHEMAP.get(
                                        e.getItemStack().getItem())), EquipmentSlot.OFFHAND);
            else
                OffhandAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent(k -> {
                    apply(e, k.getValue(), EquipmentSlot.OFFHAND);
                    OffhandAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                });

            if (MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                Map<Holder<Attribute>, List<AttributeModifier>> map = MAP.get(e.getItemStack().getItem());
                apply(e, map, EquipmentSlot.MAINHAND);
            } else if (MainHandAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                apply(e, MainHandAttributizer.ARCHETYPES.get(MainHandAttributizer.CACHEMAP.get(e.getItemStack().getItem())), EquipmentSlot.MAINHAND);
            else
                MainHandAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent((k) -> {
                    apply(e, k.getValue(), EquipmentSlot.MAINHAND);
                    MainHandAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                });
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) {
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
                    Map<Holder<Attribute>, List<EntityAttributizer.AttributeMod>> map = EntityAttributizer.MAP.get(elb.getType());
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
                //max health resetting
                if (elb.getHealth() < elb.getMaxHealth()) {
                    elb.setHealth(elb.getMaxHealth());
                }
            }
        }

        @SubscribeEvent
        public static void onJsonListener(AddReloadListenerEvent event) {
            ArmorAttributizer.register(event);
            MainHandAttributizer.register(event);
            OffhandAttributizer.register(event);
            EntityAttributizer.register(event);
        }

        @SubscribeEvent
        public static void reload(OnDatapackSyncEvent e) {
            for (ServerPlayer sp : e.getPlayerList().getPlayers()) {
                MainHandAttributizer.sendItemData(sp);
                OffhandAttributizer.sendItemData(sp);
                ArmorAttributizer.sendItemData(sp);
            }
        }
    }
}
