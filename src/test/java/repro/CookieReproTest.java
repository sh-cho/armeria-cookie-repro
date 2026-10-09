package repro;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.Cookie;

class CookieReproTest {

    // Cookie.fromSetCookieHeader() is documented to return null on failure, not to throw.

    @Test
    void nonAsciiDomain() {
        assertThatCode(() -> Cookie.fromSetCookieHeader("a=b; Domain=éxample.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void controlCharInDomain() {
        assertThatCode(() -> Cookie.fromSetCookieHeader("a=b; Domain=ex\u0001ample.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void nonAsciiPath() {
        assertThatCode(() -> Cookie.fromSetCookieHeader("a=b; Path=/é"))
                .doesNotThrowAnyException();
    }

    // A raw HTTP/1.1 server sends 'Set-Cookie: a=b; Domain=\xE9xample.com' (obs-text) on the wire.
    // 'h1c' skips the HTTP/2 upgrade which the raw server doesn't support.
    @Test
    void setCookieFromServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final Thread serverThread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    readRequestHeaders(socket.getInputStream());
                    final OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n" +
                               "Content-Length: 0\r\n" +
                               "Set-Cookie: a=b; Domain=éxample.com\r\n" +
                               "Connection: close\r\n" +
                               "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } catch (Exception ignored) {
                    // Ignore
                }
            });
            serverThread.start();

            final AggregatedHttpResponse res =
                    WebClient.of("h1c://127.0.0.1:" + server.getLocalPort()).blocking().get("/");
            assertThatCode(() -> res.headers().cookies()).doesNotThrowAnyException();
            serverThread.join();
        }
    }

    private static void readRequestHeaders(InputStream in) throws Exception {
        int matched = 0;
        final byte[] end = { '\r', '\n', '\r', '\n' };
        while (matched < end.length) {
            final int b = in.read();
            if (b < 0) {
                return;
            }
            matched = b == end[matched] ? matched + 1 : (b == '\r' ? 1 : 0);
        }
    }
}
