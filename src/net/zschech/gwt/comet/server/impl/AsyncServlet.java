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
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

public abstract class AsyncServlet {
	
	public static final String SERVLET_CONTEXT_KEY = "net.zschech.gwt.comet.server.AsyncServlet";
	
	public static AsyncServlet initialize(ServletContext context) {
		synchronized (context) {
			AsyncServlet async = (AsyncServlet) context.getAttribute(SERVLET_CONTEXT_KEY);
			if (async == null) {
				String server;
				String serverInfo = context.getServerInfo();
				if (serverInfo.startsWith("jetty-6") || serverInfo.startsWith("jetty/6")) {
					// e.g. jetty-6.1.x
					server = "Jetty6";
				}
				else if (serverInfo.startsWith("jetty/7")) {
					server = "Jetty7";
				}
				else if (serverInfo.startsWith("Apache Tomcat/5.5.")) {
					// e.g. Apache Tomcat/5.5.26
					server = "Catalina55";
				}
				else if (serverInfo.startsWith("Apache Tomcat/6.")) {
					// e.g. Apache Tomcat/6.0.18
					server = "Catalina60";
				}
				else if (serverInfo.startsWith("GlassFish v3")) {
					server = "Grizzly";
				}
				else if (serverInfo.startsWith("Google App Engine/")) {
					server = "GAE";
				}
				else {
					server = null;
				}
				
				if (server != null) {
					context.log("Creating " + server + " async servlet handler for server " + serverInfo);
					try {
						async = (AsyncServlet) Class.forName("net.zschech.gwt.comet.server.impl." + server + "AsyncServlet").newInstance();
					}
					catch (Throwable e) {
						context.log("Error creating " + server + " async servlet handler for server " + serverInfo + ". Falling back to default blocking async servlet handler.", e);
						async = new BlockingAsyncServlet();
					}
				}
				else {
					context.log("Creating blocking async servlet handler for server " + serverInfo);
					async = new BlockingAsyncServlet();
				}
				
				try {
					try {
						async.init(context);
					}
					catch (Throwable e) {
						context.log("Error initiating " + server + " async servlet handler for server " + serverInfo + ". Falling back to default blocking async servlet handler.", e);
						context.log("Creating blocking async servlet handler for server " + serverInfo);
						async = new BlockingAsyncServlet();
						async.init(context);
					}
					context.setAttribute(SERVLET_CONTEXT_KEY, async);
				}
				catch (ServletException e) {
					throw new Error("Error setting up async servlet");
				}
			}
			return async;
		}
	}
	
	public static void destroy(ServletContext context) {
		synchronized (context) {
			AsyncServlet async = (AsyncServlet) context.getAttribute(SERVLET_CONTEXT_KEY);
			if (async != null) {
				async.shutdown();
			}
		}
	}
	
	private ServletContext context;
	
	protected void init(ServletContext context) throws ServletException {
		this.context = context;
	}
	
	protected void shutdown() {
	}
	
	protected ServletContext getServletContext() {
		return context;
	}
	
	protected void log(String message) {
		context.log(message);
	}
	
	protected void log(String message, Throwable throwable) {
		context.log(message, throwable);
	}
	
	public OutputStream getOutputStream(OutputStream outputStream) {
		return outputStream;
	}
	
	public abstract Object suspend(CometServletResponseImpl response, CometSessionImpl session) throws IOException;
	
	public abstract void terminate(CometServletResponseImpl response, CometSessionImpl session, boolean serverInitiated, Object suspendInfo);
	
	public abstract void invalidate(CometSessionImpl cometSessionImpl);
	
	public abstract void enqueued(CometSessionImpl session);
	
	protected boolean access(HttpSession httpSession) {
		return false;
	}
	
	public ScheduledFuture<?> scheduleHeartbeat(CometServletResponseImpl response, CometSessionImpl session) {
		return null;
	}
	
	public ScheduledFuture<?> scheduleSessionKeepAlive(CometServletResponseImpl response, CometSessionImpl session) {
		return null;
	}
	
	public Flushable getFlushable(CometServletResponseImpl response) throws IOException {
		return null;
	}
	
	protected Object get(String path, Object object) {
		try {
			for (String property : path.split("\\.")) {
				Class<?> c = object.getClass();
				while (true) {
					try {
						Field field = c.getDeclaredField(property);
						field.setAccessible(true);
						object = field.get(object);
						break;
					}
					catch (NoSuchFieldException e) {
						c = c.getSuperclass();
						if (c == null) {
							throw e;
						}
					}
				}
			}
			return object;
		}
		catch (Exception e) {
			log("Error accessing underlying objects " + path + " from " + object.getClass().getCanonicalName(), e);
			return null;
		}
	}
}
