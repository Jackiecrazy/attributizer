package jackiecrazy.attributizer;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
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
        }

        @SubscribeEvent
        public static void items(ItemAttributeModifierEvent e) {
            if (e.getItemStack().isEmpty()) return;
            if (ArmorAttributizer.MAP.containsKey(e.getItemStack().getItem()) && (!e.getOriginalModifiers().isEmpty())) {//presumably this is the correct equipment slot
                Map<Attribute, List<AttributeModifier>> map = ArmorAttributizer.MAP.get(e.getItemStack().getItem());
                map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
            }
            if (OffhandAttributizer.MAP.containsKey(e.getItemStack().getItem()) && (e.getSlotType() == EquipmentSlot.OFFHAND)) {//presumably this is the correct equipment slot
                Map<Attribute, List<AttributeModifier>> map = OffhandAttributizer.MAP.get(e.getItemStack().getItem());
                map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
            }
            if (MainHandAttributizer.MAP.containsKey(e.getItemStack().getItem()) && (e.getSlotType() == EquipmentSlot.MAINHAND)) {//presumably this is the correct equipment slot
                Map<Attribute, List<AttributeModifier>> map = MainHandAttributizer.MAP.get(e.getItemStack().getItem());
                map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
            }
        }
    }
}
