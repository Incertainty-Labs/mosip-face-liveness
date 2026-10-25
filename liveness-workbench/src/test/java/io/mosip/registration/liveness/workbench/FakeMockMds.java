package io.mosip.registration.liveness.workbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for MockMDS that reads a request exactly as its {@code SBIWorker} does (header lines up to a blank one,
 * the body length from {@code Content-Length:} at a fixed offset) and answers as its {@code SBIResponseInfo} does.
 * So a request this accepts, MockMDS accepts too.
 */
final class FakeMockMds implements AutoCloseable {

    /** One request as MockMDS saw it. */
    static final class Request {
        final String line;
        final String body;
        final List<String> headers;

        Request(String line, List<String> headers, String body) {
            this.line = line;
            this.headers = headers;
            this.body = body;
        }
    }

    private final ServerSocket server;
    private final Map<String, String> replies = new ConcurrentHashMap<String, String>();
    final List<Request> requests = new CopyOnWriteArrayList<Request>();

    FakeMockMds() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread t = new Thread(this::serve, "fake-mockmds");
        t.setDaemon(true);
        t.start();
    }

    int port() {
        return server.getLocalPort();
    }

    /** @param method the SBI method, for example RCAPTURE; @param json what to answer */
    void reply(String method, String json) {
        replies.put(method, json);
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(),
                        StandardCharsets.UTF_8));
                String first = reader.readLine();
                List<String> headers = new CopyOnWriteArrayList<String>();
                int contentLength = 0;
                String line;
                while (!(line = reader.readLine()).equals("")) {
                    // As SBIWorker: only these two spellings, the value from character 16
                    if (line.indexOf("content-length:") >= 0 || line.indexOf("Content-Length:") >= 0) {
                        contentLength = Integer.parseInt(line.substring(16, line.length()));
                    }
                    headers.add(line);
                }
                char[] body = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(body, read, contentLength - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                requests.add(new Request(first, headers, new String(body, 0, read)));
                String method = first.substring(0, first.indexOf(' '));
                String json = replies.getOrDefault(method, "{\"errorCode\":\"500\",\"errorInfo\":\"no reply set\"}");
                String response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nCACHE-CONTROL:no-cache\r\n"
                        + "Content-Length: " + json.length() + "\r\nContent-Type: application/json\r\n"
                        + "LOCATION: HTTP://127.0.0.1:" + port() + "/\r\nConnection: close\r\n\r\n" + json
                        + "\r\n\r\n";
                OutputStream out = s.getOutputStream();
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException | RuntimeException e) {
                if (server.isClosed()) {
                    return;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
