package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

public class LivingCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private static final TagKey<Item> MEAT = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "biomass"));
    private static final TagKey<Item> LEVITITE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite"));
    private final BioshipNeeds needs = new BioshipNeeds();
    private boolean awakened, resting, mouthDeveloped;
    private int stage, growthIndex, metabolismTicks, mouthBiomass;
    private long lastServerTick = Long.MIN_VALUE;
    private double hoverHeight = Double.NaN;
    private net.minecraft.world.entity.item.ItemEntity foodTarget;
    private volatile FlightIntent flight = new FlightIntent(false, Vec3.ZERO, 0);
    private record FlightIntent(boolean lift, Vec3 velocity, double turn) {}
    public LivingCoreBlockEntity(BlockPos pos, BlockState state) { super(Create_bioships.CORE_ENTITY.get(), pos, state); }

    ServerSubLevel ship() {
        if (!(level instanceof ServerLevel server)) return null;
        var container = SubLevelContainer.getContainer(server);
        if (container == null || !container.inBounds(worldPosition)) return null;
        var plot = container.getPlot(new ChunkPos(worldPosition));
        return plot != null && plot.getSubLevel() instanceof ServerSubLevel ship ? ship : null;
    }
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
            boolean growingMouth = stage == 2 && !mouthDeveloped && mouthBiomass < 32;
            if (needs.feedMeat(stack.is(Items.ROTTEN_FLESH) ? 40 : 70, stage != 0) || growingMouth) {
                if (growingMouth) mouthBiomass++;
                stack.shrink(1); setChanged();
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.65f);
            }
        } else if (stack.is(LEVITITE)) {
            if (needs.feedLevitite()) { stack.shrink(1); setChanged(); }
        } else if (stack.isEmpty() && !player.isShiftKeyDown() && stage == 2) {
            resting = !resting; setChanged();
            message(player, resting ? "message.create_bioships.resting" : "message.create_bioships.controls_enabled"); return;
        }
        message(player, "message.create_bioships.status", Component.translatable("stage.create_bioships." + stage), needs.biomass(), BioshipNeeds.BIOMASS_REQUIRED,
                needs.levitite(), BioshipNeeds.LEVITITE_REQUIRED, needs.satiety(), BioshipNeeds.MAX_SATIETY,
                Component.translatable("mood.create_bioships." + needs.mood()), needs.trust());
        if (stage == 0 && needs.readyToGrow() && !roomToGrow(ship)) message(player, "message.create_bioships.obstructed");
        if (stage == 2 && !mouthDeveloped) message(player, "message.create_bioships.mouth_growth", mouthBiomass);
    }
    private static void message(Player player, String key, Object... args) { player.displayClientMessage(Component.translatable(key, args), true); }
    public void mistreat() { if (ship() != null) { needs.mistreat(); setChanged(); } }
    private boolean clearForGrowth(ServerSubLevel ship, BlockPos pos) {
        if (!ship.getPlot().contains(new ChunkPos(pos)) || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
        Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(pos));
        return level.getBlockState(BlockPos.containing(physical)).isAir();
    }
    private boolean roomToGrow(ServerSubLevel ship) {
        for (var part : DinghyGrowth.body()) if (!clearForGrowth(ship, worldPosition.offset(part.offset()))) return false;
        return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, LivingCoreBlockEntity core) {
        core.serverTick(); core.growBody();
    }
    // New actor block entities are created outside Sable's actor iteration.
    private void growBody() {
        ServerSubLevel ship = ship();
        if (ship == null || !primary(ship)) return;
        if (stage == 0 && needs.readyToGrow() && level.getGameTime() % 20 == 0 && roomToGrow(ship)) { stage = 1; setChanged(); }
        if (stage != 1 || level.getGameTime() % 2 != 0) return;
        var parts = DinghyGrowth.body();
        if (growthIndex < parts.size()) {
            var part = parts.get(growthIndex); BlockPos target = worldPosition.offset(part.offset());
            if (clearForGrowth(ship, target) && level.setBlock(target, part.state(), 3)) {
                if (level.getBlockEntity(target) instanceof SteeringWheelBlockEntity helm) {
                    helm.material = net.minecraft.world.level.block.Blocks.CRIMSON_PLANKS.defaultBlockState(); helm.notifyUpdate();
                }
                growthIndex++; setChanged();
            }
        }
        if (growthIndex == parts.size()) { stage = 2; setChanged(); }
    }
    @Override public void sable$tick(ServerSubLevel ship) { serverTick(); }
    private void serverTick() {
        if (level == null || level.getGameTime() == lastServerTick) return;
        lastServerTick = level.getGameTime();
        ServerSubLevel ship = ship();
        if (ship == null || !primary(ship)) { flight = new FlightIntent(false, Vec3.ZERO, 0); return; }
        if (!awakened) { awakened = true; ship.setName("Living Bioship"); setChanged(); }
        if (++metabolismTicks >= 20) { metabolismTicks = 0; needs.metabolize(); setChanged(); }
        Vec3 desired = Vec3.ZERO; double turn = 0; boolean heightCommanded = false;
        if (stage == 2 && !resting) {
            BlockState throttleState = level.getBlockState(worldPosition.offset(DinghyGrowth.THROTTLE));
            double throttle = throttleState.is(Create_bioships.THROTTLE.get()) ? LivingThrottleBlock.signed(throttleState) : 0;
            if (level.getBlockEntity(worldPosition.offset(DinghyGrowth.HELM)) instanceof SteeringWheelBlockEntity helm)
                turn = Math.clamp(-helm.getAngle() / 180.0, -1, 1);
            double vertical = 0;
            for (BlockPos offset : java.util.List.of(new BlockPos(2, 1, -1), new BlockPos(2, 1, 0))) {
                BlockPos pos = worldPosition.offset(offset); BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof NerveControlBlock block && (state.getValue(NerveControlBlock.ACTIVE) || level.hasNeighborSignal(pos))) vertical += block.command.vertical;
            }
            heightCommanded = vertical != 0;
            Vec3 local = new Vec3(0, vertical * 1.5, -3 * throttle);
            if (local.lengthSqr() > 9) local = local.normalize().scale(3);
            double response = needs.obedience();
            if (needs.mood().equals("grumpy") && level.getGameTime() % 100 < 40) response = 0;
            desired = ship.logicalPose().transformNormal(local).scale(response);
            turn *= response * (0.25 + 0.75 * Math.abs(throttle));
        }
        if (desired.lengthSqr() == 0 && turn == 0) {
            desired = resting ? Vec3.ZERO : forage(ship);
            heightCommanded = desired.lengthSqr() > 0;
            if (desired.lengthSqr() == 0 && !resting) desired = new Vec3(0, Math.sin(level.getGameTime() / 35.0) * 0.08, 0);
        }
        if (stage < 2 && needs.levitite() > 0) {
            BlockPos physical = BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)));
            if (level.getBlockState(physical.above()).isAir()) for (int depth = 1; depth <= 6; depth++)
                if (!level.getBlockState(physical.below(depth)).isAir()) { desired = new Vec3(0, 0.6, 0); break; }
        }
        if (stage == 2 && !mouthDeveloped && mouthBiomass >= 32 && clearForGrowth(ship, worldPosition.offset(DinghyGrowth.MOUTH))) {
            if (level.setBlock(worldPosition.offset(DinghyGrowth.MOUTH), Create_bioships.MOUTH.get().defaultBlockState(), 3)) { mouthDeveloped = true; setChanged(); }
        }
        if (stage == 2) {
            double muscles = FlagellumMotion.strength(swimmingFlagella(), level.getGameTime());
            desired = desired.scale(muscles); turn *= muscles;
            if (muscles == 0) heightCommanded = false;
            double height = ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition)).y;
            if (heightCommanded) hoverHeight = Double.NaN;
            else {
                if (!Double.isFinite(hoverHeight) || Math.abs(height - hoverHeight) > 8) hoverHeight = height;
                // Position feedback prevents tiny per-step buoyancy errors accumulating into drift.
                desired = desired.add(0, Math.clamp((hoverHeight - height) * 2.0, -1.5, 1.5), 0);
            }
        }
        flight = new FlightIntent(needs.levitite() > 0, desired, turn);
    }
    public boolean isMature() { return stage == 2; }
    public boolean resting() { return resting; }
    public boolean hungry() { return needs.satiety() < 240; }
    public boolean hasMouth() { return level != null && level.getBlockState(worldPosition.offset(DinghyGrowth.MOUTH)).is(Create_bioships.MOUTH.get()); }
    public Vec3 mouthPosition(ServerSubLevel ship) { return ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition.offset(DinghyGrowth.MOUTH))).add(0, -0.5, 0); }
    public void eatPrey() { if (hasMouth()) { needs.feedMeat(200, true); setChanged(); } }
    public int swimmingFlagella() {
        int count = 0;
        for (BlockPos offset : DinghyGrowth.FLAGELLA)
            if (level.getBlockEntity(worldPosition.offset(offset)) instanceof TentacleBlockEntity tentacle && tentacle.swimming()) count++;
        return count;
    }
    private Vec3 forage(ServerSubLevel ship) {
        if (!hungry()) { foodTarget = null; return Vec3.ZERO; }
        Vec3 mouth = hasMouth() ? mouthPosition(ship) : ship.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition).add(0, 1.5, 0));
        if (foodTarget != null && (!foodTarget.isAlive() || !foodTarget.getItem().is(MEAT) || foodTarget.position().distanceToSqr(mouth) > 64)) foodTarget = null;
        if (foodTarget == null && level.getGameTime() % 20 == 0) {
            double closest = 64;
            for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(mouth, mouth).inflate(8), entity -> entity.getItem().is(MEAT))) {
                double distance = item.position().distanceToSqr(mouth);
                if (distance < closest) { foodTarget = item; closest = distance; }
            }
        }
        if (foodTarget == null) return Vec3.ZERO;
        Vec3 food = foodTarget.position().add(0, 0.2, 0);
        if (level.clip(new ClipContext(mouth, food, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, foodTarget)).getType() != HitResult.Type.MISS) return Vec3.ZERO;
        if (food.distanceToSqr(mouth) < 4 && level.getGameTime() % 40 == 0) {
            ItemStack stack = foodTarget.getItem();
            if (needs.feedMeat(stack.is(Items.ROTTEN_FLESH) ? 40 : 70, stage != 0)) { stack.shrink(1); if (stack.isEmpty()) foodTarget.discard(); setChanged(); }
        }
        Vec3 scent = food.subtract(mouth);
        return scent.lengthSqr() < 0.25 ? Vec3.ZERO : scent.normalize().scale(0.5);
    }
    @Override public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle body, double dt) {
        FlightIntent intent = flight;
        if (!intent.lift() || !body.isValid()) return;
        double mass = ship.getMassTracker().getMass();
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0) return;
        Vector3d acceleration = new Vector3d(intent.velocity().x, intent.velocity().y, intent.velocity().z)
                .sub(body.getLinearVelocity()).mul(2).sub(DimensionPhysicsData.getGravity(ship.getLevel()));
        Vector3d up = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0), new Vector3d());
        Vector3d correction = up.cross(new Vector3d(0, 1, 0), new Vector3d()).mul(3);
        correction.add(0, intent.turn() * 1.2, 0).sub(new Vector3d(body.getAngularVelocity()).mul(1.8));
        body.addLinearAndAngularVelocity(acceleration.mul(dt), correction.mul(dt));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BodyLayoutVersion", 2); tag.putInt("MouthBiomass", mouthBiomass); tag.putBoolean("MouthDeveloped", mouthDeveloped);
        tag.putBoolean("Resting", resting); tag.putBoolean("Awakened", awakened); tag.putInt("Stage", stage); tag.putInt("GrowthIndex", growthIndex);
        tag.putInt("Biomass", needs.biomass()); tag.putInt("Levitite", needs.levitite()); tag.putInt("Satiety", needs.satiety()); tag.putInt("Trust", needs.trust()); tag.putInt("MetabolismTicks", metabolismTicks);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mouthBiomass = Math.clamp(tag.getInt("MouthBiomass"), 0, 32); mouthDeveloped = tag.getBoolean("MouthDeveloped");
        resting = tag.getBoolean("Resting"); awakened = tag.getBoolean("Awakened"); stage = Math.clamp(tag.getInt("Stage"), 0, 2);
        growthIndex = Math.clamp(tag.getInt("GrowthIndex"), 0, DinghyGrowth.body().size());
        if (stage == 1 && tag.getInt("BodyLayoutVersion") < 2) { stage = 0; growthIndex = 0; }
        needs.restore(tag.getInt("Biomass"), tag.getInt("Levitite"), tag.contains("Satiety") ? tag.getInt("Satiety") : 600, tag.contains("Trust") ? tag.getInt("Trust") : 60);
        metabolismTicks = Math.clamp(tag.getInt("MetabolismTicks"), 0, 19);
        foodTarget = null; hoverHeight = Double.NaN; flight = new FlightIntent(false, Vec3.ZERO, 0);
    }
}
