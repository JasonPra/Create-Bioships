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
    public static final DeferredBlock<LivingCoreBlock> CORE = BLOCKS.register("living_core", () -> new LivingCoreBlock(BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK).lightLevel(s -> 7)));
    public static final DeferredBlock<LivingHelmBlock> HELM = BLOCKS.register("living_helm", () -> new LivingHelmBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final DeferredBlock<LivingThrottleBlock> THROTTLE = BLOCKS.register("living_throttle", () -> new LivingThrottleBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion().lightLevel(state -> state.getValue(LivingThrottleBlock.THROTTLE))));
    public static final DeferredBlock<Block> FLESH = BLOCKS.registerSimpleBlock("living_hull", BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK));
    public static final DeferredBlock<com.simibubi.create.content.contraptions.actors.seat.SeatBlock> PERCH = BLOCKS.register("living_perch", () ->
            new com.simibubi.create.content.contraptions.actors.seat.SeatBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK).noOcclusion(), DyeColor.PURPLE));
    public static final DeferredBlock<Block> TENTACLE = BLOCKS.register("tentacle", () -> new TentacleBlock(BlockBehaviour.Properties.of().strength(1).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final java.util.List<DeferredBlock<NerveControlBlock>> CONTROLS = java.util.Arrays.stream(NerveControlBlock.Command.values())
            .map(command -> BLOCKS.register("nerve_" + command.id(), () -> new NerveControlBlock(command,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.SLIME_BLOCK).noOcclusion()
                            .lightLevel(state -> state.getValue(NerveControlBlock.ACTIVE) ? 6 : 0))))
            .toList();
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem("living_core", CORE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LivingCoreBlockEntity>> CORE_ENTITY = BLOCK_ENTITIES.register("living_core", () -> BlockEntityType.Builder.of(LivingCoreBlockEntity::new, CORE.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("bioships", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_bioships")).icon(() -> CORE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(CORE_ITEM.get())).build());
    public Create_bioships(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
    }
}
