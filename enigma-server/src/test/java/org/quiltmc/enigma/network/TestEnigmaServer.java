package org.quiltmc.enigma.network;

import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
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

		final var tasksThread = new Thread(() -> {
			while (true) {
				try {
					this.tasks.take().run();
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

	@Override
	public void confirmChange(Socket client, int syncId) {
		super.confirmChange(client, syncId);

		Objects
			.requireNonNull(
				this.changeConfirmationLatches.get(client),
				() -> "no change latch to confirm for client: " + client
			)
			.countDown();
	}

	public CountDownLatch waitChangeConfirmation(Socket client) {
		if (!this.getClients().containsKey(client)) {
			throw new IllegalStateException("trying to wait for change from non-client: " + client);
		}

		return Objects.requireNonNull(
			this.changeConfirmationLatches.get(client),
			() -> "not change latch to await for client: " + client
		);
	}

	@Override
	public void sendMessage(ServerMessage message) {
		if (this.sendMessageLatch != null) {
			this.sendMessageLatch.countDown();
		}

		super.sendMessage(message);
	}

	@Override
	void putClient(Socket client, Thread thread) {
		super.putClient(client, thread);

		final CountDownLatch old = this.changeConfirmationLatches.put(client, new CountDownLatch(1));
		if (old != null) {
			throw new IllegalStateException("replacing change latch for client: " + client);
		}
	}

	@Override
	void disconnect(Socket client) {
		try {
			final Map<Socket, Thread> clients = this.getClients();
			synchronized (clients) {
				Objects.requireNonNull(clients.get(client)).join(3000);
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		super.disconnect(client);
	}
}
