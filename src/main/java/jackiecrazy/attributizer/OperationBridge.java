package jackiecrazy.attributizer;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public enum OperationBridge {
    ADD_VALUE(AttributeModifier.Operation.ADDITION),
    ADD_MULTIPLIED_BASE(AttributeModifier.Operation.MULTIPLY_BASE),
    ADD_MULTIPLIED_TOTAL(AttributeModifier.Operation.MULTIPLY_TOTAL),
    ADDITION(AttributeModifier.Operation.ADDITION),
    MULTIPLY_BASE(AttributeModifier.Operation.MULTIPLY_BASE),
    MULTIPLY_TOTAL(AttributeModifier.Operation.MULTIPLY_TOTAL);

    private AttributeModifier.Operation actual;
    private OperationBridge(AttributeModifier.Operation translate) {
        actual=translate;
    }

    public AttributeModifier.Operation translate() {
        return actual;
    }
}
