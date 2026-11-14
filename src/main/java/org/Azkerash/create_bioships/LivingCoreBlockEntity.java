package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class LivingCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private static final TagKey<Item> MEAT = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "biomass"));
    private static final TagKey<Item> LEVITITE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite"));
    private final BioshipNeeds needs = new BioshipNeeds();
    private boolean awakened;
    private int stage, growthIndex, metabolismTicks;
    private long lastServerTick = Long.MIN_VALUE;
    private boolean resting;
    private net.minecraft.world.entity.item.ItemEntity foodTarget;
    private volatile FlightIntent flight = new FlightIntent(false, Vec3.ZERO, 0);
    private record FlightIntent(boolean lift, Vec3 velocity, double turn) {}
    public LivingCoreBlockEntity(BlockPos pos, BlockState state) { super(Create_bioships.CORE_ENTITY.get(), pos, state); }

    private ServerSubLevel ship() {
        if (!(level instanceof ServerLevel server)) return null;
        var container = SubLevelContainer.getContainer(server);
        if (container == null || !container.inBounds(worldPosition)) return null;
        var plot = container.getPlot(new ChunkPos(worldPosition));
        return plot != null && plot.getSubLevel() instanceof ServerSubLevel ship ? ship : null;
    }
    // One organism controls each assembled structure, even if players assemble multiple cores.
    private boolean primary(ServerSubLevel ship) {
        for (var actor : ship.getPlot().getBlockEntityActors())
            if (actor instanceof LivingCoreBlockEntity other && other != this && other.getBlockPos().compareTo(worldPosition) < 0) return false;
        return true;
    }
    public void interact(Player player, ItemStack stack) {
        ServerSubLevel ship = ship();
        if (ship == null) { message(player, "message.create_bioships.dormant"); return; }
        if (!primary(ship)) { message(player, "message.create_bioships.secondary"); return; }
        if (stack.is(MEAT)) {
            if (needs.feedMeat(stack.is(Items.ROTTEN_FLESH) ? 40 : 70, stage != 0)) {
                stack.shrink(1); setChanged();
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.65f);
            }
        } else if (stack.is(LEVITITE)) {
            if (needs.feedLevitite()) { stack.shrink(1); setChanged(); }
        } else if (stack.isEmpty() && !player.isShiftKeyDown() && stage == 2) {
            resting = !resting; setChanged();
            message(player, resting ? "message.create_bioships.resting" : "message.create_bioships.controls_enabled");
            return;
        }
        status(player);
    }
    private void status(Player player) {
        message(player, "message.create_bioships.status", Component.translatable("stage.create_bioships." + stage),
            needs.biomass(), BioshipNeeds.BIOMASS_REQUIRED, needs.levitite(), BioshipNeeds.LEVITITE_REQUIRED,
            needs.satiety(), BioshipNeeds.MAX_SATIETY, Component.translatable("mood.create_bioships." + needs.mood()), needs.trust());
        if (stage == 0 && needs.readyToGrow() && !roomToGrow()) message(player, "message.create_bioships.obstructed");
    }
    private static void message(Player player, String key, Object... args) { player.displayClientMessage(Component.translatable(key, args), true); }
    public void mistreat() { if (ship() != null) { needs.mistreat(); setChanged(); } }
    private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos) {
        if (!ship.getPlot().contains(new ChunkPos(pos)) || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
        // Plot coordinates are storage coordinates; also check the physical world location.
        Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(pos));
        return level.getBlockState(BlockPos.containing(physical)).isAir();
    }
    private boolean roomToGrow() {
        ServerSubLevel ship = ship();
        if (ship == null) return false;
        for (var part : DinghyGrowth.body()) {
            BlockPos pos = worldPosition.offset(part.offset());
            if (!clearForGrowth(ship, pos)) return false;
        }
        return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core) { core.serverTick(); }
    @Override public void sable$tick(ServerSubLevel ship) { serverTick(); }
    private void serverTick() {
        if (level == null || level.getGameTime() == lastServerTick) return;
        lastServerTick = level.getGameTime();
        ServerSubLevel ship = ship();
        if (ship == null || !primary(ship)) { flight = new FlightIntent(false, Vec3.ZERO, 0); return; }
        if (!awakened) { awakened = true; ship.setName("Living Bioship"); setChanged(); }
        if (++metabolismTicks >= 20) { metabolismTicks = 0; needs.metabolize(); setChanged(); }
        if (stage == 0 && needs.readyToGrow() && level.getGameTime() % 20 == 0 && roomToGrow()) { stage = 1; setChanged(); }
        if (stage == 1 && level.getGameTime() % 5 == 0) {
            var parts = DinghyGrowth.body();
            if (growthIndex < parts.size()) {
                var part = parts.get(growthIndex); BlockPos target = worldPosition.offset(part.offset());
                // Pause rather than overwriting a block placed in the growth space.
                if (clearForGrowth(ship, target) && level.setBlock(target, part.state(), 3)) { growthIndex++; setChanged(); }
            }
            if (growthIndex == parts.size()) { stage = 2; setChanged(); }
        }
        Vec3 desired = Vec3.ZERO;
        double turn = 0;
        if (stage == 2 && !resting) {
            BlockState throttleState = level.getBlockState(worldPosition.offset(-1, 1, 1));
            double throttle = throttleState.is(Create_bioships.THROTTLE.get()) ? throttleState.getValue(LivingThrottleBlock.THROTTLE) / 5.0 : 0;
            BlockState helmState = level.getBlockState(worldPosition.offset(0, 1, -1));
            turn = helmState.is(Create_bioships.HELM.get()) ? (3 - helmState.getValue(LivingHelmBlock.STEERING)) / 3.0 : 0;
            double forward = 1, side = 0, vertical = 0;
            for (var control : Create_bioships.CONTROLS) {
                var block = control.get();
                BlockPos controlPos = worldPosition.offset(block.command.offset);
                BlockState controlState = level.getBlockState(controlPos);
                if (!controlState.is(block)) continue;
                if (controlState.getValue(NerveControlBlock.ACTIVE) || level.hasNeighborSignal(controlPos)) {
                    if (block.command == NerveControlBlock.Command.BACK) forward = -1;
                    side += block.command.side; vertical += block.command.vertical;
                }
            }
            Vec3 local = new Vec3(side * 1.5, vertical * 1.5, -forward * 3 * throttle);
            if (local.lengthSqr() > 9) local = local.normalize().scale(3);
            double response = needs.obedience();
            // Predictable hesitation when grumpy; lift is retained even while refusing commands.
            if (needs.mood().equals("grumpy") && level.getGameTime() % 100 < 40) response = 0;
            desired = ship.logicalPose().transformNormal(local).scale(response);
            turn *= response * (0.25 + 0.75 * throttle);
        }
        if (desired.lengthSqr() == 0 && turn == 0) {
            desired = resting ? Vec3.ZERO : forage(ship);
            if (desired.lengthSqr() == 0 && !resting)
                desired = new Vec3(0, Math.sin(level.getGameTime() / 35.0) * 0.08, 0);
        }
        if (stage < 2 && needs.levitite() > 0) {
            // A newly awakened core may have landed before its first meal.
            // Rise gently until the future tendrils have clearance from terrain.
            Vec3 center = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition));
            BlockPos physical = BlockPos.containing(center);
            if (level.getBlockState(physical.above()).isAir()) {
                for (int depth = 1; depth <= 4; depth++) {
                    if (!level.getBlockState(physical.below(depth)).isAir()) { desired = new Vec3(0, 0.6, 0); break; }
                }
            }
        }
        flight = new FlightIntent(needs.levitite() > 0, desired, turn);
    }
    private Vec3 forage(ServerSubLevel ship) {
        if (needs.satiety() >= 240) { foodTarget = null; return Vec3.ZERO; }
        Vec3 mouth = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition).add(0, 1.5, 0));
        if (foodTarget != null && (!foodTarget.isAlive() || !foodTarget.getItem().is(MEAT) || foodTarget.position().distanceToSqr(mouth) > 64)) foodTarget = null;
        if (foodTarget == null && level.getGameTime() % 20 == 0) {
            double closest = 64;
            for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(mouth, mouth).inflate(8), entity -> entity.getItem().is(MEAT))) {
                double distance = item.position().distanceToSqr(mouth);
                if (distance < closest) { foodTarget = item; closest = distance; }
            }
        }
        if (foodTarget == null) return Vec3.ZERO;
        Vec3 food = foodTarget.position().add(0, 0.2, 0);
        var sight = level.clip(new net.minecraft.world.level.ClipContext(mouth, food,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, foodTarget));
        if (sight.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return Vec3.ZERO;
        if (food.distanceToSqr(mouth) < 4 && level.getGameTime() % 40 == 0) {
            ItemStack stack = foodTarget.getItem();
            if (needs.feedMeat(stack.is(Items.ROTTEN_FLESH) ? 40 : 70, stage != 0)) {
                stack.shrink(1);
                if (stack.isEmpty()) foodTarget.discard();
                setChanged();
            }
        }
        Vec3 scent = food.subtract(mouth);
        return scent.lengthSqr() < 0.25 ? Vec3.ZERO : scent.normalize().scale(0.5);
    }
    @Override public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt) {
        FlightIntent intent = flight;
        if (!intent.lift() || !body.isValid()) return;
        double mass = ship.getMassTracker().getMass();
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0) return;
        var velocity = body.getLinearVelocity();
        Vector3d acceleration = new Vector3d(intent.velocity().x, intent.velocity().y, intent.velocity().z)
            .sub(velocity).mul(2).sub(DimensionPhysicsData.getGravity(ship.getLevel()));
        Vector3d up = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
        Vector3d correction = up.cross(new Vector3d(0, 1, 0), new Vector3d()).mul(3);
        correction.add(0, intent.turn() * 1.2, 0).sub(new Vector3d(body.getAngularVelocity()).mul(1.8));
        // Apply muscle acceleration and stabilization together through Sable's
        // velocity interface, after computing gravity compensation for this step.
        body.addLinearAndAngularVelocity(acceleration.mul(dt), correction.mul(dt));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Resting", resting); tag.putBoolean("Awakened", awakened); tag.putInt("Stage", stage); tag.putInt("GrowthIndex", growthIndex);
        tag.putInt("Biomass", needs.biomass()); tag.putInt("Levitite", needs.levitite()); tag.putInt("Satiety", needs.satiety()); tag.putInt("Trust", needs.trust());
        tag.putInt("MetabolismTicks", metabolismTicks);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        resting = tag.getBoolean("Resting"); awakened = tag.getBoolean("Awakened"); stage = Math.clamp(tag.getInt("Stage"), 0, 2);
        growthIndex = Math.clamp(tag.getInt("GrowthIndex"), 0, DinghyGrowth.body().size());
        needs.restore(tag.getInt("Biomass"), tag.getInt("Levitite"), tag.contains("Satiety") ? tag.getInt("Satiety") : 600, tag.contains("Trust") ? tag.getInt("Trust") : 60);
        metabolismTicks = Math.clamp(tag.getInt("MetabolismTicks"), 0, 19);
        foodTarget = null; flight = new FlightIntent(false, Vec3.ZERO, 0);
    }
}
