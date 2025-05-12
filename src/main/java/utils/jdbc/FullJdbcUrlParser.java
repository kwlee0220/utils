package utils.jdbc;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FullJdbcUrlParser {
    public static Map<String, String> parse(String fullJdbcUrl) {
        Map<String, String> parsed = new HashMap<>();

        int questionMarkIndex = fullJdbcUrl.indexOf('?');
        if (questionMarkIndex == -1) return parsed;
        
        parsed.put("jdbcUrl", fullJdbcUrl.substring(0, questionMarkIndex));

        String queryPart = fullJdbcUrl.substring(questionMarkIndex + 1);
        String[] pairs = queryPart.split("&");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
					String key = URLDecoder.decode(kv[0], "UTF-8");
					String value = URLDecoder.decode(kv[1], "UTF-8");
					parsed.put(key, value);
				}
				catch ( UnsupportedEncodingException e ) {
					throw new IllegalArgumentException("Failed to decode URL parameters", e);
				}
            }
        }

        return parsed;
    }
}
