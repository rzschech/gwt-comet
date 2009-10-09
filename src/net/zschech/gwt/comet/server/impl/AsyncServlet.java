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

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

public abstract class AsyncServlet {
	
	public void init(ServletContext context) throws ServletException {
	}
	
	public abstract Object suspend(CometServletResponseImpl response, CometSessionImpl session) throws IOException;
	
	public abstract void terminate(CometServletResponseImpl response, CometSessionImpl session, Object suspendInfo);
	
	public abstract void invalidate(CometSessionImpl cometSessionImpl);
	
	public abstract void enqueued(CometSessionImpl session);
	
	public abstract ScheduledFuture<?> scheduleHeartbeat(CometServletResponseImpl response);
	
	private static AsyncServlet async;
	
	public static AsyncServlet create(ServletContext context) {
		if (async == null) {
			// TODO work out which implementation of AsyncServlet to create based on the web server were running on.
			async = new BlockingAsyncServlet();
			try {
				async.init(context);
			}
			catch (ServletException e) {
				throw new RuntimeException("Error setting up async servlet");
			}
		}
		return async;
	}
}
