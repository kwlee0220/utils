package utils.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import okhttp3.OkHttpClient;

/**
 * {@link OkHttpClientUtils} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OkHttpClientUtilsTest {
	@Test
	public void newClientNotNull() {
		assertNotNull(OkHttpClientUtils.newClient());
	}

	@Test
	public void newTrustAllClientBuildsWithTls() {
		// "TLS" SSLContext로 trust-all 클라이언트가 예외 없이 생성되어야 한다.
		OkHttpClient client = assertDoesNotThrow(OkHttpClientUtils::newTrustAllOkHttpClient);
		assertNotNull(client);
		assertNotNull(client.hostnameVerifier());
		assertNotNull(client.sslSocketFactory());
	}

	@Test
	public void newTrustAllBuilderReturnsConfigurableBuilder() {
		OkHttpClient.Builder builder = assertDoesNotThrow(OkHttpClientUtils::newTrustAllOkHttpClientBuilder);
		assertNotNull(builder);
		assertNotNull(builder.build());
	}
}
