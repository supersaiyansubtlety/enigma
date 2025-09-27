package org.quiltmc.enigma.network;

import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;

public class TestEnigmaServer extends EnigmaServer {
	private final Map<Socket, CountDownLatch> changeConfirmationLatches = new ConcurrentHashMap<>();
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingDeque<>();

	CountDownLatch sendMessageLatch;

	public TestEnigmaServer(byte[] jarChecksum, char[] password, EntryRemapper remapper, int port) {
		super(jarChecksum, password, remapper, port);
	}

	@Override
	public void start() throws IOException {
		super.start();

		var tasksThread = new Thread(() -> {
			while (true) {
				try {
					// synchronized (this.tasks) {
						this.tasks.take().run();
					// 	if (this.tasks.isEmpty()) {
					// 		this.tasks.notify();
					// 	}
					// }
				} catch (InterruptedException e) {
					break;
				}
			}
		});
		tasksThread.setName("Test server tasks");
		tasksThread.setDaemon(true);
		tasksThread.start();
	}

	@Override
	protected void runOnThread(Runnable task) {
		this.tasks.add(task);
	}

	// public void awaitTasks() {
	// 	synchronized (this.tasks) {
	// 		while (!this.tasks.isEmpty()) {
	// 			try {
	// 				this.tasks.wait(3000);
	// 			} catch (InterruptedException e) {
	// 				throw new RuntimeException(e);
	// 			}
	// 		}
	// 	}
	// }
	// public boolean hasTasks() {
	// 	return !this.tasks.isEmpty();
	// }

	public boolean hasOpenClients() {
		return this.clients.stream().anyMatch(socket -> !socket.isClosed());
	}

	public List<Socket> getClients() {
		return this.clients;
	}

	@Override
	public boolean isRunning() {
		return super.isRunning();
	}

	@Override
	public void confirmChange(Socket client, int syncId) {
		super.confirmChange(client, syncId);

		var latch = this.changeConfirmationLatches.get(client);
		if (latch != null) {
			latch.countDown();
		}
	}

	public CountDownLatch waitChangeConfirmation(Socket client, int count) {
		var latch = new CountDownLatch(count);
		this.changeConfirmationLatches.put(client, latch);
		return latch;
	}

	@Override
	public void sendMessage(ServerMessage message) {
		if (this.sendMessageLatch != null) {
			this.sendMessageLatch.countDown();
		}

		super.sendMessage(message);
	}
}
