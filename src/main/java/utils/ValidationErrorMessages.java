package utils;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ValidationErrorMessages {
	private final List<String> m_messages = Lists.newArrayList();
	
	public ValidationErrorMessages addMessage(String msg) {
		m_messages.add(msg);
		return this;
	}
	
	public boolean isEmpty() {
		return m_messages.isEmpty();
	}
	
	public int getMessageCount() {
		return m_messages.size();
	}
	
	public List<String> getMessages() {
		return Collections.unmodifiableList(m_messages);
	}
}
