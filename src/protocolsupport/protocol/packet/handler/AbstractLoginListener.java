package protocolsupport.protocol.packet.handler;

import java.security.PrivateKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;

import com.google.common.base.Charsets;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import protocolsupport.ProtocolSupport;
import protocolsupport.api.events.PlayerLoginStartEvent;
import protocolsupport.api.events.PlayerPropertiesResolveEvent.ProfileProperty;
import protocolsupport.protocol.ConnectionImpl;
import protocolsupport.protocol.pipeline.ChannelHandlers;
import protocolsupport.protocol.pipeline.common.PacketDecrypter;
import protocolsupport.protocol.pipeline.common.PacketEncrypter;
import protocolsupport.protocol.utils.MinecraftEncryption;
import protocolsupport.protocol.utils.authlib.GameProfile;
import protocolsupport.utils.Utils;
import protocolsupport.zplatform.ServerPlatform;
import protocolsupport.zplatform.impl.spigot.SpigotMiscUtils;
import protocolsupport.zplatform.impl.spigot.network.SpigotChannelHandlers;
import protocolsupport.zplatform.network.NetworkManagerWrapper;

public abstract class AbstractLoginListener implements IHasProfile {

	private static final int loginThreadKeepAlive = Utils.getJavaPropertyValue("loginthreadskeepalive", 60, Integer::parseInt);

	static {
		ProtocolSupport.logInfo(MessageFormat.format("Login threads keep alive time: {0}", loginThreadKeepAlive));
	}

	private static final Executor loginprocessor = new ThreadPoolExecutor(
		1, Integer.MAX_VALUE,
		loginThreadKeepAlive, TimeUnit.SECONDS,
		new LinkedBlockingQueue<Runnable>(),
		r -> new Thread(r, "LoginProcessingThread")
	);

	protected final NetworkManagerWrapper networkManager;
	protected final String hostname;
	protected final boolean hasCompression;
	protected final boolean fullEncryption;
	protected final byte[] randomBytes = new byte[4];
	protected int loginTicks;
	protected SecretKey loginKey;
	protected LoginState state = LoginState.HELLO;
	protected GameProfile profile;

	protected boolean isOnlineMode = Bukkit.getOnlineMode();
	protected boolean useOnlineModeUUID = isOnlineMode;
	protected UUID forcedUUID = null;

	public AbstractLoginListener(NetworkManagerWrapper networkmanager, String hostname, boolean hasCompression, boolean fullEncryption) {
		this.networkManager = networkmanager;
		this.hostname = hostname;
		this.hasCompression = hasCompression;
		this.fullEncryption = fullEncryption;
		ThreadLocalRandom.current().nextBytes(randomBytes);
	}

	@Override
	public GameProfile getProfile() {
		return profile;
	}

	public void tick() {
		if (loginTicks++ == 600) {
			disconnect("Took too long to log in");
		}
	}

	@SuppressWarnings("unchecked")
	public void disconnect(String s) {
		try {
			Bukkit.getLogger().info("Disconnecting " + getConnectionRepr() + ": " + s);
			networkManager.sendPacket(ServerPlatform.get().getPacketFactory().createLoginDisconnectPacket(s), new GenericFutureListener<Future<? super Void>>() {
				@Override
				public void operationComplete(Future<? super Void> future)  {
					networkManager.close(s);
				}
			});
		} catch (Exception exception) {
			Bukkit.getLogger().log(Level.SEVERE, "Error whilst disconnecting player", exception);
		}
	}

	public void initOfflineModeGameProfile() {
		profile = new GameProfile(networkManager.getSpoofedUUID() != null ? networkManager.getSpoofedUUID() : generateOffileModeUUID(), profile.getName());
		if (networkManager.getSpoofedProperties() != null) {
			for (ProfileProperty property : networkManager.getSpoofedProperties()) {
				profile.addProperty(property);
			}
		}
	}

	protected UUID generateOffileModeUUID() {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + profile.getName()).getBytes(Charsets.UTF_8));
	}

	public String getConnectionRepr() {
		return (profile != null) ? (profile + " (" + networkManager.getAddress() + ")") : networkManager.getAddress().toString();
	}

	public void handleLoginStart(String name) {
		Validate.isTrue(state == LoginState.HELLO, "Unexpected hello packet");
		state = LoginState.ONLINEMODERESOLVE;
		loginprocessor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					MinecraftServer server = SpigotMiscUtils.getServer();

					if (server.isSuspended()) {
						server.setSuspended(false);
					}
					profile = new GameProfile(null, name);

					PlayerLoginStartEvent event = new PlayerLoginStartEvent(
						ConnectionImpl.getFromChannel(networkManager.getChannel()),
						profile.getName(),
						isOnlineMode,
						useOnlineModeUUID,
						hostname
					);
					Bukkit.getPluginManager().callEvent(event);
					if (event.isLoginDenied()) {
						AbstractLoginListener.this.disconnect(event.getDenyLoginMessage());
						return;
					}

					isOnlineMode = event.isOnlineMode();
					useOnlineModeUUID = event.useOnlineModeUUID();
					forcedUUID = event.getForcedUUID();
					if (isOnlineMode) {
						state = LoginState.KEY;
						networkManager.sendPacket(ServerPlatform.get().getPacketFactory().createLoginEncryptionBeginPacket(ServerPlatform.get().getMiscUtils().getEncryptionKeyPair().getPublic(), randomBytes));
					} else {
						new PlayerAuthenticationTask(AbstractLoginListener.this, isOnlineMode).run();
					}
				} catch (Throwable t) {
					AbstractLoginListener.this.disconnect("Error occured while logging in");
					if (ServerPlatform.get().getMiscUtils().isDebugging()) {
						t.printStackTrace();
					}
				}
			}
		});
	}

	public static interface EncryptionPacketWrapper {

		public byte[] getNonce(PrivateKey key);

		public SecretKey getSecretKey(PrivateKey key);

	}

	public void handleEncryption(EncryptionPacketWrapper encryptionpakcet) {
		Validate.isTrue(state == LoginState.KEY, "Unexpected key packet");
		state = LoginState.AUTHENTICATING;
		loginprocessor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					final PrivateKey privatekey = ServerPlatform.get().getMiscUtils().getEncryptionKeyPair().getPrivate();
					if (!Arrays.equals(randomBytes, encryptionpakcet.getNonce(privatekey))) {
						throw new IllegalStateException("Invalid nonce!");
					}
					loginKey = encryptionpakcet.getSecretKey(privatekey);
					enableEncryption(loginKey);
					new PlayerAuthenticationTask(AbstractLoginListener.this, isOnlineMode).run();
				} catch (Throwable t) {
					AbstractLoginListener.this.disconnect("Error occured while logging in");
					if (ServerPlatform.get().getMiscUtils().isDebugging()) {
						t.printStackTrace();
					}
				}
			}
		});
	}

	protected void enableEncryption(SecretKey key) {
		ChannelPipeline pipeline = networkManager.getChannel().pipeline();
		pipeline.addBefore(SpigotChannelHandlers.SPLITTER, ChannelHandlers.DECRYPT, new PacketDecrypter(MinecraftEncryption.getCipher(Cipher.DECRYPT_MODE, key)));
		if (fullEncryption) {
			pipeline.addBefore(SpigotChannelHandlers.PREPENDER, ChannelHandlers.ENCRYPT, new PacketEncrypter(MinecraftEncryption.getCipher(Cipher.ENCRYPT_MODE, key)));
		}
	}

	@SuppressWarnings("unchecked")
	public void setReadyToAccept() {
		UUID newUUID = null;
		if (isOnlineMode && !useOnlineModeUUID) {
			newUUID = generateOffileModeUUID();
		}
		if (forcedUUID != null) {
			newUUID = forcedUUID;
		}
		if (newUUID != null) {
			GameProfile newProfile = new GameProfile(newUUID, profile.getName());
			newProfile.getProperties().putAll(profile.getProperties());
			profile = newProfile;
		}
		if (hasCompression) {
			int threshold = ServerPlatform.get().getMiscUtils().getCompressionThreshold();
			if (threshold >= 0) {
				this.networkManager.sendPacket(
					ServerPlatform.get().getPacketFactory().createSetCompressionPacket(threshold),
					new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future)  {
							ServerPlatform.get().getMiscUtils().enableCompression(networkManager.getChannel().pipeline(), threshold);
						}
					}
				);
			}
		}

		AbstractLoginListenerPlay listener = getLoginListenerPlay();
		networkManager.setPacketListener(listener);
		listener.finishLogin();
	}

	protected abstract AbstractLoginListenerPlay getLoginListenerPlay();

	public enum LoginState {
		HELLO, ONLINEMODERESOLVE, KEY, AUTHENTICATING;
	}

}
