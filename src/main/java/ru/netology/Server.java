package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    public static final String GET = "GET";
    public static final String POST = "POST";

    static final int MAX_THREAD = 64;
    private static int port;
    private Socket socket;
    private volatile BufferedInputStream in;
    private volatile BufferedOutputStream out;

    final List<String> allowedMethods = List.of(GET, POST);

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
                        if (!socket.isClosed()) {
                            this.handler(socket);
                        }
                    } catch (NullPointerException | InvalidPathException ignored) {
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

        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
        // лимит на request line + заголовки
        final var limit = 4096;

        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // ищем request line
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);

        if (requestLineEnd == -1) {
            badRequest(out);
            socket.close();
        }

        // читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");

        if (requestLine.length != 3) {
            badRequest(out);
            socket.close();
        }

        final var method = requestLine[0];
        if (!allowedMethods.contains(method)) {
            badRequest(out);
            socket.close();
        }
        System.out.println(method);

        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            badRequest(out);
            socket.close();
        }

        System.out.println(getQueryParam(path));

        List<NameValuePair> queryParsmsList = getQueryParams(path);
        if (!queryParsmsList.isEmpty()) {
            System.out.println("QueryParams");
            for (NameValuePair item : queryParsmsList) {
                System.out.println(item.getName() + "= " + item.getValue());
            }
        }

        // ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            badRequest(out);
            socket.close();
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        System.out.println(headers);

        // для GET тела нет
        if (!method.equals(GET)) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);

                final var body = new String(bodyBytes);
                System.out.println(body);
            }
        }

        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public List<NameValuePair> getQueryParams(String string) throws IOException {
        return URLEncodedUtils.parse(URI.create(string), "UTF-8");
    }

    public String getQueryParam(String path) {
        int start = path.indexOf("/");
        int end = (path.contains("?")) ? (path.indexOf("?") - 1) : path.length();
        return (end == -1) ? "" : path.substring(start, end);
    }
}

