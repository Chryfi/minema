package info.ata4.minecraft.minema.client.modules;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.modules.modifiers.TimerModifier;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

// A better synchronize module insteads of ShaderSync & TickSynchronizer (Maybe)
public class SyncModule extends CaptureModule {

	private static final Logger NetworkLogger = LogManager.getLogger("MinemaNetworkSync");
	
	private static SyncModule instance = null;
	private static Queue<FutureTask<?>>  queue = null;
	
	// Called by ASM from Minecraft
	public static int minTicks(int ten, int elapsedTicks) {
		return instance != null && instance.isEnabled() ? elapsedTicks : Math.min(ten, elapsedTicks);
	}

	// Called by ASM from EntityTrackerEntry & NetHandlerPlayClient
	public static int getUpdateFrequency(int origin) {
		return canSync() && Minema.instance.getConfig().entitySync.get() && (origin == 3 || origin == 2) ? 1 : origin;
	}

	private static AtomicBoolean isRemote = null;
	
    private static boolean canSync() {
        return Minema.instance.isInGame() && MC.player != null && MC.world != null && MC.isSingleplayer() && (instance != null && instance.isEnabled() || Minema.instance.getConfig().threadSync.get());
    }
	
	// Called by ASM from MinecraftServer
	public static long doServerTickSync(long currentTime) {
		try {
			if (canSync() && isRemote != null) {
				MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
				while (canSync() && isRemote != null) {
                    if (canSync() && isRemote != null && isRemote.get()) {
                        Thread.sleep(0L);
                        continue;
                    }
                    PacketMinemaSync.await();
                    server.tick();
                    server.getPlayerList().getPlayers().get(0).connection.sendPacket(new PacketMinemaSync());
                    isRemote.set(true);
				}
				return MinecraftServer.getCurrentTimeMillis();
			}
		} catch (Exception ex) {
			L.error("Server tick sync failed: {}", ex.getMessage());
			L.catching(Level.DEBUG, ex);
		}
		return currentTime;
	}
	
	public static boolean wakeServerTick() {
		if (!canSync())
			return false;
		try {
		    if (isRemote == null)
		        isRemote = new AtomicBoolean();
            isRemote.set(false);

		    MC.player.connection.sendPacket(new PacketMinemaSync());
		    while (canSync() && !isRemote.get()) Thread.sleep(0L);
			return true;
		} catch (Exception ex) {
			L.error("Client tick sync failed: {}", ex.getMessage());
			L.catching(Level.DEBUG, ex);
			return false;
		}
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent e) {
		if (canSync() && e.phase == Phase.START) {
			if (wakeServerTick()) {
	            if (queue  == null)
	                queue = PrivateAccessor.getScheduledTasks(MC);
	            PacketMinemaSync.await();
	            
	            while (!queue.isEmpty())
	                Util.runTask(queue.poll(), NetworkLogger); // Don't postpone network events to the next frame
			}
		}
	}
	
	@SubscribeEvent
	public static void onUnloadWorld(Unload e) {
	    if (e.getWorld().isRemote)
	        isRemote = null;
	}

	@Override
	protected void doEnable() throws Exception {
		instance = this;
		if (isRemote == null)
		    isRemote = new AtomicBoolean(true);
	}

	@Override
	protected boolean checkEnable() {
		return (Minema.instance.getConfig().syncEngine.get() || Minema.instance.getConfig().vr.get()) && MC.isSingleplayer();
	}

	@Override
	protected void doDisable() throws Exception {
		instance = null;
	}
	
	public static class PacketMinemaSync implements Packet<INetHandler> {

		private static volatile CountDownLatch lock = null;
		
		public static void await() {
		    if (lock != null)
		        try {lock.await();} catch (InterruptedException e) {}
		}
		
		private CountDownLatch l;
		
		public PacketMinemaSync() {
			lock = l = new CountDownLatch(1);
		}
		
		@Override
		public void readPacketData(PacketBuffer buf) throws IOException {}

		@Override
		public void writePacketData(PacketBuffer buf) throws IOException {}

		@Override
		public void processPacket(INetHandler handler) {
			l.countDown();
			l = null;
		}

        @Override
        protected void finalize() throws Throwable
        {
            if (l != null)
                l.countDown();
        }

	}
	
}
