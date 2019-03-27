package tamaized.melongolem.network;


import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.unsafe.UnsafeHacks;
import tamaized.melongolem.network.client.ClientPacketHandlerMelonTTS;
import tamaized.melongolem.network.client.ClientPacketHandlerParticle;
import tamaized.melongolem.network.server.ServerPacketHandlerDonatorSettings;
import tamaized.melongolem.network.server.ServerPacketHandlerMelonSign;

import java.util.function.Supplier;

public class NetworkMessages {

	private static int index = 0;

	public static void register(SimpleChannel network) {
		registerMessage(network, ServerPacketHandlerMelonSign.class, IMessage.Side.SERVER);
		registerMessage(network, ServerPacketHandlerDonatorSettings.class, IMessage.Side.SERVER);

		registerMessage(network, ClientPacketHandlerMelonTTS.class, IMessage.Side.CLIENT);
		registerMessage(network, ClientPacketHandlerParticle.class, IMessage.Side.CLIENT);
	}

	private static <M extends IMessage<M>> void registerMessage(SimpleChannel network, Class<M> type, IMessage.Side side) {
		network.registerMessage(index++, type, IMessage::encode, p -> IMessage.decode(p, type), (m, s) -> IMessage.onMessage(m, s, side));
	}

	public interface IMessage<SELF extends IMessage<SELF>> {

		static <M extends IMessage<M>> void encode(M message, PacketBuffer packet) {
			message.toBytes(packet);
		}

		static <M extends IMessage<M>> M decode(PacketBuffer packet, Class<M> type) {
			return UnsafeHacks.newInstance(type).fromBytes(packet);
		}

		static void onMessage(IMessage message, Supplier<NetworkEvent.Context> context, Side side) {
			switch (side) {
				case CLIENT: {
					EntityPlayer player = Minecraft.getInstance().player;
					Minecraft.getInstance().addScheduledTask(() -> message.handle(player));
				}
				break;
				case SERVER: {
					EntityPlayerMP player = context.get().getSender();
					if (player != null)
						player.getServerWorld().addScheduledTask(() -> message.handle(player));
				}
				break;
			}
			context.get().setPacketHandled(true);
		}

		void handle(EntityPlayer player);

		void toBytes(PacketBuffer packet);

		SELF fromBytes(PacketBuffer packet);

		enum Side {
			CLIENT, SERVER
		}

	}
}
