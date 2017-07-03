package protocolsupport.zplatform.impl.spigot.network.handler;

import net.minecraft.server.IChatBaseComponent;
import net.minecraft.server.PacketStatusInListener;
import net.minecraft.server.PacketStatusInPing;
import net.minecraft.server.PacketStatusInStart;
import protocolsupport.protocol.packet.handler.AbstractStatusListener;
import protocolsupport.zplatform.network.NetworkManagerWrapper;

public class SpigotStatusListener extends AbstractStatusListener implements PacketStatusInListener {

	public SpigotStatusListener(NetworkManagerWrapper networkmanager) {
		super(networkmanager);
	}

	@Override
	public void a(IChatBaseComponent msg) {
	}

	@Override
	public void a(PacketStatusInStart packet) {
		handleStatusRequest();
	}

	@Override
	public void a(PacketStatusInPing packet) {
		handlePing(packet.a());
	}

}
