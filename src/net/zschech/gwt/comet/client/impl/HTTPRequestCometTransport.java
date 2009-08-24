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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.zschech.gwt.comet.client.CometClient;
import net.zschech.gwt.comet.client.CometException;
import net.zschech.gwt.comet.client.CometListener;
import net.zschech.gwt.comet.client.CometSerializer;
import net.zschech.gwt.comet.client.CometTimeoutException;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallbackEx;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.RequestTimeoutException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.StatusCodeException;

/**
 * This class uses a XmlHttpRequest and onreadystatechange events to process stream events.<br/>
 * 
 * The main issue with this implementation is that GWT does not generate RECEIVING events from its XMLHTTPRequest. The
 * implementation of XMLHTTPRequest included in this package overrides that behaviour.
 * 
 * Another issues is that the memory required for the XMLHTTPRequest's responseText constantly grows so the server
 * occasionally disconnects the client who then reestablishes the connection.
 * 
 * The protocol for this transport is a "\r\n" separated transport messages. The different types of transport message
 * are identified by the first character in the line as follows:
 * 
 * ! A connection message followed by the heartbeat duration as an integer
 * 
 * ? A clean server disconnection message
 * 
 * # A heartbeat message
 * 
 * * A padding message to cause the browser to start processing the stream
 * 
 * ] A string message
 * 
 * [ A GWT serialized object
 * 
 * string messages are escaped for '\\' '\r' and '\n' characters as "\r\n" is the message separator.
 * 
 * GWT serialized object messages are escaped by GWT so do not need to be escaped by the transport
 * 
 * @author Richard Zschech
 */
public class HTTPRequestCometTransport extends CometTransport {
	
	private static final String SEPARATOR = "\r\n";
	private RequestBuilder requestBuilder;
	private Request request;
	private boolean expectingDisconnection;
	
	@Override
	public void initiate(CometClient client, CometListener listener) {
		super.initiate(client, listener);
		requestBuilder = new RequestBuilder(RequestBuilder.GET, client.getUrl());
		requestBuilder.setHeader("Accept", "text/plain");
		requestBuilder.setHeader("Cache-Control", "no-cache");
	}
	
	@Override
	public void connect() {
		expectingDisconnection = false;
		try {
			request = requestBuilder.sendRequest("", new RequestCallbackEx() {
				private int read = 0;
				
				@Override
				public void onResponseReceived(Request request, Response response) {
					onResponseReceiving(request, response);
					
					request = null;
					if (expectingDisconnection) {
						listener.onDisconnected();
					}
					else {
						listener.onError(new CometException("Unexpected disconnection"), false);
					}
				}
				
				@Override
				public void onResponseReceiving(Request request, Response response) {
					
					int statusCode = response.getStatusCode();
					String text = response.getText();
					if (statusCode != Response.SC_OK) {
						request.cancel();
						expectingDisconnection = true;
						listener.onError(new StatusCodeException(statusCode, text), false);
					}
					else {
						List<Serializable> messages = new ArrayList<Serializable>();
						int index = text.indexOf(SEPARATOR, read);
						while (index > 0) {
							parse(text.substring(read, index), messages);
							read = index + SEPARATOR.length();
							index = text.indexOf(SEPARATOR, read);
						}
						
						if (!request.isPending()) {
							if (read < text.length() - SEPARATOR.length()) {
								parse(text.substring(read), messages);
							}
						}
						
						if (!messages.isEmpty()) {
							listener.onMessage(messages);
						}
					}
				}
				
				@Override
				public void onError(Request request, Throwable exception) {
					if (exception instanceof RequestTimeoutException) {
						RequestTimeoutException rte = (RequestTimeoutException) exception;
						exception = new CometTimeoutException(client.getUrl(), rte.getTimeoutMillis());
					}
					expectingDisconnection = request.isPending();
					listener.onError(exception, request.isPending());
				}
			});
		}
		catch (RequestException e) {
			listener.onError(e, false);
		}
	}
	
	@Override
	public void disconnect() {
		if (request != null) {
			request.cancel();
			request = null;
		}
	}
	
	private void parse(String message, List<Serializable> messages) {
		if (expectingDisconnection) {
			listener.onError(new CometException("Expecting disconnection but received message: " + message), true);
		}
		else if (message.startsWith("!")) {
			String hertbeatParameter = message.substring(1);
			try {
				listener.onConnected(Integer.parseInt(hertbeatParameter));
			}
			catch (NumberFormatException e) {
				listener.onError(new CometException("Unexpected heartbeat parameter: " + hertbeatParameter), true);
			}
		}
		else if (message.startsWith("?")) {
			// clean disconnection
			expectingDisconnection = true;
		}
		else if (message.startsWith("#")) {
			listener.onHeartbeat();
		}
		else if (message.startsWith("*")) {
			// ignore padding
		}
		else if (message.startsWith("]")) {
			try {
				messages.add(unescape(message).substring(1));
			}
			catch (SerializationException e) {
				listener.onError(e, true);
			}
		}
		else {
			CometSerializer serializer = client.getSerializer();
			if (serializer == null) {
				listener.onError(new SerializationException("Can not deserialize message with no serializer: " + message), true);
			}
			else {
				try {
					messages.add(serializer.parse(message));
				}
				catch (SerializationException e) {
					listener.onError(e, true);
				}
			}
		}
	}
	
	static String unescape(String string) throws SerializationException {
		int index = string.indexOf('\\');
		if (index == -1) {
			return string;
		}
		
		// TODO this unescaping code is probably not that efficient when converted to
		// java script
		StringBuilder str = new StringBuilder(string.length());
		str.append(string.substring(0, index));
		
		int length = string.length();
		char c;
		for (int i = index; i < length; i++) {
			c = string.charAt(i);
			switch (c) {
			case '\\':
				i++;
				if (i >= length) {
					throw new SerializationException("Invalid escape sequence at: " + i + " in:" + string);
				}
				c = string.charAt(i);
				switch (c) {
				case '\\':
					str.append(c);
					break;
				case 'r':
					str.append('\r');
					break;
				case 'n':
					str.append('\n');
					break;
				default:
					throw new SerializationException("Invalid escape sequence: " + c + " at: " + i + " in:" + string);
				}
				break;
			default:
				str.append(c);
			}
		}
		return str.toString();
	}
}
