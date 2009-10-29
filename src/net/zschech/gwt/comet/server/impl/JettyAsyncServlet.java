/*
 * Copyright 2009 Richard Zschech.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.zschech.gwt.comet.server.impl;

import java.io.Flushable;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.Context.SContext;

/**
 * An extension of {@link BlockingAsyncServlet} for Jetty.
 * 
 * This extension improves on the default session keep alive stratagy, refreshing the connection just before the session
 * expires, by updating the session managers last access time when ever sending data down the Comet connection
 * 
 * @author Richard Zschech
 */
public class JettyAsyncServlet extends BlockingAsyncServlet {
	
	private SessionManager sessionManager;
	
	@Override
	public void init(ServletContext context) throws ServletException {
		super.init(context);
		sessionManager = ((Context) ((SContext) context).getContextHandler()).getSessionHandler().getSessionManager();
	}
	
	@Override
	public ScheduledFuture<?> scheduleHeartbeat(CometServletResponseImpl response, CometSessionImpl session) {
		if (session != null) {
			// Keep the HTTP session alive when ever sending stuff
			sessionManager.access(session.getHttpSession(), false);
		}
		return super.scheduleHeartbeat(response, session);
	}
	
	@Override
	public ScheduledFuture<?> scheduleSessionKeepAlive(CometServletResponseImpl response, CometSessionImpl session) {
		// The heartbeat scheduler also keeps the HTTP session alive
		return null;
	}
	
	@Override
	public Flushable getFlushable(CometServletResponseImpl response) throws IOException {
		// A hack to get SSL AppOutputStream to flush the underlying socket.
		// The property path below only works on Jetty.
		if (response.getRequest().getScheme().equals("https")) {
			return (Flushable) get("_generator._endp._socket.sockOutput", response.getResponse().getOutputStream());
		}
		else {
			return null;
		}
	}
}
