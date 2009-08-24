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
package net.zschech.gwt.comet.server;

import java.io.IOException;
import java.io.Serializable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.zschech.gwt.comet.server.impl.AsyncServlet;
import net.zschech.gwt.comet.server.impl.CometServletResponseImpl;
import net.zschech.gwt.comet.server.impl.CometSessionImpl;
import net.zschech.gwt.comet.server.impl.HTTPRequestCometServletResponse;
import net.zschech.gwt.comet.server.impl.IEHTMLFileCometServletResponse;
import net.zschech.gwt.comet.server.impl.OperaEventSourceCometServletResponse;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamWriter;

/**
 * This is the base class for application's Comet servlets. To process a Comet request override
 * {@link #doComet(CometServletResponse)} and send messages by calling {@link CometServletResponse#write(Serializable)}
 * or enqueue messages using {@link CometServletResponse#getSession()} and {@link CometSession#enqueue(Serializable)}.
 * 
 * @author Richard Zschech
 */
public abstract class CometServlet extends HttpServlet {
	
	private static final long serialVersionUID = 820972291784919880L;
	
	private int heartbeat = 15 * 1000; // 15 seconds by default
	
	private AsyncServlet async;
	
	public void setHeartbeat(int heartbeat) {
		this.heartbeat = heartbeat;
	}
	
	public int getHeartbeat() {
		return heartbeat;
	}
	
	@Override
	public void init() throws ServletException {
		ServletConfig servletConfig = getServletConfig();
		String heartbeat = servletConfig.getInitParameter("heartbeat");
		if (heartbeat != null) {
			this.heartbeat = Integer.parseInt(heartbeat);
		}
		async = AsyncServlet.create(getServletContext());
	}
	
	@Override
	protected void doGet(final HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		int requestHeartbeat = heartbeat;
		String requestedHeartbeat = request.getParameter("heartbeat");
		if (requestedHeartbeat != null) {
			try {
				requestHeartbeat = Integer.parseInt(requestedHeartbeat);
				if (requestHeartbeat <= 0) {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid heartbeat parameter");
					return;
				}
				requestHeartbeat = getHeartbeat();
			}
			catch (NumberFormatException e) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid heartbeat parameter");
				return;
			}
		}
		
		String accept = request.getHeader("Accept");
		String userAgent = request.getHeader("User-Agent");
		SerializationPolicy serializationPolicy = createSerializationPolicy();
		if ("text/plain".equals(accept)) {
			doCometImpl(new HTTPRequestCometServletResponse(request, response, serializationPolicy, this, async, requestHeartbeat));
		}
		else if (userAgent != null && userAgent.contains("Opera")) {
			doCometImpl(new OperaEventSourceCometServletResponse(request, response, serializationPolicy, this, async, requestHeartbeat));
		}
		else {
			doCometImpl(new IEHTMLFileCometServletResponse(request, response, serializationPolicy, this, async, requestHeartbeat));
		}
	}
	
	private void doCometImpl(CometServletResponseImpl response) throws ServletException, IOException {
		
		response.initiate();
		
		doComet(response);
		if (!response.isTerminated()) {
			response.suspend();
		}
	}
	
	/**
	 * Override this method to process a new comet request. All required information from the {@link HttpServletRequest}
	 * must be retrieved {@link CometServletResponse#getRequest()} in this method as it will not be available after this
	 * method returns and the request is suspended. This method may write data to the Comet response but should not
	 * block. Writing data from this method before the request is suspended can improve the efficiency because padding
	 * data may not be needed to cause the browser to start processing the stream.
	 * 
	 * @param cometResponse
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void doComet(CometServletResponse cometResponse) throws ServletException, IOException {
	}
	
	/**
	 * Override this method to be notified of the Comet connection being terminated.
	 * 
	 * @param cometResponse
	 * @param serverInitiated
	 */
	public void cometTerminated(CometServletResponse cometResponse, boolean serverInitiated) {
		
	}
	
	/**
	 * Override this method to override the requested heartbeat. By default only requested heartbeats > this.heartbeat
	 * are allowed.
	 * 
	 * @param requestedHeartbeat
	 * @return
	 */
	protected int getHeartbeat(int requestedHeartbeat) {
		return requestedHeartbeat < heartbeat ? heartbeat : requestedHeartbeat;
	}
	
	protected SerializationPolicy createSerializationPolicy() {
		return new SerializationPolicy() {
			@Override
			public boolean shouldDeserializeFields(final Class<?> clazz) {
				throw new UnsupportedOperationException("shouldDeserializeFields");
			}
			
			@Override
			public boolean shouldSerializeFields(final Class<?> clazz) {
				return Object.class != clazz;
			}
			
			@Override
			public void validateDeserialize(final Class<?> clazz) {
				throw new UnsupportedOperationException("validateDeserialize");
			}
			
			@Override
			public void validateSerialize(final Class<?> clazz) {
			}
		};
	}
	
	/**
	 * Utility to GWT serialize an object to a String.
	 * @param message
	 * @param serializationPolicy
	 * @return the serialized message
	 * @throws SerializationException
	 */
	public static String serialize(Serializable message, SerializationPolicy serializationPolicy) throws SerializationException {
		ServerSerializationStreamWriter streamWriter = new ServerSerializationStreamWriter(serializationPolicy);
		streamWriter.prepareToWrite();
		streamWriter.writeObject(message);
		return streamWriter.toString();
	}
	
	public static CometSession getCometSession(HttpSession httpSession) {
		return getCometSession(httpSession, new ConcurrentLinkedQueue<Serializable>());
	}
	
	public static CometSession getCometSession(HttpSession httpSession, Queue<Serializable> queue) {
		return getCometSession(httpSession, true, queue);
	}
	
	public static CometSession getCometSession(HttpSession httpSession, boolean create) {
		return getCometSession(httpSession, true, new ConcurrentLinkedQueue<Serializable>());
	}
	
	public static CometSession getCometSession(HttpSession httpSession, boolean create, Queue<Serializable> queue) {
		CometSessionImpl session = (CometSessionImpl) httpSession.getAttribute(CometSession.HTTP_SESSION_KEY);
		if (session == null) {
			if (create) {
				session = new CometSessionImpl(httpSession, queue, AsyncServlet.create(httpSession.getServletContext()));
				httpSession.setAttribute(CometSession.HTTP_SESSION_KEY, session);
			}
		}
		
		return session;
	}
}
