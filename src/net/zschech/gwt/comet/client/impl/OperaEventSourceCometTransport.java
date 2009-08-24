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
package net.zschech.gwt.comet.client.impl;

import java.util.Collections;

import net.zschech.gwt.comet.client.CometClient;
import net.zschech.gwt.comet.client.CometException;
import net.zschech.gwt.comet.client.CometListener;
import net.zschech.gwt.comet.client.CometSerializer;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.StatusCodeException;

/**
 * This class uses Opera's event-source element to stream events.<br/>
 * http://my.opera.com/WebApplications/blog/show.dml/438711
 * 
 * The main issue with Opera's implementation is that we can't detect connection events. To support this three event
 * listeners are setup: one "s" for string messages, one "o" for the GWT serialized object messages, and the other "c"
 * for connection events. The server sends the event "c" as soon as the connection is established and "d"
 * when the connection is terminated. A connection timer is setup to detect initial connection errors. To detect
 * subsequent connection failure it also sends a heart beat events "h" when no messages have been sent for a specified
 * heart beat interval.
 * 
 * @author Richard Zschech
 */
public class OperaEventSourceCometTransport extends CometTransport {
	
	private Element eventsource;
	
	@Override
	public void initiate(CometClient client, CometListener listener) {
		super.initiate(client, listener);
		eventsource = createEventSource(this);
	}
	
	@Override
	public void connect() {
		DOM.setElementAttribute(eventsource, "src", client.getUrl());
	}
	
	@Override
	public void disconnect() {
		DOM.setElementAttribute(eventsource, "src", "");
	}
	
	private static native Element createEventSource(OperaEventSourceCometTransport client) /*-{
		var eventsource = document.createElement("event-source");

		var messagehandler = function(event) {
			client.@net.zschech.gwt.comet.client.impl.OperaEventSourceCometTransport::onString(Ljava/lang/String;)(event.data);
		};

		eventsource.addEventListener("s", messagehandler, false);

		var messagehandler = function(event) {
			client.@net.zschech.gwt.comet.client.impl.OperaEventSourceCometTransport::onObject(Ljava/lang/String;)(event.data);
		};

		eventsource.addEventListener("o", messagehandler, false);

		var connectionhandler = function(event) {
			client.@net.zschech.gwt.comet.client.impl.OperaEventSourceCometTransport::onConnection(Ljava/lang/String;)(event.data);
		};

		eventsource.addEventListener("c", connectionhandler, false);

		return eventsource;
	}-*/;
	
	@SuppressWarnings("unused")
	private void onString(String message) {
		try {
			listener.onMessage(Collections.singletonList(HTTPRequestCometTransport.unescape(message)));
		}
		catch (SerializationException e) {
			listener.onError(e, true);
		}
	}
	
	@SuppressWarnings("unused")
	private void onObject(String message) {
		CometSerializer serializer = client.getSerializer();
		if (serializer == null) {
			listener.onError(new SerializationException("Can not deserialize message with no serializer: " + message), true);
		}
		else {
			try {
				listener.onMessage(Collections.singletonList(serializer.parse(HTTPRequestCometTransport.unescape(message))));
			}
			catch (SerializationException e) {
				listener.onError(e, true);
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void onConnection(String message) {
		if (message.startsWith("c")) {
			String hertbeatParameter = message.substring(1);
			try {
				listener.onConnected(Integer.parseInt(hertbeatParameter));
			}
			catch (NumberFormatException e) {
				listener.onError(new CometException("Unexpected heartbeat parameter: " + hertbeatParameter), true);
			}
		}
		else if (message.startsWith("e")) {
			disconnect();
			String status = message.substring(1);
			try {
				int statusCode;
				String statusMessage;
				int index = status.indexOf(' ');
				if (index == -1) {
					statusCode = Integer.parseInt(status);
					statusMessage = null;
				}
				else {
					statusCode = Integer.parseInt(status.substring(0, index - 1));
					statusMessage = HTTPRequestCometTransport.unescape(status.substring(index + 1));
				}
				listener.onError(new StatusCodeException(statusCode, statusMessage), false);
			}
			catch (NumberFormatException e) {
				listener.onError(new CometException("Unexpected status code: " + status), false);
			}
			catch (SerializationException e) {
				listener.onError(e, false);
			}
		}
		else if (message.equals("d")) {
			disconnect();
			listener.onDisconnected();
		}
		else if (message.equals("h")) {
			listener.onHeartbeat();
		}
		else {
			listener.onError(new CometException("Unexpected connection status: " + message), true);
		}
	}
}
