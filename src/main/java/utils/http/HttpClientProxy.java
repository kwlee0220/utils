package utils.http;

import okhttp3.OkHttpClient;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface HttpClientProxy {
	public OkHttpClient getHttpClient();
	
	public String getEndpoint();
}
