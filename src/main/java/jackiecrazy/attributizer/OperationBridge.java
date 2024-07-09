package jackiecrazy.attributizer;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public enum OperationBridge {
    ADD_VALUE(AttributeModifier.Operation.ADD_VALUE),
    ADD_MULTIPLIED_BASE(AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
    ADD_MULTIPLIED_TOTAL(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    ADDITION(AttributeModifier.Operation.ADD_VALUE),
    MULTIPLY_BASE(AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
    MULTIPLY_TOTAL(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private AttributeModifier.Operation actual;
    private OperationBridge(AttributeModifier.Operation translate) {
        actual=translate;
    }

    public AttributeModifier.Operation translate() {
        return actual;
    }
}
