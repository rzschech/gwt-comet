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
import net.zschech.gwt.comet.client.CometSerializer;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.http.client.RequestException;
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
 * The protocol for this transport is a '\n' separated transport messages. The different types of transport message are
 * identified by the first character in the line as follows:
 * 
 * ! A connection message followed by the heartbeat duration as an integer
 * 
 * ? A clean server disconnection message
 * 
 * # A heartbeat message
 * 
 * * A padding message to cause the browser to start processing the stream
 * 
 * ] A string message that needs unescaping
 * 
 * | A string message that does not need unescaping
 * 
 * [ A GWT serialized object
 * 
 * string messages are escaped for '\\' and '\n' characters as '\n' is the message separator.
 * 
 * GWT serialized object messages are escaped by GWT so do not need to be escaped by the transport
 * 
 * @author Richard Zschech
 */
public class HTTPRequestCometTransport extends CometTransport {
	
	private static final String SEPARATOR = "\n";
	private JavaScriptObject xmlHttpRequest;
	private boolean expectingDisconnection;
	private int read;
	
	@Override
	public void connect() {
		expectingDisconnection = false;
		read = 0;
		
		xmlHttpRequest = createXMLHttpRequest();
		String sendError = connect(xmlHttpRequest, getUrl(), this);
		if (sendError != null) {
			xmlHttpRequest = null;
			listener.onError(new RequestException(sendError), false);
		}
	}
	
	private native JavaScriptObject createXMLHttpRequest() /*-{
		return new XMLHttpRequest();
	}-*/;
	
	private native String connect(JavaScriptObject xmlHttpRequest, String url, HTTPRequestCometTransport transport) /*-{
		try {
			xmlHttpRequest.open("GET", url, true);
			xmlHttpRequest.setRequestHeader("Accept", "text/plain");
			xmlHttpRequest.setRequestHeader("Cache-Control", "no-cache");
			xmlHttpRequest.onreadystatechange = function() {
				var readyState = xmlHttpRequest.readyState;
				if (readyState == @com.google.gwt.http.client.XMLHTTPRequest::LOADED) {
					xmlHttpRequest.onreadystatechange = null;
					transport.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::onLoaded(ILjava/lang/String;)(xmlHttpRequest.status, xmlHttpRequest.responseText);
				}
				else if (readyState == @com.google.gwt.http.client.XMLHTTPRequest::RECEIVING) {
					// IE does not support getting the response text so we check to make hosted mode debugging easier
					if (typeof xmlHttpRequest.responseText != 'unknown') {
						transport.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::onReceiving(ILjava/lang/String;)(xmlHttpRequest.status, xmlHttpRequest.responseText);
					}
				}
			};
			xmlHttpRequest.send(null);
			return null;
		}
		catch (e) {
			return e.message || e.toString();
		}
	}-*/;
	
	@Override
	public native void disconnect() /*-{
		if (this.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::xmlHttpRequest != null) {
			this.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::xmlHttpRequest.onreadystatechange = null;
			this.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::xmlHttpRequest.abort();
			this.@net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport::xmlHttpRequest = null;
		}
	}-*/;
	
	@SuppressWarnings("unused")
	private void onLoaded(int statusCode, String responseText) {
		xmlHttpRequest = null;
		onReceiving(statusCode, responseText, false);
	}
	
	@SuppressWarnings("unused")
	private void onReceiving(int statusCode, String responseText) {
		onReceiving(statusCode, responseText, true);
	}
	
	private void onReceiving(int statusCode, String responseText, boolean connected) {
		if (statusCode != Response.SC_OK) {
			if (!connected) {
				expectingDisconnection = true;
				listener.onError(new StatusCodeException(statusCode, responseText), connected);
			}
		}
		else {
			int index = responseText.lastIndexOf(SEPARATOR);
			if (index > read) {
				List<Serializable> messages = new ArrayList<Serializable>();
				JsArrayString data = CometClient.split(responseText.substring(read, index), SEPARATOR);
				int length = data.length();
				for (int i = 0; i < length; i++) {
					parse(data.get(i), messages);
				}
				read = index + 1;
				if (!messages.isEmpty()) {
					listener.onMessage(messages);
				}
			}
			
			if (!connected) {
				if (expectingDisconnection) {
					listener.onDisconnected();
				}
				else {
					listener.onError(new CometException("Unexpected disconnection"), false);
				}
			}
		}
	}
	
	private void parse(String message, List<Serializable> messages) {
		if (expectingDisconnection) {
			listener.onError(new CometException("Expecting disconnection but received message: " + message), true);
		}
		else if (message.isEmpty()) {
			listener.onError(new CometException("Invalid empty message received"), true);
		}
		else {
			char c = message.charAt(0);
			switch (c) {
			case '!':
				String hertbeatParameter = message.substring(1);
				try {
					listener.onConnected(Integer.parseInt(hertbeatParameter));
				}
				catch (NumberFormatException e) {
					listener.onError(new CometException("Unexpected heartbeat parameter: " + hertbeatParameter), true);
				}
				break;
			case '?':
				// clean disconnection
				expectingDisconnection = true;
				break;
			case '#':
				listener.onHeartbeat();
				break;
			case '*':
				// ignore padding
				break;
			case '|':
				messages.add(message.substring(1));
				break;
			case ']':
				messages.add(unescape(message.substring(1)));
				break;
			case '[':
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
				break;
			default:
				listener.onError(new CometException("Invalid message received: " + message), true);
			}
		}
	}
	
	static String unescape(String string) {
		return string.replace("\\n", "\n").replace("\\\\", "\\");
	}
}
