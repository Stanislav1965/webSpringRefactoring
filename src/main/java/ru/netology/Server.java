package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    static final int MAX_THREAD = 64;
    private static int port;
    private Socket socket;
    private volatile BufferedReader in;
    private volatile BufferedOutputStream out;

    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css"
            , "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.listen(9999);
    }


    public void listen(int port) {
        Server.port = port;
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD);

        try (ServerSocket serverSocket = new ServerSocket(Server.port)) {
            while (true) {
                // accept() будет ждать пока кто-нибудь подключиться
                socket = serverSocket.accept();
                Runnable runnable = () -> {
                    try {
                        this.handler(socket);
                    } catch (NullPointerException ignored) {
                    } catch (IOException e) {
                        throw new RuntimeException(e);

                    }
                };
                pool.execute(runnable);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    public synchronized void handler(Socket socket) throws IOException {

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedOutputStream(socket.getOutputStream());
        String request = in.readLine();
        final var parts = request.split(" ");

        if (parts.length != 3) {
            socket.close();
        }

        final var path = parts[1];
        if (!validPaths.contains(path)) {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
            System.out.println("404 Not Found");
            socket.close();
        }

        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            socket.close();
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }
}

