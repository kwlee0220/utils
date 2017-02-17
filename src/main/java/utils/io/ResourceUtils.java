package utils.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.stream.Collectors;


public abstract class ResourceUtils {
	public static final String CLASSPATH_URL_PREFIX = "classpath:";
	public static final String URL_PROTOCOL_FILE = "file";

	public static boolean isUrl(String location) {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			return true;
		}
		try {
			new URL(location);
			return true;
		}
		catch ( MalformedURLException e ) {
			return false;
		}
	}

	public static URL getURL(String location) throws FileNotFoundException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			URL url = Thread.currentThread().getContextClassLoader().getResource(path);
			if ( url == null ) {
				throw new FileNotFoundException("Resource [" + path + "] not found");
			}
			return url;
		}
		try {
			return new URL(location);
		}
		catch ( MalformedURLException ex ) {
			try {
				return new URL("file:" + location);
			}
			catch ( MalformedURLException ex2 ) {
				throw new FileNotFoundException("Resource location [" + location
						+ "] is neither a URL not a well-formed file path");
			}
		}
	}
	
	public static InputStream openInputStream(String location) throws IOException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			return Thread.currentThread()
						.getContextClassLoader()
						.getResource(path)
						.openStream();
		}
		else if ( location.startsWith(URL_PROTOCOL_FILE) ) {
			return new URL(location).openStream();
		}
		else {
			throw new IllegalArgumentException("invalid resource loc: " + location);
		}
	}

	public static File getFile(String location) throws FileNotFoundException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			URL url = Thread.currentThread().getContextClassLoader().getResource(path);
			if ( url == null ) {
				throw new FileNotFoundException("Resource[" + path + "] not found");
			}
			return getFile(url);
		}
		try {
			return getFile(new URL(location));
		}
		catch ( MalformedURLException e ) {
			return new File(location);
		}
	}

	public static File getFile(URL resourceUrl) throws FileNotFoundException {
		if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {
			throw new FileNotFoundException("Resource[" + resourceUrl + "] not found");
		}
		
//		return new File(URLDecoder.decode(resourceUrl.getFile()));
		try {
			return new File(URLDecoder.decode(resourceUrl.getFile(), "UTF-8"));
		}
		catch ( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
	}
	
	public static List<String> getTextLines(String location) throws IOException {
		try ( InputStream is = openInputStream(location);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is)) ) {
			return reader.lines().collect(Collectors.toList());
		}
	}
}
