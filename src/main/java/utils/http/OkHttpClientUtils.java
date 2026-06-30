package utils.http;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * {@link OkHttpClient} 생성 헬퍼 유틸리티.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OkHttpClientUtils {
	private OkHttpClientUtils() {
		throw new AssertionError("Should not be called: class=" + OkHttpClientUtils.class);
	}

	/**
	 * 기본 설정의 {@link OkHttpClient}를 생성한다.
	 *
	 * @return 생성된 클라이언트.
	 */
	public static OkHttpClient newClient() {
		return new OkHttpClient.Builder().build();
	}

	/**
	 * 모든 서버 인증서를 무조건 신뢰하고 호스트명 검증도 생략하는 {@link OkHttpClient}를 생성한다.
	 * <p>
	 * <b>경고:</b> TLS 인증서/호스트명 검증을 비활성화하므로 중간자 공격(MITM)에 취약하다.
	 * 테스트나 신뢰된 내부망 등 보안 위험을 감수할 수 있는 경우에만 사용해야 한다.
	 *
	 * @return 생성된 클라이언트.
	 * @throws KeyManagementException   SSL 컨텍스트 초기화에 실패한 경우.
	 * @throws NoSuchAlgorithmException SSL 컨텍스트 생성에 실패한 경우.
	 */
	public static OkHttpClient newTrustAllOkHttpClient() throws KeyManagementException, NoSuchAlgorithmException {
		return newTrustAllOkHttpClientBuilder().build();
	}

	/**
	 * 모든 서버 인증서를 무조건 신뢰하는 {@link OkHttpClient.Builder}를 생성한다.
	 * <p>
	 * <b>경고:</b> {@link #newTrustAllOkHttpClient()}와 동일하게 TLS 검증을 비활성화하므로 MITM에 취약하다.
	 *
	 * @return 추가 설정이 가능한 빌더.
	 * @throws KeyManagementException   SSL 컨텍스트 초기화에 실패한 경우.
	 * @throws NoSuchAlgorithmException SSL 컨텍스트 생성에 실패한 경우.
	 */
	public static OkHttpClient.Builder newTrustAllOkHttpClientBuilder()
		throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, new TrustManager[] {TRUST_ALL_CERTS}, new SecureRandom());
		
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		builder.sslSocketFactory(sslContext.getSocketFactory(), TRUST_ALL_CERTS);
		builder.hostnameVerifier((hostname, session) -> true);
		
		return builder;
	}
	
	private static final X509Certificate[] EMPTY_ISSUERS = new X509Certificate[] {};
	private static final X509TrustManager TRUST_ALL_CERTS = new X509TrustManager() {
		@Override
		public X509Certificate[] getAcceptedIssuers() { return EMPTY_ISSUERS; }
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
	};
}
