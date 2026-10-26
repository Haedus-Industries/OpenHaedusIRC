package io.github.wzhy233.openhaedusirc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class ProtocolCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int checks;

    public static void main(String[] args) throws Exception {
        var failure = new AtomicReference<Throwable>();
        try (var server = new OpenHaedusIRC("127.0.0.1", 0)) {
            Thread.startVirtualThread(() -> {
                try {
                    server.serve();
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            try (var alice = new Peer(server.port()); var bob = new Peer(server.port())) {
                alice.login("Alice", "000");
                check(alice.until(type("welcome")).path("service").asText().equals("OpenHaedusIRC"), "service name");
                bob.login("Bob", "000");
                check(bob.until(type("welcome")).path("version").asText().equals("000"), "protocol version");
                try (var duplicate = new Peer(server.port())) {
                    duplicate.login("alice", "000");
                    check(duplicate.until(type("error")).path("content").asText().contains("already"), "case-insensitive duplicate name");
                }
                alice.send(Map.of("type", "chat", "content", "hello"));
                check(alice.until(type("message")).path("content").asText().equals("hello"), "sender sees message");
                check(bob.until(type("message")).path("sender").asText().equals("Alice"), "peer sees server-selected sender");
                alice.send(Map.of("type", "presence", "content", Map.of(
                        "mcName", "Alice", "uuid", "12345678-1234-1234-1234-123456789abc", "skin-name", "Alex",
                        "customSkin", true, "cape", true, "model", "slim")));
                JsonNode presence = bob.until(packet -> type("onlineUsers").test(packet)
                        && packet.path("content").toString().contains("12345678-1234-1234-1234-123456789abc"));
                check(presence.path("content").size() == 2, "presence broadcast contains both users");
                bob.send(Map.of("type", "command", "command", "version", "arguments", new String[0]));
                check(bob.until(type("system")).path("content").asText().equals("OpenHaedusIRC 000"), "version command");
                bob.send(Map.of("type", "ping"));
                check(bob.until(type("pong")) != null, "heartbeat");
                bob.send(Map.of("type", "chat", "content", "x".repeat(513)));
                check(bob.until(type("error")) != null, "oversized chat rejected");
                check(alice.until(packet -> type("onlineUsers").test(packet) && packet.path("content").size() == 1) != null, "disconnect updates online list");
            }
            try (var wrong = new Peer(server.port())) {
                wrong.login("Wrong", "001");
                check(wrong.until(type("error")) != null, "unsupported public version rejected");
            }
            try (var early = new Peer(server.port())) {
                early.send(Map.of("type", "chat", "content", "before login"));
                check(early.until(type("error")) != null, "chat before login rejected");
            }
            try (var invalid = new Peer(server.port())) {
                invalid.login("../bad", "000");
                check(invalid.until(type("error")) != null, "invalid username rejected");
            }
            try (var large = new Peer(server.port())) {
                large.writer.write("x".repeat(16385) + "\n");
                large.writer.flush();
                check(large.until(type("error")) != null, "oversized frame rejected");
            }
            check(failure.get() == null, "server remains healthy after invalid clients");
        }
        System.out.println("PASS: OpenHaedusIRC protocol 000, " + checks + " checks");
    }

    private static Predicate<JsonNode> type(String expected) {
        return packet -> packet.path("type").asText().equals(expected);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void login(String name, String version) throws Exception {
            send(Map.of("type", "login", "service", "OpenHaedusIRC", "version", version, "username", name));
        }

        private void send(Map<String, Object> packet) throws Exception {
            writer.write(JSON.writeValueAsString(packet));
            writer.newLine();
            writer.flush();
        }

        private JsonNode until(Predicate<JsonNode> predicate) throws Exception {
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null) throw new AssertionError("Unexpected disconnect");
                JsonNode packet = JSON.readTree(line);
                if (predicate.test(packet)) return packet;
            }
            throw new AssertionError("Expected packet not received");
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
