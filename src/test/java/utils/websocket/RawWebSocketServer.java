package utils.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;


/**
 * 프레임을 직접 쓰는 테스트용 WebSocket 서버. 연결 하나만 받는다.
 * <p>
 * OkHttp MockWebServer로는 만들 수 없는 상황(메시지를 조각 프레임으로 나눠 보내기, ping에 응답하지 않기,
 * 업그레이드 요청에 응답하지 않기)을 재현한다. 클라이언트가 보내는 프레임은 읽어서 버린다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class RawWebSocketServer implements AutoCloseable {
	/** 업그레이드 뒤에 서버가 할 일. */
	interface Handler {
		void handle(OutputStream out) throws IOException;
	}

	static final int OP_CONTINUATION = 0x0;
	static final int OP_TEXT = 0x1;
	static final int OP_BINARY = 0x2;

	private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	private final ServerSocket m_serverSocket;
	private final Thread m_thread;
	private volatile Socket m_socket;

	/**
	 * 서버를 시작한다.
	 *
	 * @param upgrade	{@code false}이면 업그레이드 요청에 응답하지 않는다(접속 시간 제한 시험용).
	 * @param handler	업그레이드 뒤에 실행할 것. {@code null}이면 아무것도 보내지 않는다.
	 */
	RawWebSocketServer(boolean upgrade, Handler handler) throws IOException {
		m_serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		m_thread = new Thread(() -> serve(upgrade, handler), "raw-websocket-server");
		m_thread.setDaemon(true);
		m_thread.start();
	}

	String url() {
		return "ws://127.0.0.1:" + m_serverSocket.getLocalPort() + "/ws";
	}

	private void serve(boolean upgrade, Handler handler) {
		try {
			Socket socket = m_serverSocket.accept();
			m_socket = socket;
			InputStream in = socket.getInputStream();
			String request = readHeaders(in);
			if ( !upgrade ) {
				return;			// 응답하지 않고 연결만 붙잡고 있는다.
			}
			String key = request.lines()
								.filter(l -> l.toLowerCase().startsWith("sec-websocket-key:"))
								.map(l -> l.substring(l.indexOf(':') + 1).trim())
								.findFirst().orElseThrow();
			String accept = Base64.getEncoder().encodeToString(
								MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
			OutputStream out = socket.getOutputStream();
			out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
						+ "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			if ( handler != null ) {
				handler.handle(out);
			}
			// 클라이언트가 보내는 프레임(ping, close 등)은 읽어서 버린다. 응답하지 않는다.
			byte[] buf = new byte[1024];
			while ( in.read(buf) >= 0 ) { }
		}
		catch ( Exception ignored ) {
			// 테스트 종료 시 소켓이 닫히며 생기는 예외
		}
	}

	private static String readHeaders(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int matched = 0;
		byte[] end = { '\r', '\n', '\r', '\n' };
		int b;
		while ( matched < 4 && (b = in.read()) >= 0 ) {
			buf.write(b);
			matched = (b == end[matched]) ? matched + 1 : (b == '\r' ? 1 : 0);
		}
		return buf.toString(StandardCharsets.US_ASCII);
	}

	/** 마스크하지 않은 서버 프레임 하나를 쓴다. */
	static void writeFrame(OutputStream out, boolean fin, int opcode, byte[] payload) throws IOException {
		out.write((fin ? 0x80 : 0) | opcode);
		if ( payload.length < 126 ) {
			out.write(payload.length);
		}
		else {
			out.write(126);
			out.write(payload.length >> 8);
			out.write(payload.length & 0xFF);
		}
		out.write(payload);
		out.flush();
	}

	@Override
	public void close() throws IOException {
		Socket socket = m_socket;
		if ( socket != null ) {
			socket.close();
		}
		m_serverSocket.close();
	}
}
