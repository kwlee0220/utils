package utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;


/**
 * 
 * @author Kang-Woo Lee
 */
public class NetUtils {
	private NetUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + NetUtils.class.getName());
	}
	
	/** 'localhost'에 해당하는 IP 주소 */
	public static final String LOCAL_HOST;
    static {
		try {
			LOCAL_HOST = InetAddress.getLocalHost().getHostAddress();
		}
		catch ( UnknownHostException e ) {
			throw new RuntimeException(e);
		}
    }
	
	public static String resolveLocalhost(String host) {
		if ( host.equals("localhost") ) {
			return LOCAL_HOST;
		}
		else {
			return host;
		}
	}
	
	public static String getLocalHostAddress() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		}
		catch ( UnknownHostException e ) {
			throw new RuntimeException(e);
		}
	}
	
	public static List<NetworkInterface> getNetworkInterfaces(boolean ignoreLoopback)
		throws SocketException {
		Enumeration<NetworkInterface> intfcEnum = NetworkInterface.getNetworkInterfaces();
		if ( !ignoreLoopback ) {
			return Collections.list(intfcEnum);
		}

		List<NetworkInterface> intfcList = new ArrayList<NetworkInterface>();
		while ( intfcEnum.hasMoreElements() ) {
			NetworkInterface intfc = intfcEnum.nextElement();
			if ( !intfc.isLoopback() ) {
				intfcList.add(intfc);
			}
		}
		
		return intfcList;
	}
	
	public static List<String> getLocalHostAddresses() throws SocketException {
		List<NetworkInterface> niList = getNetworkInterfaces(true);
		List<String> addrList = new ArrayList<String>(niList.size());
		for ( NetworkInterface ni: niList ) {
			Enumeration<InetAddress> inets = ni.getInetAddresses();
			while ( inets.hasMoreElements() ) {
				InetAddress inet = (InetAddress)inets.nextElement();
				addrList.add(inet.getHostAddress());
			}
		}
		
		return addrList;
	}
	
	public static List<NetworkInterface> getRunningNetworkInterfaces(boolean ignoreLoopback)
		throws SocketException {
		List<NetworkInterface> upList = new ArrayList<NetworkInterface>();
		
		Enumeration<NetworkInterface> intfcEnum = NetworkInterface.getNetworkInterfaces();
		while ( intfcEnum.hasMoreElements() ) {
			NetworkInterface intfc = intfcEnum.nextElement();
			if ( intfc.isUp() && (ignoreLoopback && !intfc.isLoopback()) ) {
				upList.add(intfc);
			}
		}
		
		return upList;
	}
	
	public static String getMACString(NetworkInterface nwIntfc) throws SocketException {
		byte[] macBytes = nwIntfc.getHardwareAddress();
		
		if ( macBytes != null ) {
			StringBuilder builder = new StringBuilder();
			for ( int i =0; i < macBytes.length - 1; ++i ) {
				builder.append(String.format("%02X:", macBytes[i]));
			}
			builder.append(String.format("%02X", macBytes[macBytes.length - 1]));
			
			return builder.toString();
		}
		else {
			return null;
		}
	}
	
	public static NetworkInterface findNetworkInterfaceByMAC(String macStr) throws SocketException {
		String[] parts = macStr.split(":");
		if ( parts.length != 6 ) {
			throw new IllegalArgumentException("invalid MAC Address=" + macStr);
		}
		
		byte[] mac = new byte[parts.length];
		for ( int i =0; i < mac.length; ++i ) {
			mac[i] = Short.valueOf(parts[i], 16).byteValue();
		}
		
		Enumeration<NetworkInterface> intfcEnum = NetworkInterface.getNetworkInterfaces();
		while ( intfcEnum.hasMoreElements() ) {
			NetworkInterface intfc = intfcEnum.nextElement();
			
			if ( Arrays.equals(mac, intfc.getHardwareAddress()) ) {
				return intfc;
			}
		}
		
		return null;
	}
}
