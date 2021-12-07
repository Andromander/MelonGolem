package tamaized.melongolem.common;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fmllegacy.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import tamaized.melongolem.ISignHolder;
import tamaized.melongolem.MelonMod;
import tamaized.melongolem.client.ClientListener;
import tamaized.melongolem.network.client.ClientPacketHandlerMelonAmbientSound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public class EntityMelonGolem extends AbstractGolem implements RangedAttackMob, IForgeShearable, IEntityAdditionalSpawnData, ISignHolder {

	private static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.ITEM_STACK);
	//	private static final ResourceLocation LOOT = LootTables.register(new ResourceLocation(MelonMod.MODID, "melongolem"));
	private static final List<EntityDataAccessor<Component>> SIGN_TEXT = Lists.newArrayList(

			SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.COMPONENT),

			SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.COMPONENT),

			SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.COMPONENT),

			SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.COMPONENT)

	);
	private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(EntityMelonGolem.class, EntityDataSerializers.FLOAT);
	public static BlockState SIGN_TILE_BLOCKSTATE = Blocks.OAK_WALL_SIGN.defaultBlockState();
	public static final SignBlockEntity te = new SignBlockEntity(BlockPos.ZERO, SIGN_TILE_BLOCKSTATE) {
		@Nonnull
		@Override
		public BlockState getBlockState() {
			return SIGN_TILE_BLOCKSTATE;
		}
	};

	public EntityMelonGolem(Level worldIn) {
		this(Objects.requireNonNull(MelonMod.entityTypeMelonGolem), worldIn);
	}

	protected EntityMelonGolem(EntityType<? extends AbstractGolem> entity, Level level) {
		super(entity, level);
	}

	/*
	 * underscore required due to a bad reobf catch causing calls to this method to be obfuscated while the method remains the same
	 */
	public static AttributeSupplier.Builder _registerAttributes() {
		return Mob.createMobAttributes().
				add(Attributes.MAX_HEALTH, MelonMod.config.health.get().floatValue()).
				add(Attributes.MOVEMENT_SPEED, 0.2F);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		entityData.define(HEAD, ItemStack.EMPTY);
		for (EntityDataAccessor<Component> sign : SIGN_TEXT)
			entityData.define(sign, new TextComponent(""));
		entityData.define(PITCH, random.nextFloat() * 3.0F);
	}

	@Override
	public void setSignText(int index, Component text) {
		entityData.set(SIGN_TEXT.get(index), text);
	}

	@Override
	public int networkID() {
		return getId();
	}

	@Override
	public Component getSignText(int index) {
		return entityData.get(SIGN_TEXT.get(index));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
		this.goalSelector.addGoal(2, new EntityAISearchAndEatMelons(this));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D, 1.0000001E-5F));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (e) -> e instanceof Enemy));
	}

	@Override
	public void performRangedAttack(@Nonnull LivingEntity target, float distanceFactor) {
		EntityMelonSlice slice = new EntityMelonSlice(this.level, this);
		double d0 = target.getY() + (double) target.getEyeHeight() - 1.100000023841858D;
		double d1 = target.getX() - this.getX();
		double d2 = d0 - slice.getY();
		double d3 = target.getZ() - this.getZ();
		double f = Math.sqrt(d1 * d1 + d3 * d3) * 0.2F;
		slice.shoot(d1, d2 + f, d3, 1.6F, 12.0F);
		this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		slice.teleportTo(slice.getX(), slice.getY(), slice.getZ());
		level.addFreshEntity(slice);
	}

	@Override
	public boolean isShearable(@Nonnull ItemStack item, Level world, BlockPos vertex) {
		return !getHead().isEmpty();
	}

	/*@Override
	@Nullable
	protected ResourceLocation getLootTable() {
		return LOOT;
	}*/

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
		return SoundEvents.SLIME_HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return MelonMod.SIGNS.contains(getHead().getItem()) ? 200 : super.getAmbientSoundInterval();
	}

	@Override
	public float getVoicePitch() { // protected -> public
		return super.getVoicePitch();
	}

	@Override
	public float getSoundVolume() { // protected -> public
		return super.getSoundVolume();
	}

	@Override
	public void playAmbientSound() {
		if (level != null && !level.isClientSide)
			MelonMod.network.send(PacketDistributor.TRACKING_ENTITY.with(() -> this), new ClientPacketHandlerMelonAmbientSound(this));
	}

	@Nonnull
	@Override
	public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
		if (!MelonMod.config.hats.get() || player.getMainHandItem().getItem() instanceof ShearsItem || player.getOffhandItem().getItem() instanceof ShearsItem)
			return InteractionResult.FAIL;
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.isEmpty() && getHead().isEmpty()) {
			if (Block.byItem(stack.getItem()) != Blocks.AIR || MelonMod.SIGNS.contains(stack.getItem())) {
				setHead(stack);
				if (!player.isCreative())
					player.getItemInHand(hand).shrink(1);
				return InteractionResult.SUCCESS;
			}
		} else if (!getHead().isEmpty() && MelonMod.SIGNS.contains(getHead().getItem())) {
			if (level.isClientSide) {
				ClientListener.openSignHolderGui(this);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.FAIL;
	}

	@Override
	protected float getStandingEyeHeight(Pose p_213348_1_, EntityDimensions p_213348_2_) {
		return 1.7F;
	}

	@Override
	public ItemStack getHead() {
		return entityData.get(HEAD);
	}

	public void setHead(ItemStack stack) {
		for (int i = 0; i < 4; ++i)
			setSignText(i, new TextComponent(""));
		ItemStack newstack = stack.copy();
		newstack.setCount(1);
		entityData.set(HEAD, newstack);
	}

	@Nonnull
	@Override
	public List<ItemStack> onSheared(@Nullable Player player, @Nonnull ItemStack item, Level world, BlockPos vertex, int fortune) {
		List<ItemStack> list = Lists.newArrayList(MelonMod.config.shear.get() ? getHead() : ItemStack.EMPTY);
		setHead(ItemStack.EMPTY);
		return list;
	}

	@Override
	public void die(@Nonnull DamageSource cause) {
		super.die(cause);
		ItemStack stack = getHead();
		if (!level.isClientSide && !stack.isEmpty()) {
			ItemEntity e = new ItemEntity(level, getX(), getY(), getZ(), stack);
			e.setDeltaMovement(e.getDeltaMovement().add(

					random.nextFloat() * 0.05F,

					(random.nextFloat() - random.nextFloat()) * 0.1F,

					(random.nextFloat() - random.nextFloat()) * 0.1F

			));
			level.addFreshEntity(e);
		}
	}

	@Nonnull
	@Override
	public CompoundTag saveWithoutId(CompoundTag compound) {
		compound.put("head", getHead().serializeNBT());
		for (int i = 0; i < 4; ++i) {
			String s = Component.Serializer.toJson(getSignText(i));
			compound.putString("Text" + (i + 1), s);
		}
		return super.saveWithoutId(compound);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		setHead(ItemStack.of(compound.getCompound("head")));
		for (int i = 0; i < 4; ++i) {
			String s = compound.getString("Text" + (i + 1));
			Component itextcomponent = Component.Serializer.fromJson(s);

			try {
				setSignText(i, itextcomponent == null ? new TextComponent("") : ComponentUtils.updateForEntity(createCommandSourceStack(), itextcomponent, null, 0));
			} catch (CommandRuntimeException | CommandSyntaxException var7) {
				setSignText(i, itextcomponent);
			}
		}
		super.readAdditionalSaveData(compound);
	}

	@Override
	public void writeSpawnData(FriendlyByteBuf buffer) {
		buffer.writeItem(getHead());
	}

	@Override
	public void readSpawnData(FriendlyByteBuf additionalData) {
		setHead(additionalData.readItem());
	}

	public float getPitch() {
		return entityData.get(PITCH);
	}

	static class EntityAISearchAndEatMelons extends Goal {

		private final Mob parent;
		private final Item melon;
		private final Block melonblock;
		private int cooldown;
		private BlockPos.MutableBlockPos vertex = new BlockPos.MutableBlockPos();
		private boolean foundMelon = false;

		EntityAISearchAndEatMelons(Mob entity) {
			parent = entity;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
			melon = entity instanceof EntityGlisteringMelonGolem ? Items.GLISTERING_MELON_SLICE : Items.MELON_SLICE;
			melonblock = entity instanceof EntityGlisteringMelonGolem ? MelonMod.glisteringMelonBlock : Blocks.MELON;
		}

		@Override
		public boolean canUse() {
			return MelonMod.config.eats.get() && parent.getHealth() < parent.getMaxHealth();
		}

		@Override
		public void stop() {
			cooldown = 0;
		}

		private boolean isMelon(ItemEntity item) {
			return isMelon(item.getItem());
		}

		private boolean isMelon(ItemStack item) {
			return isMelon(item.getItem());
		}

		private boolean isMelon(Item item) {
			return item == melon || item == melonblock.asItem();
		}

		@Override
		public void tick() {
			if (parent == null)
				return;
			if (cooldown > 0)
				cooldown--;
			final int radius = 25;
			AABB area = new AABB(parent.getX() - radius, parent.getY() - radius, parent.getZ() - radius, parent.getX() + radius, parent.getY() + radius, parent.getZ() + radius);
			List<ItemEntity> items = parent.level.getEntitiesOfClass(ItemEntity.class, area);
			for (ItemEntity item : items) {
				if (parent.getNavigation().isDone() && isMelon(item)) {
					parent.getNavigation().moveTo(item, 1.25F);
					parent.getLookControl().setLookAt(item, 30.0F, 30.0F);
				}
				if (cooldown <= 0 && item.isAlive() && isMelon(item) && item.getBoundingBox().intersects(parent.getBoundingBox().inflate(1))) {
					boolean flag = item.getItem().getItem() == melonblock.asItem();
					item.getItem().shrink(1);
					parent.playSound(SoundEvents.PLAYER_BURP, 1F, 1F);
					parent.heal(MelonMod.config.heal.get().floatValue() * (flag ? 9 : 1));
					cooldown = 30 + parent.getRandom().nextInt(40);
				}
			}
			if (parent.getNavigation().isDone()) {
				foundMelon = false;
				search:
				for (int x = -radius; x < radius; x++)
					for (int y = -radius; y < radius; y++)
						for (int z = -radius; z < radius; z++) {
							vertex.set(parent.blockPosition().offset(x, y, z));
							if (parent.level.hasChunkAt(vertex)) {
								BlockEntity te = parent.level.getBlockEntity(vertex);
								if (te != null) {
									te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP).ifPresent(cap -> {
										for (int i = 0; i < cap.getSlots(); i++) {
											if (isMelon(cap.getStackInSlot(i))) {
												foundMelon = parent.distanceToSqr(vertex.getX(), vertex.getY(), vertex.getZ()) < 4 || parent.getNavigation().moveTo(vertex.getX(), vertex.getY(), vertex.getZ(), 1.25F);
												break;
											}
										}
									});
									if (foundMelon)
										break search;
								}
							}
						}
			}
			if (foundMelon) {
				// Validate
				if (!parent.level.hasChunkAt(vertex)) {
					parent.getNavigation().stop();
					foundMelon = false;
					return;
				}
				BlockEntity te = parent.level.getBlockEntity(vertex);
				if (te == null || !te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP).isPresent()) {
					parent.getNavigation().stop();
					foundMelon = false;
					return;
				}
				LazyOptional<IItemHandler> handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP);
				if (handler.isPresent()) {
					handler.ifPresent(cap -> {
						boolean valid = false;
						int i;
						for (i = 0; i < cap.getSlots(); i++) {
							if (isMelon(cap.getStackInSlot(i))) {
								valid = true;
								break;
							}
						}
						if (!valid) {
							parent.getNavigation().stop();
							foundMelon = false;
							return;
						}

						if (cooldown <= 0 && parent.distanceToSqr(vertex.getX(), vertex.getY(), vertex.getZ()) < 4) {
							boolean flag = cap.getStackInSlot(i).getItem() == melonblock.asItem();
							cap.getStackInSlot(i).shrink(1);
							parent.playSound(SoundEvents.PLAYER_BURP, 1F, 1F);
							parent.heal(MelonMod.config.heal.get().floatValue() * (flag ? 9 : 1));
							cooldown = 10 + parent.getRandom().nextInt(40);
						}
					});
				} else {
					parent.getNavigation().stop();
					foundMelon = false;
				}
			}
		}
	}
}
