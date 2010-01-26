package net.zschech.gwt.comet.server.impl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.servlet.http.HttpSession;

/**
 * An extension of {@link BlockingAsyncServlet} for Catalina/Tomcat 5.5.
 * 
 * This extension improves on the default session keep alive strategy, refreshing the connection just before the session
 * expires, by updating the session managers last access time when ever sending data down the Comet connection
 * 
 * @author Richard Zschech
 */
public class Catalina55AsyncServlet extends BlockingAsyncServlet {
	
	private Field sessionField;
	private Method accessMethod;
	
	@Override
	protected boolean access(HttpSession httpSession) {
		try {
			Field sessionField = getSessionField(httpSession);
			sessionField.setAccessible(true);
			Object catalinaSession = sessionField.get(httpSession);
			Method accessMethod = getAccessMethod(catalinaSession);
			accessMethod.invoke(catalinaSession, new Object[0]);
			return true;
		}
		catch (IllegalArgumentException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (IllegalAccessException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (SecurityException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (NoSuchFieldException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (NoSuchMethodException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (InvocationTargetException e) {
			log("Error updating session last access time", e);
			return false;
		}
	}
	
	private Field getSessionField(HttpSession httpSession) throws SecurityException, NoSuchFieldException {
		if (sessionField == null) {
			sessionField = httpSession.getClass().getDeclaredField("session");
			sessionField.setAccessible(true);
		}
		return sessionField;
	}
	
	private Method getAccessMethod(Object catalinaSession) throws SecurityException, NoSuchMethodException {
		if (accessMethod == null) {
			accessMethod = catalinaSession.getClass().getMethod("access", new Class[0]);
		}
		return accessMethod;
	}
}
