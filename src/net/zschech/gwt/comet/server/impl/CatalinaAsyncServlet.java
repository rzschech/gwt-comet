package net.zschech.gwt.comet.server.impl;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSessionFacade;

/**
 * An extension of {@link BlockingAsyncServlet} for Catalina/Tomcat.
 * 
 * This extension improves on the default session keep alive stratagy,
 * refreshing the connection just before the session expires, by updating the
 * session managers last access time when ever sending data down the Comet
 * connection
 * 
 * @author Richard Zschech
 */
public class CatalinaAsyncServlet extends BlockingAsyncServlet {
	
	private Field sessionField;
	
	public CatalinaAsyncServlet() throws SecurityException, NoSuchFieldException {
		sessionField = StandardSessionFacade.class.getDeclaredField("session");
		sessionField.setAccessible(true);
	}
	
	@Override
	public ScheduledFuture<?> scheduleHeartbeat(CometServletResponseImpl response, CometSessionImpl session) {
		if (session != null) {
			// Keep the HTTP session alive when ever sending stuff
			try {
				HttpSession httpSession = session.getHttpSession();
				Session catalinaSession = (Session) sessionField.get(httpSession);
				catalinaSession.access();
			}
			catch (IllegalArgumentException e) {
				log("Error updating session last access time", e);
			}
			catch (IllegalAccessException e) {
				log("Error updating session last access time", e);
			}
		}
		return super.scheduleHeartbeat(response, session);
	}
	
	@Override
	public ScheduledFuture<?> scheduleSessionKeepAlive(CometServletResponseImpl response, CometSessionImpl session) {
		// The heartbeat scheduler also keeps the HTTP session alive
		return null;
	}
}
