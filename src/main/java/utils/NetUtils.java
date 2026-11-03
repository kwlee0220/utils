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
 * 로컬 호스트의 IP 주소와 네트워크 인터페이스를 조회하는 정적 유틸리티.
 *
 * @author Kang-Woo Lee
 */
public class NetUtils {
	private NetUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + NetUtils.class.getName());
	}
	
	/**
	 * 'localhost'에 해당하는 IP 주소({@link InetAddress#getLocalHost()}의 주소).
	 * <p>
	 * 클래스를 초기화할 때 한 번 조회한다. 조회에 실패하면 클래스 초기화가 실패한다.
	 */
	public static final String LOCAL_HOST;
    static {
		try {
			LOCAL_HOST = InetAddress.getLocalHost().getHostAddress();
		}
		catch ( UnknownHostException e ) {
			throw new RuntimeException(e);
		}
    }
	
	/**
	 * 호스트 이름이 정확히 {@code "localhost"}이면 {@link #LOCAL_HOST}로 바꾸고, 그 밖에는 그대로 반환한다.
	 *
	 * @param host	호스트 이름.
	 * @return	바뀐 호스트 이름.
	 */
	public static String resolveLocalhost(String host) {
		if ( host.equals("localhost") ) {
			return LOCAL_HOST;
		}
		else {
			return host;
		}
	}
	
	/**
	 * 로컬 호스트의 IP 주소를 조회한다. {@link #LOCAL_HOST}와 달리 부를 때마다 새로 조회한다.
	 *
	 * @return	로컬 호스트의 IP 주소 문자열.
	 * @throws RuntimeException	로컬 호스트 주소를 알 수 없는 경우({@link UnknownHostException}을 감쌈).
	 */
	public static String getLocalHostAddress() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		}
		catch ( UnknownHostException e ) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * 이 호스트의 네트워크 인터페이스 목록을 반환한다. 동작 상태(up/down)는 따지지 않는다.
	 *
	 * @param ignoreLoopback	{@code true}이면 루프백 인터페이스를 뺀다.
	 * @return	네트워크 인터페이스 목록.
	 * @throws SocketException	인터페이스 목록을 얻지 못한 경우.
	 */
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
	
	/**
	 * 루프백이 아닌 모든 네트워크 인터페이스의 IP 주소를 반환한다. IPv4와 IPv6 주소를 모두 포함한다.
	 *
	 * @return	IP 주소 문자열 목록.
	 * @throws SocketException	인터페이스 정보를 얻지 못한 경우.
	 */
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
	
	/**
	 * 동작 중(up)인 네트워크 인터페이스 중 루프백이 아닌 것을 반환한다.
	 * <p>
	 * <b>주의:</b> 현재 구현은 {@code ignoreLoopback}이 {@code false}이면 항상 빈 목록을 반환한다(조건식이
	 * {@code ignoreLoopback && !isLoopback()}이므로). 루프백을 포함한 목록이 필요하면 이 메소드를 쓰지 않는다.
	 *
	 * @param ignoreLoopback	루프백 인터페이스를 뺄지 여부. 현재는 {@code true}일 때만 의미 있는 결과를 낸다.
	 * @return	동작 중인 네트워크 인터페이스 목록.
	 * @throws SocketException	인터페이스 정보를 얻지 못한 경우.
	 */
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
	
	/**
	 * 네트워크 인터페이스의 MAC 주소를 {@code "AA:BB:CC:DD:EE:FF"} 형식(대문자 16진수)으로 반환한다.
	 *
	 * @param nwIntfc	네트워크 인터페이스.
	 * @return	MAC 주소 문자열. 하드웨어 주소가 없으면(루프백 등) {@code null}.
	 * @throws SocketException	하드웨어 주소를 얻지 못한 경우.
	 */
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
	
	/**
	 * MAC 주소로 네트워크 인터페이스를 찾는다.
	 *
	 * @param macStr	{@code ':'}로 구분한 여섯 개의 16진수로 이루어진 MAC 주소(예: {@code "aa:bb:cc:dd:ee:ff"},
	 * 					대소문자 무관).
	 * @return	MAC 주소가 같은 인터페이스. 없으면 {@code null}.
	 * @throws IllegalArgumentException	{@code macStr}이 여섯 부분으로 나뉘지 않는 경우.
	 * @throws NumberFormatException	16진수가 아닌 부분이 있는 경우.
	 * @throws SocketException	인터페이스 정보를 얻지 못한 경우.
	 */
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
