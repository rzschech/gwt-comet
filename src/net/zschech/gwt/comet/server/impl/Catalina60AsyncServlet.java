package net.zschech.gwt.comet.server.impl;

import java.lang.reflect.Field;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSessionFacade;

/**
 * An extension of {@link BlockingAsyncServlet} for Catalina/Tomcat 6.
 * 
 * This extension improves on the default session keep alive strategy, refreshing the connection just before the session
 * expires, by updating the session managers last access time when ever sending data down the Comet connection
 * 
 * @author Richard Zschech
 */
public class Catalina60AsyncServlet extends BlockingAsyncServlet {
	
	private final Field sessionField;
	
	public Catalina60AsyncServlet() throws SecurityException, NoSuchFieldException {
		sessionField = StandardSessionFacade.class.getDeclaredField("session");
		sessionField.setAccessible(true);
	}
	
	@Override
	protected boolean access(HttpSession httpSession) {
		try {
			Session catalinaSession = (Session) sessionField.get(httpSession);
			catalinaSession.access();
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
	}
}
