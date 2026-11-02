package utils.stream;


/**
 * 정렬 순서.
 * <p>
 * {@link FStream#takeTopK(int, SortOrder)} 등에서 원소를 고르고 내보내는 순서를 지정한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum SortOrder {
	/** 오름차순: 작은 값부터. */
	ASCENDING,
	/** 내림차순: 큰 값부터. */
	DESCENDING;
}
