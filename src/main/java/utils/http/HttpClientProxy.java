package utils.http;

import okhttp3.OkHttpClient;

/**
 * {@link OkHttpClient}와 그 대상 엔드포인트를 함께 제공하는 프록시 추상화.
 * <p>
 * HTTP 호출에 필요한 클라이언트와 기본 엔드포인트를 한 쌍으로 노출하여, 호출 측이 둘을 묶어
 * 다룰 수 있게 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface HttpClientProxy {
	/**
	 * 사용할 OkHttp 클라이언트를 반환한다.
	 *
	 * @return OkHttp 클라이언트.
	 */
	public OkHttpClient getHttpClient();

	/**
	 * 대상 엔드포인트 URL을 반환한다.
	 *
	 * @return 엔드포인트 URL.
	 */
	public String getEndpoint();
}
