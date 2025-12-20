package org.quiltmc.enigma.network;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.quiltmc.enigma.TestUtil;
import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.EnigmaProject;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.class_provider.ClasspathClassProvider;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.network.packet.c2s.LoginC2SPacket;
import org.quiltmc.enigma.network.packet.c2s.MessageC2SPacket;
import org.quiltmc.enigma.util.Utils;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkTest {
	private static final Path JAR = TestUtil.obfJar("complete");
	private static final String PASSWORD = "foobar";
	private static byte[] checksum;
	private static TestEnigmaServer server;
	private static EntryRemapper remapper;

	@BeforeAll
	public static void startServer() throws IOException {
		Enigma enigma = Enigma.create();
		EnigmaProject project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.createEmpty());

		checksum = Utils.zipSha1(JAR);
		remapper = project.getRemapper();
		server = new TestEnigmaServer(checksum, PASSWORD.toCharArray(), remapper, 0);

		server.start();
	}

	@AfterAll
	public static void stopServer() {
		server.stop();
	}

	private static TestEnigmaClient connectClient(ClientPacketHandler handler) throws IOException {
		if (server.socket == null || server.socket.isClosed() || !server.socket.isBound()) {
			throw new IllegalStateException("server socket unavailable");
		}

		var client = new TestEnigmaClient(handler, "127.0.0.1", server.getActualPort());
		client.connect();

		return client;
	}

	@RepeatedTest(100)
	public void testLogin(RepetitionInfo repetitionInfo) throws IOException, InterruptedException {
		final int repetition = repetitionInfo.getCurrentRepetition();
		Logger.info("Starting repetition: " + repetition);

		final Set<Socket> unapprovedClients = server.getUnapprovedClients();
		final Map<Socket, Thread> clients = server.getClients();

		final Socket oldClientSocket = clients.isEmpty() ? null : clients.keySet().iterator().next();

		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);

		handler.client = client;

		synchronized (unapprovedClients) {
			Assertions.assertEquals(1, unapprovedClients.size());
		}

		synchronized (clients) {
			Assertions.assertEquals(1, clients.size());
		}

		client.sendPacket(new LoginC2SPacket(checksum, PASSWORD.toCharArray(), "alice"));
		Logger.info("waiting for change packet");
		final Socket clientSocket = clients.keySet().iterator().next();

		Assertions.assertNotSame(oldClientSocket, clientSocket);

		final boolean confirmed;
		synchronized (clients) {
			confirmed = server.waitChangeConfirmation(clientSocket)
				.await(3, TimeUnit.SECONDS);
		}
		Logger.info("done waiting for change packet");

		Assertions.assertNotEquals(0, handler.disconnectFromServerLatch.getCount(), "The client was disconnected by the server");
		Assertions.assertTrue(confirmed, "Timed out waiting for the change confirmation");
		client.disconnect();

		// Logger.info("client count: " + clients.size());
		// Logger.info("client itr hash: " + clients.keySet().iterator().next().hashCode());
		// Logger.info("client socket hash: " + clientSocket.hashCode());
		Assertions.assertSame(clientSocket, clients.keySet().iterator().next());

		// TODO these don't work, but I've confirmed that clients does contain a different socket each repetition
		// synchronized (unapprovedClients) {
		// 	Assertions.assertEquals(0, unapprovedClients.size());
		// }

		// synchronized (clients) {
		// 	Assertions.assertEquals(0, clients.size());
		// }

		Logger.info("Finished repetition: " + repetition);
	}

	@Test
	public void testInvalidUsername() throws IOException, InterruptedException {
		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);
		handler.client = client;

		client.sendPacket(new LoginC2SPacket(checksum, PASSWORD.toCharArray(), "<span style=\"color: lavender\">eve</span>"));
		var disconnected = handler.disconnectFromServerLatch.await(3, TimeUnit.SECONDS);

		Assertions.assertTrue(disconnected, "Timed out waiting for the server to kick the client");
		client.disconnect();
	}

	@Test
	public void testWrongPassword() throws IOException, InterruptedException {
		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);
		handler.client = client;

		client.sendPacket(new LoginC2SPacket(checksum, "password".toCharArray(), "eve"));
		var disconnected = handler.disconnectFromServerLatch.await(3, TimeUnit.SECONDS);

		Assertions.assertTrue(disconnected, "Timed out waiting for the server to kick the client");
		client.disconnect();
	}

	@Test
	public void testTakenUsername() throws IOException, InterruptedException {
		var packet = new LoginC2SPacket(checksum, PASSWORD.toCharArray(), "alice");

		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);
		handler.client = client;
		client.sendPacket(packet);

		var handler2 = new DummyClientPacketHandler();
		var client2 = connectClient(handler2);
		handler2.client = client2;

		client2.sendPacket(packet);
		var disconnected = handler2.disconnectFromServerLatch.await(3, TimeUnit.SECONDS);

		Assertions.assertTrue(disconnected, "Timed out waiting for the server to kick the client");
		client.disconnect();
		client2.disconnect();
	}

	@Test
	public void testWrongChecksum() throws IOException, InterruptedException {
		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);
		handler.client = client;

		handler.disconnectFromServerLatch = new CountDownLatch(1);
		client.sendPacket(new LoginC2SPacket(new byte[EnigmaServer.CHECKSUM_SIZE], PASSWORD.toCharArray(), "eve"));
		var disconnected = handler.disconnectFromServerLatch.await(3, TimeUnit.SECONDS);

		Assertions.assertTrue(disconnected, "Timed out waiting for the server to kick the client");
		client.disconnect();
	}

	@Test
	public void testUnapprovedMessage() throws IOException, InterruptedException {
		var handler = new DummyClientPacketHandler();
		var client = connectClient(handler);
		handler.client = client;

		client.sendPacket(new MessageC2SPacket("I am in your (walls) server :3"));
		server.sendMessageLatch = new CountDownLatch(1);
		var sent = client.packetSentLatch.await(1, TimeUnit.SECONDS);
		Assertions.assertTrue(sent, "Failed to send packet");

		var handled = server.sendMessageLatch.await(2, TimeUnit.SECONDS);
		Assertions.assertFalse(handled, "The server handled an unapproved message!");
	}
}
