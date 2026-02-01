package jackiecrazy.attributizer.curio;

import jackiecrazy.attributizer.ItemAttributeMod;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

import java.util.List;
import java.util.Objects;

public class CurioEventHandler {
    @SubscribeEvent
    public static void handle(CurioAttributeModifierEvent e) {
        if ((!e.getSlotContext().cosmetic())) {
            if (CurioAttributizer.MAP.containsKey(e.getItemStack().getItem())) {//presumably this is the correct equipment slot
                List<ItemAttributeMod> map = CurioAttributizer.MAP.get(e.getItemStack().getItem());
                apply(e, map);
            } else if (CurioAttributizer.CACHEMAP.containsKey(e.getItemStack().getItem()))
                apply(e, CurioAttributizer.ARCHETYPES.get(CurioAttributizer.CACHEMAP.get(e.getItemStack().getItem())));
            else
                CurioAttributizer.ARCHETYPES.entrySet().stream().filter(k -> e.getItemStack().is(k.getKey())).findFirst().ifPresent((k) -> {
                    apply(e, k.getValue());
                    CurioAttributizer.CACHEMAP.put(e.getItemStack().getItem(), k.getKey());
                });
        }
    }

    private static void apply(CurioAttributeModifierEvent i, List<ItemAttributeMod> map) {
        map.forEach(mod -> {
            if (mod.baked != null)
                i.addModifier(mod.attribute, mod.baked);
            //find and delete the old attribute modifier
            i.getModifiers().get(mod.attribute).stream().filter(a -> a.getId().equals(mod.uuid)).findFirst().ifPresent(a -> i.removeModifier(mod.attribute, a));
            if (Objects.requireNonNull(mod.operation) == ItemAttributeMod.Operation.EQUALIZE) {
                //go down the list of original attributes and calculate the current addition
                double original = i.getOriginalModifiers().get(mod.attribute).stream().filter(Objects::nonNull)
                        .filter(a -> !a.getId().equals(mod.uuid) && a.getOperation() == AttributeModifier.Operation.ADDITION)
                        .mapToDouble(AttributeModifier::getAmount).sum();
                //apply precisely enough to counteract it
                AttributeModifier counteract = new AttributeModifier(mod.uuid, "attributizer change", -original + mod.modify, AttributeModifier.Operation.ADDITION);
                //can't cache this
                i.addModifier(mod.attribute, counteract);
            } else if (i.getModifiers().get(mod.attribute).stream().filter(Objects::nonNull).noneMatch(a -> a.getId() == mod.uuid)) {
                AttributeModifier am = new AttributeModifier(mod.uuid, "attributizer change", mod.modify, AttributeModifier.Operation.valueOf(mod.operation.name()));
                mod.baked = am;
                i.addModifier(mod.attribute, am);
            }
        });
        //map.forEach((k, v) -> v.forEach(am -> e.addModifier(k, am)));
    }
}
