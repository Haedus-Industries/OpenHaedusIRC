package io.github.wzhy233.openhaedusirc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenHaedusIRC implements AutoCloseable {
    public static final String NAME = "OpenHaedusIRC";
    public static final String VERSION = "000";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ServerSocket listener = new ServerSocket();
    private final Set<Client> connections = ConcurrentHashMap.newKeySet();
    private final Map<String, Client> users = new ConcurrentHashMap<>();

    public OpenHaedusIRC(String host, int port) throws IOException {
        listener.bind(new InetSocketAddress(host, port));
    }

    public int port() {
        return listener.getLocalPort();
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 16667;
        try (var server = new OpenHaedusIRC(host, port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            System.out.println(NAME + " " + VERSION + " listening on " + host + ":" + server.port());
            server.serve();
        }
    }

    public void serve() throws IOException {
        while (!listener.isClosed()) {
            Socket socket;
            try {
                socket = listener.accept();
            } catch (IOException exception) {
                if (listener.isClosed()) return;
                throw exception;
            }
            try {
                if (connections.size() >= 64) {
                    socket.close();
                    continue;
                }
                Client client = new Client(socket);
                connections.add(client);
                Thread.startVirtualThread(client);
            } catch (IOException exception) {
                socket.close();
            }
        }
    }

    @Override
    public void close() {
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        connections.forEach(Client::close);
    }

    private void broadcast(Map<String, Object> packet) {
        for (Client client : users.values()) {
            if (client.ready) client.sendOrClose(packet);
        }
    }

    private void onlineUsers() {
        var list = new ArrayList<Map<String, Object>>();
        for (Client client : users.values()) {
            if (!client.ready) continue;
            var user = new HashMap<>(client.presence.toMap());
            user.put("username", client.name);
            user.put("permission", "User");
            list.add(user);
        }
        broadcast(Map.of("type", "onlineUsers", "content", list));
    }

    private static String text(JsonNode packet, String field, int maxLength) throws IOException {
        JsonNode value = packet.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > maxLength) {
            throw new IOException("Invalid field: " + field);
        }
        return value.textValue();
    }

    private final class Client implements Runnable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private volatile boolean ready;
        private volatile UserPresence presence = UserPresence.EMPTY;
        private String name;
        private long window = System.nanoTime();
        private int packets;

        private Client(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(10000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try {
                JsonNode login = read();
                if (login == null || !"login".equals(text(login, "type", 16))
                        || !NAME.equals(text(login, "service", 32)) || !VERSION.equals(text(login, "version", 3))) {
                    throw new IOException("Unsupported OpenHaedusIRC login");
                }
                name = text(login, "username", 16);
                if (!name.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Invalid username");
                if (login.has("presence")) presence = UserPresence.fromJson(login.get("presence"));
                if (users.putIfAbsent(name.toLowerCase(Locale.ROOT), this) != null) throw new IOException("Username already connected");
                send(Map.of("type", "welcome", "service", NAME, "version", VERSION, "username", name));
                ready = true;
                socket.setSoTimeout(30000);
                onlineUsers();
                JsonNode packet;
                while ((packet = read()) != null) {
                    long now = System.nanoTime();
                    if (now - window >= 1_000_000_000L) {
                        window = now;
                        packets = 0;
                    }
                    if (++packets > 20) throw new IOException("Too many packets");
                    switch (text(packet, "type", 16)) {
                        case "chat" -> {
                            String message = text(packet, "content", 512).strip();
                            if (message.isEmpty()) throw new IOException("Empty message");
                            broadcast(Map.of("type", "message", "sender", name, "permission", "User",
                                    "content", message, "timestamp", System.currentTimeMillis()));
                        }
                        case "presence" -> {
                            presence = UserPresence.fromJson(packet.get("content"));
                            onlineUsers();
                        }
                        case "ping" -> send(Map.of("type", "pong"));
                        case "command" -> {
                            switch (text(packet, "command", 32)) {
                                case "users" -> onlineUsers();
                                case "version" -> send(Map.of("type", "system", "content", NAME + " " + VERSION));
                                case "help" -> send(Map.of("type", "system", "content", "Commands: users, version, help"));
                                default -> send(Map.of("type", "error", "content", "Unknown command"));
                            }
                        }
                        default -> throw new IOException("Unknown packet type");
                    }
                }
            } catch (IOException | IllegalArgumentException exception) {
                sendOrClose(Map.of("type", "error", "content", exception.getMessage() == null ? "Invalid packet" : exception.getMessage()));
            } finally {
                ready = false;
                close();
                connections.remove(this);
                if (name != null && users.remove(name.toLowerCase(Locale.ROOT), this)) onlineUsers();
            }
        }

        private JsonNode read() throws IOException {
            var line = new StringBuilder();
            int value;
            while ((value = reader.read()) != -1) {
                if (value == '\n') {
                    JsonNode packet = JSON.readTree(line.toString());
                    if (packet == null || !packet.isObject()) throw new IOException("Expected JSON object");
                    return packet;
                }
                if (line.length() >= 16384) throw new IOException("Packet too large");
                line.append((char) value);
            }
            if (!line.isEmpty()) throw new IOException("Incomplete packet");
            return null;
        }

        private synchronized void send(Map<String, Object> packet) throws IOException {
            writer.write(JSON.writeValueAsString(packet));
            writer.newLine();
            writer.flush();
        }

        private void sendOrClose(Map<String, Object> packet) {
            try {
                send(packet);
            } catch (IOException exception) {
                close();
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
