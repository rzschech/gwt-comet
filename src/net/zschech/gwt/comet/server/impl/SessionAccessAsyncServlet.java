package net.zschech.gwt.comet.server.impl;

import java.util.concurrent.ScheduledFuture;

import javax.servlet.http.HttpSession;

public abstract class SessionAccessAsyncServlet extends BlockingAsyncServlet {
	
	@Override
	public ScheduledFuture<?> scheduleHeartbeat(CometServletResponseImpl response, CometSessionImpl session) {
		if (session != null) {
			access(session.getHttpSession());
		}
		return super.scheduleHeartbeat(response, session);
	}
	
	@Override
	public ScheduledFuture<?> scheduleSessionKeepAlive(CometServletResponseImpl response, CometSessionImpl session) {
		// The heartbeat scheduler also keeps the HTTP session alive
		return null;
	}
	
	protected abstract void access(HttpSession httpSession);
}
