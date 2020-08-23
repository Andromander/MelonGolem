package tamaized.melongolem.common;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandException;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.Pose;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import tamaized.melongolem.IModProxy;
import tamaized.melongolem.MelonMod;
import tamaized.melongolem.common.capability.CapabilityList;
import tamaized.melongolem.network.DonatorHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class EntityTinyMelonGolem extends TameableEntity implements IForgeShearable, IEntityAdditionalSpawnData, IModProxy.ISignHolder {

	private static final DataParameter<ItemStack> HEAD = EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.ITEMSTACK);
	private static final DataParameter<Boolean> ENABLED = EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.BOOLEAN);
	private static final DataParameter<Integer> COLOR = EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.VARINT);
	private static final List<DataParameter<ITextComponent>> SIGN_TEXT = Lists.newArrayList(

			EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.TEXT_COMPONENT),

			EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.TEXT_COMPONENT),

			EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.TEXT_COMPONENT),

			EntityDataManager.createKey(EntityTinyMelonGolem.class, DataSerializers.TEXT_COMPONENT)

	);

	public EntityTinyMelonGolem(World worldIn) {
		super(Objects.requireNonNull(MelonMod.entityTypeTinyMelonGolem), worldIn);
	}

	@Nullable
	@Override
	public AgeableEntity func_241840_a(ServerWorld p_241840_1_, AgeableEntity p_241840_2_) {
		return null;
	}

	@Override
	protected void registerData() {
		super.registerData();
		dataManager.register(HEAD, ItemStack.EMPTY);
		dataManager.register(ENABLED, false);
		dataManager.register(COLOR, 0xFFFFFF);
		for (DataParameter<ITextComponent> sign : SIGN_TEXT)
			dataManager.register(sign, new StringTextComponent(""));
	}

	@Override
	public void livingTick() {
		super.livingTick();
		if (world.isRemote || !isAlive())
			return;
		if (getOwner() != null && getOwner().isAlive() && DonatorHandler.donators.contains(getOwnerId())) {
			DonatorHandler.DonatorSettings settings = DonatorHandler.settings.get(getOwnerId());
			if (settings != null) {
				dataManager.set(ENABLED, settings.enabled);
				dataManager.set(COLOR, settings.color);
			}
		}
		if (getOwner() instanceof PlayerEntity)
			getOwner().getCapability(CapabilityList.TINY_GOLEM).ifPresent(cap -> {
				if (cap.getPet() != this) {
					if (cap.getPet() != null && cap.getPet().getUniqueID().equals(this.getUniqueID())) {
						remove();
						return;
					}
					ItemMelonStick.summonPet(world, (PlayerEntity) getOwner(), this);
					if (cap.getPet() == null)
						cap.setPet(this);
					else
						this.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
				}
			});
	}

	public boolean isEnabled() {
		return dataManager.get(ENABLED);
	}

	public int getColor() {
		return dataManager.get(COLOR);
	}

	@Override
	public void setSignText(int index, ITextComponent text) {
		dataManager.set(SIGN_TEXT.get(index), text);
	}

	@Override
	public int networkID() {
		return getEntityId();
	}

	@Override
	public ITextComponent getSignText(int index) {
		return dataManager.get(SIGN_TEXT.get(index));
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FollowOwnerGoal(this, 1.0D, 4.0F, 2.0F, true));
		goalSelector.addGoal(1, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
		goalSelector.addGoal(2, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.addGoal(2, new LookRandomlyGoal(this));
	}

	@Override
	public boolean isShearable(@Nonnull ItemStack item, World world, BlockPos pos) {
		return !getHead().isEmpty();
	}

	@Override
	protected float getSoundPitch() {
		return rand.nextFloat() * 0.5F + 2F;
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SLIME_HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SLIME_DEATH;
	}

	@Nonnull
	@Override
	public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand) {
		if (!MelonMod.config.hats.get() || player.getHeldItemMainhand().getItem() instanceof ShearsItem || player.getHeldItemOffhand().getItem() instanceof ShearsItem)
			return ActionResultType.FAIL;
		ItemStack stack = player.getHeldItem(hand);
		if (!stack.isEmpty() && getHead().isEmpty()) {
			if (Block.getBlockFromItem(stack.getItem()) != Blocks.AIR || MelonMod.SIGNS.contains(stack.getItem())) {
				setHead(stack);
				if (!player.isCreative())
					player.getHeldItem(hand).shrink(1);
				return ActionResultType.SUCCESS;
			}
		} else if (!getHead().isEmpty() && MelonMod.SIGNS.contains(getHead().getItem())) {
			MelonMod.proxy.openSignHolderGui(this);
			return ActionResultType.SUCCESS;
		}
		return ActionResultType.FAIL;
	}

	@Override
	public float getStandingEyeHeight(Pose p_213316_1_, EntitySize p_213316_2_) {
		return 0.425F;
	}

	@Override
	public ItemStack getHead() {
		return dataManager.get(HEAD);
	}

	public void setHead(ItemStack stack) {
		for (int i = 0; i < 4; ++i)
			setSignText(i, new StringTextComponent(""));
		ItemStack newstack = stack.copy();
		newstack.setCount(1);
		dataManager.set(HEAD, newstack);
	}

	@Nonnull
	@Override
	public List<ItemStack> onSheared(@Nullable PlayerEntity player, @Nonnull ItemStack item, World world, BlockPos pos, int fortune) {
		List<ItemStack> list = Lists.newArrayList(MelonMod.config.shear.get() ? getHead() : ItemStack.EMPTY);
		setHead(ItemStack.EMPTY);
		return list;
	}

	@Override
	public void onDeath(@Nonnull DamageSource cause) {
		super.onDeath(cause);
		ItemStack stack = getHead();
		if (!world.isRemote && !stack.isEmpty()) {
			ItemEntity e = new ItemEntity(world, getPosX(), getPosY(), getPosZ(), stack);
			e.setMotion(e.getMotion().add(

					rand.nextFloat() * 0.05F,

					(rand.nextFloat() - rand.nextFloat()) * 0.1F,

					(rand.nextFloat() - rand.nextFloat()) * 0.1F

			));
			world.addEntity(e);
		}
	}

	@Nonnull
	@Override
	public CompoundNBT writeWithoutTypeId(CompoundNBT compound) {
		compound.put("head", getHead().serializeNBT());
		compound.putBoolean("donator_enabled", isEnabled());
		compound.putInt("donator_color", getColor());
		for (int i = 0; i < 4; ++i) {
			String s = ITextComponent.Serializer.toJson(getSignText(i));
			compound.putString("Text" + (i + 1), s);
		}
		return super.writeWithoutTypeId(compound);
	}

	@Override
	public void read(CompoundNBT compound) {
		if (compound.contains("donator_enabled"))
			dataManager.set(ENABLED, compound.getBoolean("donator_enabled"));
		if (compound.contains("donator_color"))
			dataManager.set(COLOR, compound.getInt("donator_color"));
		setHead(ItemStack.read(compound.getCompound("head")));

		for (int i = 0; i < 4; ++i) {
			String s = compound.getString("Text" + (i + 1));
			ITextComponent itextcomponent = ITextComponent.Serializer.func_240643_a_(s);

			try {
				setSignText(i, itextcomponent == null ? new StringTextComponent("") : TextComponentUtils.func_240645_a_(getCommandSource(), itextcomponent, null, 0));
			} catch (CommandException | CommandSyntaxException var7) {
				setSignText(i, itextcomponent);
			}
		}
		super.read(compound);
	}

	@Override
	public void writeSpawnData(PacketBuffer buffer) {
		buffer.writeItemStack(getHead());
	}

	@Override
	public void readSpawnData(PacketBuffer additionalData) {
		setHead(additionalData.readItemStack());
	}

}
