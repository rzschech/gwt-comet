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

import java.io.Serializable;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpSession;

import net.zschech.gwt.comet.server.CometSession;

public class CometSessionImpl implements CometSession {
	
	private final HttpSession httpSession;
	private final Queue<Serializable> queue;
	private final AsyncServlet async;
	private final AtomicBoolean valid;
	private final AtomicReference<CometServletResponseImpl> response;
	private volatile long lastAccessedTime;
	
	public CometSessionImpl(HttpSession httpSession, Queue<Serializable> queue, AsyncServlet async) {
		this.httpSession = httpSession;
		this.queue = queue;
		this.async = async;
		this.valid = new AtomicBoolean(true);
		this.response = new AtomicReference<CometServletResponseImpl>();
	}
	
	private void ensureValid() {
		if (!valid.get()) {
			throw new IllegalStateException("CometSession has been invalidated");
		}
	}
	
	@Override
	public HttpSession getHttpSession() {
		ensureValid();
		return httpSession;
	}
	
	@Override
	public void enqueue(Serializable message) {
		ensureValid();
		queue.add(message);
		async.enqueued(this);
	}
	
	@Override
	public void enqueued() {
		ensureValid();
		async.enqueued(this);
	}
	
	@Override
	public Queue<? extends Serializable> getQueue() {
		ensureValid();
		return queue;
	}
	
	@Override
	public void invalidate() {
		if (valid.compareAndSet(true, false)) {
			async.invalidate(this);
			try {
				httpSession.removeAttribute(HTTP_SESSION_KEY);
			}
			catch (IllegalStateException e) {
				// HttpSession already invalidated
			}
			
			CometServletResponseImpl prevResponse = response.getAndSet(null);
			if (prevResponse != null) {
				prevResponse.tryTerminate();
			}
		}
	}
	
	@Override
	public boolean isValid() {
		return valid.get();
	}
	
	CometServletResponseImpl setResponse(CometServletResponseImpl response) {
		return this.response.getAndSet(response);
	}
	
	boolean clearResponse(CometServletResponseImpl response) {
		return this.response.compareAndSet(response, null);
	}
	
	CometServletResponseImpl getResponse() {
		return response.get();
	}
	
	void setLastAccessedTime(long lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
	}
	
	long getLastAccessedTime() {
		return lastAccessedTime;
	}
}
