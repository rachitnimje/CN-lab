import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BasicHTTPServer {
    private static final int PORT = 8080;
    private final ExecutorService executorService;
    private boolean running = true;

    public BasicHTTPServer() {
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public static void main(String[] args) {
        BasicHTTPServer server = new BasicHTTPServer();
        server.start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            System.out.println("\nAvailable endpoints:");
            System.out.println("HTTP/1.0: http://localhost:" + PORT + "/http1.0");
            System.out.println("HTTP/1.1: http://localhost:" + PORT + "/http1.1");
            System.out.println("HTTP/2.0: http://localhost:" + PORT + "/http2.0");
            System.out.println("Error Demo: http://localhost:" + PORT + "/error?code=404\n");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                executorService.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                String requestLine = in.readLine();
                if (requestLine == null) return;

                Map<String, String> headers = new HashMap<>();
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    String[] parts = headerLine.split(": ", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0], parts[1]);
                    }
                }

                String[] requestParts = requestLine.split(" ");
                String method = requestParts[0];
                String path = requestParts[1];
                String httpVersion = requestParts[2];

                switch (path) {
                    case "/http1.0" -> handleHttp10Response(out);
                    case "/http1.1" -> handleHttp11Response(out, headers);
                    case "/http2.0" -> handleHttp20Response(out);
                    default -> {
                        if (path.startsWith("/error")) {
                            handleErrorResponse(out, path);
                        } else {
                            handle404Response(out);
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleHttp10Response(OutputStream out) throws IOException {
            String content = "HTTP/1.0 Demo\n" +
                    "Features:\n" +
                    "- Basic request-response\n" +
                    "- No persistent connections\n" +
                    "- Limited headers";

            String response = "HTTP/1.0 200 OK\r\n" +
                    "Server: BasicHTTPServer/1.0\r\n" +
                    "Connection: close\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + content.length() + "\r\n" +
                    "\r\n" +
                    content;

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private void handleHttp11Response(OutputStream out, Map<String, String> headers) throws IOException {
            if (!headers.containsKey("Host")) {
                String errorResponse = "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 35\r\n" +
                        "\r\n" +
                        "Error: Missing required Host header";
                out.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                return;
            }

            String content = "HTTP/1.1 Demo\n" +
                    "Features:\n" +
                    "- Persistent connections\n" +
                    "- Host header requirement\n" +
                    "- Enhanced headers\n" +
                    "- Chunked transfer encoding";

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Server: BasicHTTPServer/1.1\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + content.length() + "\r\n" +
                    "\r\n" +
                    content;

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private void handleHttp20Response(OutputStream out) throws IOException {
            String content = "HTTP/2.0 Demo\n" +
                    "Features:\n" +
                    "- Multiplexing (simulated)\n" +
                    "- Server Push (simulated)\n" +
                    "- Header Compression\n" +
                    "- Binary Protocol\n" +
                    "- Stream Prioritization";

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Server: BasicHTTPServer/2.0\r\n" +
                    "X-HTTP2-Support: true\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + content.length() + "\r\n" +
                    "\r\n" +
                    content;

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private void handle404Response(OutputStream out) throws IOException {
            String content = "404 Not Found - The requested resource could not be found";
            String response = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + content.length() + "\r\n" +
                    "\r\n" +
                    content;

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private void handleErrorResponse(OutputStream out, String path) throws IOException {
            int statusCode = 500;
            try {
                String query = path.split("\\?")[1];
                if (query.startsWith("code=")) {
                    statusCode = Integer.parseInt(query.substring(5));
                }
            } catch (Exception e) {
            }

            String content = getErrorMessage(statusCode);
            String response = String.format("HTTP/1.1 %d %s\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: %d\r\n" +
                            "\r\n" +
                            "%s",
                    statusCode,
                    getStatusText(statusCode),
                    content.length(),
                    content);

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private String getErrorMessage(int statusCode) {
            return switch (statusCode) {
                case 400 -> "Bad Request - The server cannot process the request due to client error";
                case 401 -> "Unauthorized - Authentication required";
                case 403 -> "Forbidden - Server refuses to fulfill the request";
                case 404 -> "Not Found - The requested resource could not be found";
                case 405 -> "Method Not Allowed - The request method is not supported";
                case 500 -> "Internal Server Error - The server encountered an unexpected condition";
                case 501 -> "Not Implemented - The server does not support the functionality required";
                case 503 -> "Service Unavailable - The server is currently unavailable";
                default -> "Unknown Error - Status Code: " + statusCode;
            };
        }

        private String getStatusText(int statusCode) {
            return switch (statusCode) {
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 405 -> "Method Not Allowed";
                case 500 -> "Internal Server Error";
                case 501 -> "Not Implemented";
                case 503 -> "Service Unavailable";
                default -> "Unknown Status";
            };
        }
    }
}