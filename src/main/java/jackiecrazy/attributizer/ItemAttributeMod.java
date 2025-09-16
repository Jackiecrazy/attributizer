package jackiecrazy.attributizer;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.ItemAttributeModifierEvent;

import java.util.Objects;
import java.util.UUID;

public class ItemAttributeMod {
    public Attribute attribute;
    public UUID uuid;
    public double modify;
    public ItemAttributeMod.Operation operation;
    public AttributeModifier baked = null;

    public ItemAttributeMod(Attribute attribute, UUID uuid, double modify, ItemAttributeMod.Operation operation) {
        this.attribute = attribute;
        this.uuid = uuid;
        this.modify = modify;
        this.operation = operation;
        if (attribute == Attributes.ATTACK_DAMAGE && operation == Operation.EQUALIZE)
            this.uuid = Item.BASE_ATTACK_DAMAGE_UUID;
        if (attribute == Attributes.ATTACK_SPEED && operation == Operation.EQUALIZE) {
            this.uuid = Item.BASE_ATTACK_SPEED_UUID;
        }
    }

    public void applyModifier(ItemAttributeModifierEvent i) {
        if (baked != null)
            i.addModifier(attribute, baked);
        //find and delete the old attribute modifier
        i.getModifiers().get(attribute).stream().filter(a -> a.getId().equals(uuid)).findFirst().ifPresent(a -> i.removeModifier(attribute, a));
        if (Objects.requireNonNull(operation) == Operation.EQUALIZE) {
            //go down the list of original attributes and calculate the current addition
            double original = i.getOriginalModifiers().get(attribute).stream().filter(Objects::nonNull)
                    .filter(a -> !a.getId().equals(uuid) && a.getOperation() == AttributeModifier.Operation.ADDITION)
                    .mapToDouble(AttributeModifier::getAmount).sum();
            //apply precisely enough to counteract it
            AttributeModifier counteract = new AttributeModifier(uuid, "attributizer change", -original + modify, AttributeModifier.Operation.ADDITION);
            //can't cache this
            i.addModifier(attribute, counteract);
        } else if (i.getModifiers().get(attribute).stream().filter(Objects::nonNull).noneMatch(a -> a.getId() == uuid)) {
            AttributeModifier am = new AttributeModifier(uuid, "attributizer change", modify, AttributeModifier.Operation.valueOf(operation.name()));
            baked = am;
            i.addModifier(attribute, am);
        }

    }

    public enum Operation {
        ADDITION,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL,
        EQUALIZE
    }
}
