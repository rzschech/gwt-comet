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

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * This AsyncServlet implementation blocks the HTTP request processing thread.
 * 
 * It does not generate notifications for client disconnection and therefore must wait for a heartbeat send attempt to
 * detect client disconnection :-(
 * 
 * @author Richard Zschech
 */
public class BlockingAsyncServlet extends AsyncServlet {
	
	@Override
	public void init(ServletContext context) throws ServletException {
		super.init(context);
	}
	
	@Override
	public Object suspend(CometServletResponseImpl response, CometSessionImpl session) throws IOException {
		assert !Thread.holdsLock(response);
		
		if (session == null) {
			try {
				synchronized (response) {
					while (!response.isTerminated()) {
						long heartBeatTime = response.getHeartbeatScheduleTime();
						if (heartBeatTime <= 0) {
							response.heartbeat();
							heartBeatTime = response.getHeartbeat();
						}
						response.wait(heartBeatTime);
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
			
			try {
				try {
					synchronized (response) {
						while (session.isValid() && !response.isTerminated()) {
							while (response.checkSessionQueue(true)) {
								long sessionKeepAliveTime = response.getSessionKeepAliveScheduleTime();
								if (sessionKeepAliveTime <= 0) {
									if (access(session.getHttpSession())) {
										session.setLastAccessedTime(System.currentTimeMillis());
										sessionKeepAliveTime = response.getSessionKeepAliveScheduleTime();
									}
									else {
										response.terminate();
										break;
									}
								}
								
								long heartBeatTime = response.getHeartbeatScheduleTime();
								if (heartBeatTime <= 0) {
									response.heartbeat();
									heartBeatTime = response.getHeartbeat();
								}
								response.wait(Math.min(sessionKeepAliveTime, heartBeatTime));
							}
							response.writeSessionQueue(true);
						}
					}
				}
				catch (InterruptedException e) {
					response.terminate();
					throw new InterruptedIOException(e.getMessage());
				}
			}
			catch (IOException e) {
				log("Error writing messages", e);
			}
			
			if (!session.isValid() && !response.isTerminated()) {
				response.terminate();
			}
		}
		return null;
	}
	
	@Override
	public void terminate(CometServletResponseImpl response, final CometSessionImpl session, boolean serverInitiated, Object suspendInfo) {
		assert Thread.holdsLock(response);
		response.notifyAll();
	}
	
	@Override
	public void invalidate(CometSessionImpl session) {
		CometServletResponseImpl response = session.getResponse();
		if (response != null) {
			synchronized (response) {
				response.notifyAll();
			}
		}
	}
	
	@Override
	public void enqueued(CometSessionImpl session) {
		CometServletResponseImpl response = session.getResponse();
		if (response != null) {
			synchronized (response) {
				response.notifyAll();
			}
		}
	}
}
