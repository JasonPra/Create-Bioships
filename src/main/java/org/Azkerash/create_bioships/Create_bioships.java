package org.Azkerash.create_bioships;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;

@Mod(Create_bioships.MODID)
public class Create_bioships {
    public static final String MODID = "create_bioships";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredBlock<LivingCoreBlock> CORE = BLOCKS.register("living_core", () -> new LivingCoreBlock(BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK).lightLevel(s -> s.getValue(LivingCoreBlock.ACTIVE) ? s.getValue(LivingCoreBlock.BLINK) ? 3 : 7 : 1)));
    // Legacy prototype IDs remain registered; the new body uses Simulated's native helm.
    public static final DeferredBlock<LivingHelmBlock> HELM = BLOCKS.register("living_helm", () -> new LivingHelmBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<LivingThrottleBlock> THROTTLE = BLOCKS.register("living_throttle", () -> new LivingThrottleBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<LivingThrottleBlock> VERTICAL_TRIM = BLOCKS.register("vertical_trim", () -> new LivingThrottleBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion(), LivingThrottleBlock.Kind.VERTICAL));
    public static final DeferredBlock<PulseLightBlock> PULSE_LIGHT = BLOCKS.register("pulse_light", () -> new PulseLightBlock(BlockBehaviour.Properties.of().strength(1).sound(SoundType.SLIME_BLOCK).noOcclusion().lightLevel(state -> state.getValue(PulseLightBlock.LIT) ? 13 : 3)));
    public static final DeferredBlock<Block> MOUTH = BLOCKS.registerSimpleBlock("living_mouth", BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK).noOcclusion());
    public static final DeferredBlock<Block> BUOYANCY_ORGAN = BLOCKS.registerSimpleBlock("buoyancy_organ", BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).lightLevel(state -> 9));
    public static final DeferredBlock<Block> FLESH = BLOCKS.registerSimpleBlock("living_hull", BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK));
    public static final DeferredBlock<CaptainSeatBlock> PERCH = BLOCKS.register("living_perch", () -> new CaptainSeatBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<CarapaceBlock> CARAPACE = BLOCKS.register("living_carapace", () -> new CarapaceBlock(BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<TransparentBlock> MEMBRANE = BLOCKS.register("living_membrane", () -> new TransparentBlock(BlockBehaviour.Properties.of().strength(1.2f).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<LivingDoorBlock> DOOR = BLOCKS.register("living_door", () -> new LivingDoorBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<TentacleBlock> TENTACLE = BLOCKS.register("tentacle", () -> new TentacleBlock(BlockBehaviour.Properties.of().strength(1).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final java.util.List<DeferredBlock<NerveControlBlock>> CONTROLS = java.util.Arrays.stream(NerveControlBlock.Command.values())
            .map(command -> BLOCKS.register("nerve_" + command.id(), () -> new NerveControlBlock(command,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.SLIME_BLOCK).noOcclusion()
                            .lightLevel(state -> state.getValue(NerveControlBlock.ACTIVE) ? 6 : 0))))
            .toList();
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem("living_core", CORE);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> DOOR_ITEM = ITEMS.register("living_door", () -> new net.minecraft.world.item.DoubleHighBlockItem(DOOR.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LivingCoreBlockEntity>> CORE_ENTITY = BLOCK_ENTITIES.register("living_core", () -> BlockEntityType.Builder.of(LivingCoreBlockEntity::new, CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentacleBlockEntity>> TENTACLE_ENTITY = BLOCK_ENTITIES.register("tentacle", () -> BlockEntityType.Builder.of(TentacleBlockEntity::new, TENTACLE.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("bioships", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_bioships")).icon(() -> CORE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> { output.accept(CORE_ITEM.get()); output.accept(DOOR_ITEM.get()); }).build());
    public Create_bioships(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(BioshipNetwork::register);
        com.simibubi.create.api.contraption.BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                BioshipIntegration.bound(BioshipIntegration.containing(level, pos))
                        ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.FAIL
                        : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS);
    }
}
