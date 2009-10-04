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
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.zschech.gwt.comet.server.CometServlet;
import net.zschech.gwt.comet.server.CometServletResponse;
import net.zschech.gwt.comet.server.CometSession;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.SerializationPolicy;

public abstract class CometServletResponseImpl implements CometServletResponse {
	
	private HttpServletRequest request;
	private final HttpServletResponse response;
	private CometSessionImpl session;
	
	private final SerializationPolicy serializationPolicy;
	private final CometServlet servlet;
	private final AsyncServlet async;
	private final int heartbeat;
	
	private Flushable flushable;
	protected Writer writer;
	
	private volatile boolean terminated;
	private volatile boolean suspended;
	
	private Object suspendInfo;
	private ScheduledFuture<?> heartbeatFuture;
	
	protected CometServletResponseImpl(HttpServletRequest request, HttpServletResponse response, SerializationPolicy serializationPolicy, CometServlet servlet, AsyncServlet async, int heartbeat) {
		this.request = request;
		this.response = response;
		this.serializationPolicy = serializationPolicy;
		this.servlet = servlet;
		this.async = async;
		this.heartbeat = heartbeat;
	}
	
	@Override
	public int getHeartbeat() {
		return heartbeat;
	}
	
	@Override
	public boolean isTerminated() {
		return terminated;
	}
	
	protected OutputStream getOutputStream(OutputStream outputStream) {
		return outputStream;
	}
	
	@Override
	public HttpServletRequest getRequest() {
		if (suspended) {
			throw new IllegalStateException("HttpServletRequest can not be accessed after the CometServletResponse has been suspended.");
		}
		return request;
	}
	
	@Override
	public HttpServletResponse getResponse() {
		return response;
	}
	
	@Override
	public CometSession getSession() {
		return getSession(true);
	}
	
	@Override
	public CometSession getSession(boolean create) {
		if (suspended) {
			throw new IllegalStateException("CometSession can not be accessed after the CometServletResponse has been suspended.");
		}
		if (session != null) {
			return session;
		}
		HttpSession httpSession = getRequest().getSession(create);
		if (httpSession == null) {
			return null;
		}
		
		session = (CometSessionImpl) CometServlet.getCometSession(httpSession, create, create ? new ConcurrentLinkedQueue<Serializable>() : null);
		if (create) {
			async.initiateSession(this, session);
		}
		return session;
	}
	
	public void sendError(int statusCode) throws IOException {
		sendError(statusCode, null);
	}
	
	@Override
	public void sendError(int statusCode, String message) throws IOException {
		getResponse().reset();
		response.setHeader("Cache-Control", "no-cache");
		response.setCharacterEncoding("UTF-8");
		
		OutputStream outputStream = response.getOutputStream();
		writer = new OutputStreamWriter(outputStream, "UTF-8");
		
		doSendError(statusCode, message);
		setTerminated(true);
	}
	
	public void initiate() throws IOException {
		getSession(false);
		async.initiateResponse(this, session);
		
		response.setHeader("Cache-Control", "no-cache");
		response.setCharacterEncoding("UTF-8");
		
		OutputStream outputStream = response.getOutputStream();
		// A hack to get SSL AppOutputStream to flush the underlying socket. The property path below only works on
		// Jetty.
		// Need to extend this to support more servers and work in secure environments
		// if (request.getScheme().equals("https")) {
		// flushable = (Flushable) get("_generator._endp._socket.sockOutput", outputStream);
		// }
		String acceptEncoding = request.getHeader("Accept-Encoding");
		if (acceptEncoding != null && acceptEncoding.contains("deflate")) {
			response.setHeader("Content-Encoding", "deflate");
			outputStream = new FlushableDeflaterOutputStream(outputStream);
		}
		
		outputStream = getOutputStream(outputStream);
		writer = new OutputStreamWriter(outputStream, "UTF-8");
		
		startHeartbeatTimer();
	}
	
	public void suspend() throws IOException {
		flush();
		
		suspended = true;
		suspendInfo = async.suspend(this, session);
		
		// Don't hold onto the request while suspended as it takes up memory.
		// Also Jetty and possibly other web servers reuse the
		// HttpServletRequests so we can't assume
		// they are still valid after they have been suspended
		request = null;
	}
	
	public void setSuspendInfo(Object suspendInfo) {
		this.suspendInfo = suspendInfo;
	}
	
	@Override
	public synchronized void terminate() throws IOException {
		if (!terminated) {
			doTerminate();
			flush();
			setTerminated(true);
		}
	}
	
	@Override
	public void write(Serializable message) throws IOException {
		write(Collections.singletonList(message), true);
	}
	
	@Override
	public void write(Serializable message, boolean flush) throws IOException {
		write(Collections.singletonList(message), flush);
	}
	
	@Override
	public void write(List<? extends Serializable> messages) throws IOException {
		write(messages, true);
	}
	
	@Override
	public synchronized void write(List<? extends Serializable> messages, boolean flush) throws IOException {
		try {
			doWrite(messages);
			if (flush) {
				flush();
			}
			startHeartbeatTimer();
		}
		catch (IOException e) {
			servlet.log("Error writing data", e);
			setTerminated(false);
			throw e;
		}
	}
	
	@Override
	public synchronized void heartbeat() throws IOException {
		if (!terminated) {
			try {
				doHeartbeat();
				flush();
				startHeartbeatTimer();
			}
			catch (IOException e) {
				setTerminated(false);
				throw e;
			}
		}
	}
	
	private void flush() throws IOException {
		writer.flush();
		if (flushable != null) {
			flushable.flush();
		}
	}
	
	private static boolean logged = false;
	
	private Object get(String path, Object object) {
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
			if (!logged) {
				servlet.log("Error accessing underlying socket output stream to improve flushing", e);
				logged = true;
			}
			return null;
		}
	}
	
	void tryHeartbeat() {
		try {
			heartbeat();
		}
		catch (IOException e) {
			servlet.log("Error sending heartbeat", e);
		}
	}
	
	private void startHeartbeatTimer() {
		if (heartbeatFuture != null) {
			heartbeatFuture.cancel(false);
		}
		heartbeatFuture = async.scheduleHeartbeat(this);
	}
	
	protected abstract void doSendError(int statusCode, String message) throws IOException;
	
	protected abstract void doWrite(List<? extends Serializable> messages) throws IOException;
	
	protected abstract void doHeartbeat() throws IOException;
	
	protected abstract void doTerminate() throws IOException;
	
	protected void setTerminated(boolean serverInitiated) {
		terminated = true;
		if (heartbeatFuture != null) {
			heartbeatFuture.cancel(false);
			heartbeatFuture = null;
		}
		
		if (serverInitiated) {
			try {
				writer.close();
			}
			catch (IOException e) {
				servlet.log("Error closing connection", e);
			}
		}
		
		if (suspended) {
			async.terminate(this, session, suspendInfo);
		}
		
		servlet.cometTerminated(this, serverInitiated);
	}
	
	protected String serialize(Serializable message) throws NotSerializableException {
		try {
			return CometServlet.serialize(message, serializationPolicy);
		}
		catch (SerializationException e) {
			throw new NotSerializableException("Unable to serialize object, message: " + e.getMessage());
		}
	}
}
