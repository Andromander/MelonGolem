package tamaized.melongolem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public interface IModProxy {

	void init();

	void finish();

	void openSignHolderGui(ISignHolder golem);

	interface ISignHolder {

		ItemStack getHead();

		float getDistance(Entity entityIn);

		Component getSignText(int index);

		void setSignText(int index, Component text);

		int networkID();
	}

}
