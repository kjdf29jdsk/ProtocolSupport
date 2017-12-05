package protocolsupport.zplatform.impl.spigot.network.handler;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.server.PlayerConnection;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerLoginEvent;
//import org.spigotmc.SpigotConfig;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ExpirableListEntry;
import net.minecraft.server.GameProfileBanEntry;
import net.minecraft.server.IChatBaseComponent;
import net.minecraft.server.ITickable;
import net.minecraft.server.IpBanEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.NetworkManager;
import net.minecraft.server.PacketListenerPlayIn;
import net.minecraft.server.PacketLoginInEncryptionBegin;
import net.minecraft.server.PacketLoginInListener;
import net.minecraft.server.PacketLoginInStart;
import net.minecraft.server.PacketPlayInAbilities;
import net.minecraft.server.PacketPlayInAdvancements;
import net.minecraft.server.PacketPlayInArmAnimation;
import net.minecraft.server.PacketPlayInAutoRecipe;
import net.minecraft.server.PacketPlayInBlockDig;
import net.minecraft.server.PacketPlayInBlockPlace;
import net.minecraft.server.PacketPlayInBoatMove;
import net.minecraft.server.PacketPlayInChat;
import net.minecraft.server.PacketPlayInClientCommand;
import net.minecraft.server.PacketPlayInCloseWindow;
import net.minecraft.server.PacketPlayInCustomPayload;
import net.minecraft.server.PacketPlayInEnchantItem;
import net.minecraft.server.PacketPlayInEntityAction;
import net.minecraft.server.PacketPlayInFlying;
import net.minecraft.server.PacketPlayInHeldItemSlot;
import net.minecraft.server.PacketPlayInKeepAlive;
import net.minecraft.server.PacketPlayInRecipeDisplayed;
import net.minecraft.server.PacketPlayInResourcePackStatus;
import net.minecraft.server.PacketPlayInSetCreativeSlot;
import net.minecraft.server.PacketPlayInSettings;
import net.minecraft.server.PacketPlayInSpectate;
import net.minecraft.server.PacketPlayInSteerVehicle;
import net.minecraft.server.PacketPlayInTabComplete;
import net.minecraft.server.PacketPlayInTeleportAccept;
import net.minecraft.server.PacketPlayInTransaction;
import net.minecraft.server.PacketPlayInUpdateSign;
import net.minecraft.server.PacketPlayInUseEntity;
import net.minecraft.server.PacketPlayInUseItem;
import net.minecraft.server.PacketPlayInVehicleMove;
import net.minecraft.server.PacketPlayInWindowClick;
import net.minecraft.server.PlayerInteractManager;
import net.minecraft.server.PlayerList;
import protocolsupport.protocol.ConnectionImpl;
import protocolsupport.protocol.packet.handler.AbstractLoginListenerPlay;
import protocolsupport.protocol.utils.authlib.GameProfile;
import protocolsupport.zplatform.impl.spigot.SpigotMiscUtils;
import protocolsupport.zplatform.impl.spigot.network.SpigotNetworkManagerWrapper;
import protocolsupport.zplatform.network.NetworkManagerWrapper;

public class SpigotLoginListenerPlay extends AbstractLoginListenerPlay implements PacketLoginInListener, PacketListenerPlayIn, ITickable {

	protected static final MinecraftServer server = SpigotMiscUtils.getServer();

	public SpigotLoginListenerPlay(NetworkManagerWrapper networkmanager, GameProfile profile, boolean onlineMode, String hostname) {
		super(networkmanager, profile, onlineMode, hostname);
	}

	@Override
	public void e() {
		tick();
	}

	@Override
	protected JoinData createJoinData() {
		com.mojang.authlib.GameProfile mojangGameProfile = SpigotMiscUtils.toMojangGameProfile(profile);
		EntityPlayer entity = new EntityPlayer(server, server.getWorldServer(0), mojangGameProfile, new PlayerInteractManager(server.getWorldServer(0)));
		entity.playerConnection = new PlayerConnection(server, ((SpigotNetworkManagerWrapper)networkManager).unwrap(), entity); // SportBukkit - create this right away so it's never null
		entity.hostname = hostname;
		entity.protocolVersion = ConnectionImpl.getFromChannel(networkManager.getChannel()).getVersion().getId();
		return new JoinData(entity.getBukkitEntity(), entity) {
			@Override
			protected void close() {
			}
		};
	}

	private static final SimpleDateFormat banDateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
	@Override
	protected void checkBans(PlayerLoginEvent event, Object[] data) {
		PlayerList playerlist = server.getPlayerList();

		com.mojang.authlib.GameProfile mojangGameProfile = ((EntityPlayer) data[0]).getProfile();

		InetSocketAddress socketaddress = networkManager.getAddress();
		if (playerlist.getProfileBans().isBanned(mojangGameProfile)) {
			GameProfileBanEntry profileban = playerlist.getProfileBans().get(mojangGameProfile);
			if (!hasExpired(profileban)) {
				String reason = "You are banned from this server!\nReason: " + profileban.getReason();
				if (profileban.getExpires() != null) {
					reason = reason + "\nYour ban will be removed on " + banDateFormat.format(profileban.getExpires());
				}
				event.disallow(PlayerLoginEvent.Result.KICK_BANNED, reason);
			}
		} else if (!playerlist.isWhitelisted(mojangGameProfile)) {
			event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, "Server is whitelisted.");
		} else if (playerlist.getIPBans().isBanned(socketaddress)) {
			IpBanEntry ipban = playerlist.getIPBans().get(socketaddress);
			if (!hasExpired(ipban)) {
				String reason = "Your IP address is banned from this server!\nReason: " + ipban.getReason();
				if (ipban.getExpires() != null) {
					reason = reason + "\nYour ban will be removed on " + banDateFormat.format(ipban.getExpires());
				}
				event.disallow(PlayerLoginEvent.Result.KICK_BANNED, reason);
			}
		} else if ((playerlist.players.size() >= playerlist.getMaxPlayers()) && !playerlist.f(mojangGameProfile)) {
			event.disallow(PlayerLoginEvent.Result.KICK_FULL, "Server is full! Try again soon.");
		}
	}

	private static boolean hasExpired(ExpirableListEntry<?> entry) {
		Date expireDate = entry.getExpires();
		return (expireDate != null) && expireDate.before(new Date());
	}

	@Override
	protected void joinGame(Object[] data) {
		server.getPlayerList().a((NetworkManager) networkManager.unwrap(), (EntityPlayer) data[0]);
	}

	@Override
	public void a(final IChatBaseComponent ichatbasecomponent) {
		Bukkit.getLogger().info(getConnectionRepr() + " lost connection: " + ichatbasecomponent.getText());
	}

	@Override
	public void a(final PacketLoginInStart packetlogininstart) {
	}

	@Override
	public void a(final PacketLoginInEncryptionBegin packetlogininencryptionbegin) {
	}

	@Override
	public void a(PacketPlayInArmAnimation p0) {
	}

	@Override
	public void a(PacketPlayInChat p0) {
	}

	@Override
	public void a(PacketPlayInTabComplete p0) {
	}

	@Override
	public void a(PacketPlayInClientCommand p0) {
	}

	@Override
	public void a(PacketPlayInSettings p0) {
	}

	@Override
	public void a(PacketPlayInTransaction p0) {
	}

	@Override
	public void a(PacketPlayInEnchantItem p0) {
	}

	@Override
	public void a(PacketPlayInWindowClick p0) {
	}

	@Override
	public void a(PacketPlayInCloseWindow p0) {
	}

	@Override
	public void a(PacketPlayInCustomPayload p0) {
	}

	@Override
	public void a(PacketPlayInUseEntity p0) {
	}

	@Override
	public void a(PacketPlayInKeepAlive p0) {
	}

	@Override
	public void a(PacketPlayInFlying p0) {
	}

	@Override
	public void a(PacketPlayInAbilities p0) {
	}

	@Override
	public void a(PacketPlayInBlockDig p0) {
	}

	@Override
	public void a(PacketPlayInEntityAction p0) {
	}

	@Override
	public void a(PacketPlayInSteerVehicle p0) {
	}

	@Override
	public void a(PacketPlayInHeldItemSlot p0) {
	}

	@Override
	public void a(PacketPlayInSetCreativeSlot p0) {
	}

	@Override
	public void a(PacketPlayInUpdateSign p0) {
	}

	@Override
	public void a(PacketPlayInUseItem p0) {
	}

	@Override
	public void a(PacketPlayInBlockPlace p0) {
	}

	@Override
	public void a(PacketPlayInSpectate p0) {
	}

	@Override
	public void a(PacketPlayInResourcePackStatus p0) {
	}

	@Override
	public void a(PacketPlayInBoatMove p0) {
	}

	@Override
	public void a(PacketPlayInVehicleMove p0) {
	}

	@Override
	public void a(PacketPlayInTeleportAccept p0) {
	}

	@Override
	public void a(PacketPlayInAutoRecipe p0) {
	}

	@Override
	public void a(PacketPlayInRecipeDisplayed p0) {
	}

	@Override
	public void a(PacketPlayInAdvancements arg0) {
	}

}
