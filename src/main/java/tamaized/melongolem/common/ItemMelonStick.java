package tamaized.melongolem.common;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import tamaized.melongolem.MelonMod;
import tamaized.melongolem.common.capability.CapabilityList;
import tamaized.melongolem.common.capability.ITinyGolemCapability;
import tamaized.melongolem.network.client.ClientPacketHandlerParticle;

import javax.annotation.Nonnull;

public class ItemMelonStick extends Item {

	public ItemMelonStick() {
		setMaxDamage(25);
	}

	public static void summonPet(World world, EntityPlayer owner) {
		summonPet(world, owner, null);
	}

	public static void summonPet(World world, EntityPlayer owner, EntityTinyMelonGolem golem) {
		if (golem != null && !golem.isEntityAlive())
			return;
		ITinyGolemCapability cap = CapabilityList.getCap(owner, CapabilityList.TINY_GOLEM, null);
		if (cap != null && cap.load(true)) {
			WorldServer loader = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(cap.getLoadDimID());
			loader.getBlockState(cap.getLoadPos()); // Ensure chunk is loaded
			Entity entity = loader.getEntityFromUuid(cap.getLoadPetID());
			if (entity instanceof EntityTinyMelonGolem) {
				EntityTinyMelonGolem melon = (EntityTinyMelonGolem) entity;
				if (world.provider.getDimension() != loader.provider.getDimension())
					melon.changeDimension(world.provider.getDimension());
				summonPet(world, owner, melon);
				return;
			}
		}
		EntityTinyMelonGolem oldPet = golem != null ? golem : cap != null ? cap.getPet() : null;
		if (oldPet != null && !oldPet.isEntityAlive())
			oldPet = null;
		EntityTinyMelonGolem pet = oldPet == null ? new EntityTinyMelonGolem(world) : oldPet;
		pet.setTamedBy(owner);
		if (oldPet == null && cap != null)
			cap.setPet(pet);

		int x = MathHelper.floor(owner.posX) - 2;
		int z = MathHelper.floor(owner.posZ) - 2;
		int y = MathHelper.floor(owner.getEntityBoundingBox().minY);

		loop:
		for (int l = pet.getRNG().nextInt(6); l <= 8; ++l) {
			for (int i1 = pet.getRNG().nextInt(6); i1 <= 8; ++i1) {
				for (int j = 3; j > -3; j--) {
					if (isTeleportFriendlyBlock(world, pet, x, z, y + j, l, i1)) {
						double posx = (double) ((float) (x + l) + 0.5F);
						double posy = (double) y + j;
						double posz = (double) ((float) (z + i1) + 0.5F);
						pet.setPositionAndUpdate(posx, posy, posz);
						if (pet.world.provider.getDimension() != owner.world.provider.getDimension()) {
							pet = (EntityTinyMelonGolem) pet.changeDimension(owner.world.provider.getDimension());
							if (cap != null)
								cap.setPet(pet);
							if (pet == null)
								return;
							pet.setPositionAndUpdate(posx, posy, posz);
						}
						for (int i = 0; i < 25; i++) {
							Vec3d result = pet.getLook(1F).rotateYaw(pet.getRNG().nextFloat() * 360F).rotatePitch(pet.getRNG().nextFloat() * 360F).scale(0.35F);
							spawnVanillaParticleOnServer(world, EnumParticleTypes.END_ROD, pet.posX + result.x, pet.posY + pet.height / 2F + result.y, pet.posZ + result.z, 0, 0, 0);
						}
						if (oldPet == null)
							world.spawnEntity(pet);
						world.playSound(null, pet.getPosition(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, pet.getRNG().nextFloat() + 0.5F);
						break loop;
					}
				}
			}
		}
	}

	public static void spawnVanillaParticleOnServer(World world, EnumParticleTypes particle, double x, double y, double z, double xS, double yS, double zS) {
		MelonMod.network.sendToAllAround(new ClientPacketHandlerParticle.Packet(particle.getParticleID(), new Vec3d(x, y, z), new Vec3d(xS, yS, zS)), new NetworkRegistry.TargetPoint(world.provider.getDimension(), x, y, z, 64));
	}

	private static boolean isTeleportFriendlyBlock(World world, Entity entity, int x, int z, int y, int xOffset, int zOffset) {
		BlockPos blockpos = new BlockPos(x + xOffset, y - 1, z + zOffset);
		IBlockState iblockstate = world.getBlockState(blockpos);
		return iblockstate.getBlockFaceShape(world, blockpos, EnumFacing.DOWN) == BlockFaceShape.SOLID && iblockstate.canEntitySpawn(entity) && world.isAirBlock(blockpos.up()) && world.isAirBlock(blockpos.up(2));
	}

	@Nonnull
	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, @Nonnull EnumHand handIn) {
		playerIn.swingArm(handIn);
		if (worldIn.isRemote)
			return new ActionResult<>(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
		summonPet(worldIn, playerIn);
		playerIn.getHeldItem(handIn).damageItem(1, playerIn);
		return new ActionResult<>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
	}
}
