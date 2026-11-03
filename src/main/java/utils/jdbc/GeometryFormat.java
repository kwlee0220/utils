package utils.jdbc;


/**
 * JDBC로 공간(geometry) 컬럼 값을 주고받을 때 사용할 표현 형식.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum GeometryFormat {
	/** DBMS 고유의(드라이버가 제공하는) geometry 객체 형식. */
	NATIVE,
	/** WKB(Well-Known Binary) 바이트 배열 형식. */
	WKB,
	/** WKT(Well-Known Text) 문자열 형식. */
	WKT,
}
