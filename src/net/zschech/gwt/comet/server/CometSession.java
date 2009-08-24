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

import java.io.Serializable;
import java.util.Queue;

import javax.servlet.http.HttpSession;

/**
 * A Comet session encapsulates a queue of messages to be delivered to a comet client. The Comet session is attached to
 * the HTTP session as an attribute with the {@link #HTTP_SESSION_KEY}.
 * 
 * @author Richard Zschech
 */
public interface CometSession {
	
	public static final String HTTP_SESSION_KEY = "net.zschech.gwt.comet.server.CometSession";
	
	/**
	 * Enqueues a message. This is equivalent to:
	 * 
	 * <code>
	 *  session.getQueue().add(message);
	 *  session.enqueued();
	 * </code>
	 * 
	 * @param message
	 */
	public void enqueue(Serializable message);
	
	/**
	 * Call to notify the comet session that a message has been enqueued by other means than
	 * {@link CometSession#enqueue(Serializable)}.
	 */
	public void enqueued();
	
	/**
	 * @return the message queue
	 */
	public Queue<? extends Serializable> getQueue();
	
	public void invalidate();
	
	public boolean isValid();
	
	/**
	 * @return the associated HTTP session
	 */
	public HttpSession getHttpSession();
}
