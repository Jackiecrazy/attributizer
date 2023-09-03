package jackiecrazy.attributizer;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    // Create a Deferred Register to hold Blocks which will all be registered under the "attributizer" namespace
    public Attributizer() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
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
        public static void mobs(EntityJoinLevelEvent e) {
            if (e.getEntity() instanceof LivingEntity elb) {
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
                ArmorAttributizer.ARCHETYPES.forEach((k, v) -> {
                    if (e.getItemStack().is(k)) {
                        v.forEach((l, m) -> m.forEach(am -> e.addModifier(l, am[e.getSlotType().getIndex()])));
                    }
                });
            }
            if ((e.getSlotType() == EquipmentSlot.OFFHAND)) {
                if (OffhandAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Attribute, List<AttributeModifier>> map = OffhandAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map);
                }
                OffhandAttributizer.ARCHETYPES.forEach((k, v) -> {
                    if (e.getItemStack().is(k)) apply(e, v);
                });
            }
            if ((e.getSlotType() == EquipmentSlot.MAINHAND)) {
                if (MainHandAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                    Map<Attribute, List<AttributeModifier>> map = MainHandAttributizer.MAP.get(e.getItemStack().getItem());
                    apply(e, map);
                }
                MainHandAttributizer.ARCHETYPES.forEach((k, v) -> {
                    if (e.getItemStack().is(k)) apply(e, v);
                });
            }
        }

        private static void apply(ItemAttributeModifierEvent e, Map<Attribute, List<AttributeModifier>> map) {
            map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
        }
    }
}
