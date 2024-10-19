import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HTTPServer {
    private static final int PORT = 8080;
    private static final Logger LOGGER = Logger.getLogger(HTTPServer.class.getName());
    private HttpServer server;
    private Map<String, String> sessions = new HashMap<>();

    public static void main(String[] args) {
        try {
            new HTTPServer().start();
        } catch (IOException e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Create contexts with logging
        createContext("/http1.0", new HTTP1_0Handler());
        createContext("/http1.1", new HTTP1_1Handler());
        createContext("/http2.0", new HTTP2_0Handler());
        createContext("/error", new ErrorDemoHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        LOGGER.info("Server started on port " + PORT);

        // Print access URLs
        System.out.println("\nServer is running. Access the following URLs in Firefox:");
        System.out.println("HTTP/1.0: http://localhost:" + PORT + "/http1.0");
        System.out.println("HTTP/1.1: http://localhost:" + PORT + "/http1.1");
        System.out.println("HTTP/2.0: http://localhost:" + PORT + "/http2.0");
        System.out.println("Error Demo: http://localhost:" + PORT + "/error?code=404\n");
    }

    private void createContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            try {
                LOGGER.info("Received request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
                handler.handle(exchange);
            } catch (Exception e) {
                LOGGER.severe("Error handling request to " + path + ": " + e.getMessage());
                handleError(exchange, 500);
            }
        });
    }

    // Base handler with common functionality
    abstract class BaseHandler implements HttpHandler {
        protected void logRequest(HttpExchange exchange) {
            LOGGER.info(String.format("Processing %s request to %s from %s",
                    exchange.getRequestMethod(),
                    exchange.getRequestURI(),
                    exchange.getRemoteAddress()));
        }

        protected void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        }
    }

    // HTTP/1.0 Handler
    class HTTP1_0Handler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            addCorsHeaders(exchange);

            exchange.getResponseHeaders().add("Server", "HTTPDemoServer/1.0");
            exchange.getResponseHeaders().add("Connection", "close");

            String response = "HTTP/1.0 Demo\n" +
                    "Features:\n" +
                    "- Basic request-response\n" +
                    "- No persistent connections\n" +
                    "- Limited headers";

            sendResponse(exchange, response, 200);
        }
    }

    // HTTP/1.1 Handler with improved error handling
    class HTTP1_1Handler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            addCorsHeaders(exchange);

            try {
                // Verify HTTP/1.1 required headers
                String hostHeader = exchange.getRequestHeaders().getFirst("Host");
                if (hostHeader == null || hostHeader.isEmpty()) {
                    LOGGER.warning("Missing Host header in HTTP/1.1 request");
                    handleError(exchange, 400);
                    return;
                }

                exchange.getResponseHeaders().add("Server", "HTTPDemoServer/1.1");
                exchange.getResponseHeaders().add("Connection", "keep-alive");
                exchange.getResponseHeaders().add("Keep-Alive", "timeout=5, max=1000");

                String response = "HTTP/1.1 Demo\n" +
                        "Features:\n" +
                        "- Persistent connections (keep-alive)\n" +
                        "- Host header requirement (verified)\n" +
                        "- Enhanced headers\n" +
                        "- Connection management";

                sendResponse(exchange, response, 200);
                LOGGER.info("Successfully processed HTTP/1.1 request");

            } catch (Exception e) {
                LOGGER.severe("Error in HTTP/1.1 handler: " + e.getMessage());
                handleError(exchange, 500);
            }
        }
    }

    // HTTP/2.0 Handler
    class HTTP2_0Handler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            addCorsHeaders(exchange);

            exchange.getResponseHeaders().add("Server", "HTTPDemoServer/2.0");
            exchange.getResponseHeaders().add("X-Stream-ID", "1");
            exchange.getResponseHeaders().add("X-Server-Push", "enabled");

            String response = "HTTP/2.0 Demo\n" +
                    "Features:\n" +
                    "- Multiplexing\n" +
                    "- Server Push\n" +
                    "- Header Compression\n" +
                    "- Binary Protocol\n" +
                    "- Stream Prioritization";

            sendResponse(exchange, response, 200);
        }
    }

    // Error handler
    class ErrorDemoHandler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            logRequest(exchange);
            addCorsHeaders(exchange);

            String query = exchange.getRequestURI().getQuery();
            int statusCode = 500;

            if (query != null && query.startsWith("code=")) {
                try {
                    statusCode = Integer.parseInt(query.substring(5));
                    LOGGER.info("Demonstrating error code: " + statusCode);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid error code requested: " + query);
                }
            }

            handleError(exchange, statusCode);
        }
    }

    // Enhanced error handling method
    private void handleError(HttpExchange exchange, int statusCode) throws IOException {
        String errorMessage = getErrorMessage(statusCode);
        LOGGER.warning("Sending error response: " + statusCode + " - " + errorMessage);

        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        byte[] responseBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
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

    // Enhanced response sending method with logging
    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        try {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            LOGGER.info("Response sent successfully - Status: " + statusCode);
        } catch (IOException e) {
            LOGGER.severe("Failed to send response: " + e.getMessage());
            throw e;
        }
    }
}