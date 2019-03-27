package tamaized.melongolem.common.capability;


import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tamaized.melongolem.MelonMod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = MelonMod.MODID)
public class CapabilityList {

	@CapabilityInject(ITinyGolemCapability.class)
	public static final Capability<ITinyGolemCapability> TINY_GOLEM;

	static {
		TINY_GOLEM = null;
	}

	@SubscribeEvent
	public static void attachCapabilityEntity(AttachCapabilitiesEvent<Entity> e) {
		if (e.getObject() instanceof EntityPlayer) {
			e.addCapability(ITinyGolemCapability.ID, new ICapabilitySerializable<NBTTagCompound>() {

				ITinyGolemCapability inst = TINY_GOLEM.getDefaultInstance();

				@Nonnull
				@Override
				public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
					return TINY_GOLEM.orEmpty(capability, LazyOptional.of(() -> inst)).cast();
				}

				@Override
				public NBTTagCompound serializeNBT() {
					return (NBTTagCompound) TINY_GOLEM.getStorage().writeNBT(TINY_GOLEM, inst, null);
				}

				@Override
				public void deserializeNBT(NBTTagCompound nbt) {
					TINY_GOLEM.getStorage().readNBT(TINY_GOLEM, inst, null, nbt);
				}

			});
		}
	}

	@SubscribeEvent
	public static void updateClone(PlayerEvent.Clone e) {
		ITinyGolemCapability newcap = getCap(e.getEntityPlayer(), TINY_GOLEM, null);
		ITinyGolemCapability oldcap = getCap(e.getOriginal(), TINY_GOLEM, null);
		if (newcap != null && oldcap != null)
			newcap.copyFrom(oldcap);
	}

	public static <T> T getCap(@Nullable ICapabilityProvider provider, Capability<T> cap) {
		return getCap(provider, cap, null);
	}

	public static <T> T getCap(@Nullable ICapabilityProvider provider, Capability<T> cap, @Nullable EnumFacing face) {
		LazyOptional<T> data = provider != null ? provider.getCapability(cap, face) : null;
		return data != null && data.isPresent() ? data.orElseThrow(IllegalStateException::new) : null;
	}

}
