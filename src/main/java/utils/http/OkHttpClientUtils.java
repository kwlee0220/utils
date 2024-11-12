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
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OkHttpClientUtils {
	private OkHttpClientUtils() {
		throw new AssertionError("Should not be called: class=" + OkHttpClientUtils.class);
	}
	
	public static OkHttpClient newClient() {
		return new OkHttpClient.Builder().build();
	}
	
	public static OkHttpClient newTrustAllOkHttpClient() throws KeyManagementException, NoSuchAlgorithmException {
		return newTrustAllOkHttpClientBuilder().build();
	}
	
	public static OkHttpClient.Builder newTrustAllOkHttpClientBuilder()
		throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance("SSL");
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
