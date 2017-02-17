package utils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Bean {
	private final Object m_src;
	private final PropertyDescriptor[] m_descs;
	
	public Bean(Object obj) throws IntrospectionException {
		m_src = obj;
		m_descs = Introspector.getBeanInfo(obj.getClass()).getPropertyDescriptors();
	}
	
	public Object getSourceObject() {
		return m_src;
	}
   
    public String[] getPropertyNames() {
        String[] propNames = new String[m_descs.length];
        for( int i=0; i< m_descs.length; i++){
            propNames[i] = m_descs[i].getName();
        }
        
        return propNames;
    }
    
    public Object getProperty(String propName) throws BeanPropertyNotFoundException,
    				IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	PropertyDescriptor desc = findPropertyDescriptor(propName);
    	if ( desc != null && desc.getReadMethod() != null ) {
            return desc.getReadMethod().invoke(m_src);
    	}

        throw new BeanPropertyNotFoundException("getProperty: name=" + propName);
    }
    
    public Object setProperty(String propName, Object value) throws BeanPropertyNotFoundException,
    				IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	PropertyDescriptor desc = findPropertyDescriptor(propName);
    	if ( desc != null && desc.getWriteMethod() != null ) {
        	return desc.getWriteMethod().invoke(m_src, value);
    	}

        throw new BeanPropertyNotFoundException("setProperty: name=" + propName);
    }
    
    public Class<?> getPropertyType(String propName) throws BeanPropertyNotFoundException {
    	PropertyDescriptor desc = findPropertyDescriptor(propName);
    	if ( desc != null ) {
        	return desc.getPropertyType();
    	}

        throw new BeanPropertyNotFoundException("getProperty: name=" + propName);
    }
    
    @Override
    public String toString() {
        StringBuffer result = new StringBuffer();
        
        result.append( "--- begin").append(m_src.getClass().getName());
        result.append(" hash: ").append(m_src.hashCode()).append(Utilities.getLineSeparator());
        
        for ( PropertyDescriptor desc: m_descs ) {
            try{
                result.append("Property: "+ desc.getName() + " Value: "
                			+ desc.getReadMethod().invoke(m_src));
            }
            catch ( IllegalAccessException iae ) {
                result.append("Property: "+ desc.getName() + " (Illegal Access to Value) ");
            }
            catch ( InvocationTargetException iae ) {
                result.append("Property: "+ desc.getName() + " (InvocationTargetException) "
                				+ iae.toString() );
            }
            catch ( Exception e ) {
                 result.append("Property: "+ desc.getName() + " (Other Exception )"
                		 		+ e.toString());
            }
            result.append(Utilities.getLineSeparator());
        }
        result.append("--- end ").append(m_src.getClass().getName());
        result.append(" hash: ").append(m_src.hashCode()); result.append(Utilities.getLineSeparator());
        
        return result.toString();
    }
    
    private PropertyDescriptor findPropertyDescriptor(String propName) {
        for( int i=0; i< m_descs.length; i++){
            if ( m_descs[i].getName().equals(propName) ) {
            	return m_descs[i];
            }
        }
        
        return null;
    }
}
