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
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

/**
 * This AsyncServlet implementation blocks the HTTP request processing thread.
 * 
 * It does not generate notifications for client disconnection and therefore must wait for a heartbeat send attempt to
 * detect client disconnection :-(
 * 
 * @author Richard Zschech
 */
public class BlockingAsyncServlet extends AsyncServlet {
	
	private static final long SESSION_KEEP_ALIVE_BUFFER = 10000;
	
	private ScheduledExecutorService executor;
	
	public static String BATCH_SIZE = "net.zschech.gwt.comet.server.batch.size";
	
	private int batchSize = 10;
	
	@Override
	public void init(ServletContext context) throws ServletException {
		super.init(context);
		
		executor = new RemoveOnCancelScheduledThreadPoolExecutor(1, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				return new Thread(runnable, "gwt-comet heatbeat " + getServletContext().getServletContextName());
			}
		});
		
		String batchSizeString = context.getInitParameter(BATCH_SIZE);
		if (batchSizeString != null) {
			try {
				batchSize = Integer.parseInt(batchSizeString);
				if (batchSize <= 0) {
					throw new ServletException("Invalid " + BATCH_SIZE + " value: " + batchSizeString);
				}
			}
			catch (NumberFormatException e) {
				throw new ServletException("Invalid " + BATCH_SIZE + " value: " + batchSizeString);
			}
		}
	}
	
	@Override
	public Object suspend(CometServletResponseImpl response, CometSessionImpl session) throws IOException {
		assert !Thread.holdsLock(response);
		
		if (session == null) {
			try {
				synchronized (response) {
					while (!response.isTerminated()) {
						response.wait();
					}
				}
			}
			catch (InterruptedException e) {
				response.terminate();
				throw new InterruptedIOException(e.getMessage());
			}
		}
		else {
			assert !Thread.holdsLock(session);
			
			Queue<? extends Serializable> queue = session.getQueue();
			List<Serializable> messages = batchSize == 1 ? null : new ArrayList<Serializable>(batchSize);
			try {
				while (session.isValid() && !response.isTerminated()) {
					synchronized (session) {
						while (queue.isEmpty() && session.isValid() && !response.isTerminated()) {
							session.wait();
						}
					}
					
					synchronized (response) {
						if (session.isValid() && !response.isTerminated()) {
							Serializable message = queue.remove();
							if (batchSize == 1) {
								response.write(message, queue.isEmpty());
							}
							else {
								messages.add(message);
								for (int i = 0; i < batchSize - 1; i++) {
									message = queue.poll();
									if (message == null) {
										break;
									}
									messages.add(message);
								}
								response.write(messages, queue.isEmpty());
								messages.clear();
							}
						}
					}
				}
			}
			catch (InterruptedException e) {
				response.terminate();
				throw new InterruptedIOException(e.getMessage());
			}
			
			if (!session.isValid() && !response.isTerminated()) {
				response.terminate();
			}
		}
		return null;
	}
	
	@Override
	public void terminate(CometServletResponseImpl response, CometSessionImpl session, Object suspendInfo) {
		if (session == null) {
			synchronized (response) {
				response.notifyAll();
			}
		}
		else {
			synchronized (session) {
				session.notifyAll();
			}
		}
	}
	
	@Override
	public void invalidate(CometSessionImpl session) {
		synchronized (session) {
			session.notifyAll();
		}
	}
	
	@Override
	public void enqueued(CometSessionImpl session) {
		synchronized (session) {
			// BLAH don't want to wait for the session lock when enqueing :-(
			session.notifyAll();
		}
	}
	
	@Override
	public ScheduledFuture<?> scheduleHeartbeat(final CometServletResponseImpl response, CometSessionImpl session) {
		return executor.schedule(new Runnable() {
			@Override
			public void run() {
				response.tryHeartbeat();
			}
		}, response.getHeartbeat(), TimeUnit.MILLISECONDS);
	}
	
	@Override
	public ScheduledFuture<?> scheduleSessionKeepAlive(final CometServletResponseImpl response, final CometSessionImpl session) {
		long keepAliveTime = getKeepAliveTime(session);
		if (keepAliveTime <= 0) {
			response.tryTerminate();
			return null;
		}
		else {
			return executor.schedule(new Runnable() {
				@Override
				public void run() {
					response.scheduleSessionKeepAlive();
				}
			}, keepAliveTime, TimeUnit.MILLISECONDS);
		}
	}
	
	private long getKeepAliveTime(CometSessionImpl session) {
		HttpSession httpSession = session.getHttpSession();
		long lastAccessedTime = Math.max(session.getLastAccessedTime(), httpSession.getLastAccessedTime());
		return (httpSession.getMaxInactiveInterval() * 1000) - (System.currentTimeMillis() - lastAccessedTime) - SESSION_KEEP_ALIVE_BUFFER;
	}
}
