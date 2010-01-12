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

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.StatusCodeException;

/**
 * This class uses IE's ActiveX "htmlfile" with an embedded iframe to stream events.
 * http://cometdaily.com/2007/11/18/ie-activexhtmlfile-transport-part-ii/
 * 
 * The main issue with this implementation is that we can't detect initial connection errors. A connection timer is
 * setup to detect this.
 * 
 * Another issues is that the memory required for the iframe constantly grows so the server occasionally disconnects the
 * client who then reestablishes the connection with an empty iframe. To alleviate the issue the client removes script
 * tags as the messages in them have been processed.
 * 
 * The protocol for this transport is a stream of <script> tags with function calls to this transports callbacks as
 * follows:
 * 
 * c(heartbeat) A connection message with the heartbeat duration as an integer
 * 
 * e(error) An error message with the error code
 * 
 * h() A heartbeat message
 * 
 * r() A refresh message
 * 
 * s(string) A string message
 * 
 * o(string) A gwt serialized object
 * 
 * string and gwt serialized object messages Java Script escaped
 * 
 * @author Richard Zschech
 */
public class IEHTMLFileCometTransport extends CometTransport {
	
	private IFrameElement iframe;
	private BodyElement body;
	private boolean expectingDisconnection;
	
	@Override
	public void initiate(CometClient client, CometListener listener) {
		super.initiate(client, listener);
		StringBuilder html = new StringBuilder("<html>");
		String overriddenDomain = CometClient.getOverriddenDomain();
		if (overriddenDomain != null) {
			html.append("<script>document.domain='").append(overriddenDomain).append("'</script>");
		}
		html.append("<iframe src=''></iframe></html>");
		
		iframe = createIFrame(this, html.toString());
	}
	
	@Override
	public void connect() {
		expectingDisconnection = false;
		String url = getUrl();
		String overriddenDomain = CometClient.getOverriddenDomain();
		if (overriddenDomain != null) {
			url += "&domain=" + overriddenDomain;
		}
		iframe.setSrc(url);
	}
	
	@Override
	public void disconnect() {
		// TODO this does not seem to close the connection immediately.
		iframe.setSrc("");
		body = null;
	}
	
	private static native IFrameElement createIFrame(IEHTMLFileCometTransport client, String html) /*-{
		var htmlfile = new ActiveXObject("htmlfile");
		htmlfile.open();
		htmlfile.write(html);
		htmlfile.close();
		
		htmlfile.parentWindow.s = $entry(function(message) {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onString(Ljava/lang/String;)(message);
		});
		htmlfile.parentWindow.o = $entry(function(message) {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onObject(Ljava/lang/String;)(message);
		});
		htmlfile.parentWindow.c = $entry(function(heartbeat) {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onConnected(I)(heartbeat);
		});
		htmlfile.parentWindow.d = $entry(function() {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onDisconnected()();
		});
		htmlfile.parentWindow.e = $entry(function(statusCode, message) {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onError(ILjava/lang/String;)(statusCode, message);
		});
		htmlfile.parentWindow.h = $entry(function() {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onHeartbeat()();
		});
		htmlfile.parentWindow.r = $entry(function() {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onRefresh()();
		});
		// no $entry() because no user code is reachable
		htmlfile.parentWindow.t = function() {
			client.@net.zschech.gwt.comet.client.impl.IEHTMLFileCometTransport::onTerminate()();
		};
		
		return htmlfile.documentElement.getElementsByTagName("iframe").item(0);
	}-*/;
	
	private void collect() {
		NodeList<Node> childNodes = body.getChildNodes();
		if (childNodes.getLength() > 1) {
			body.removeChild(childNodes.getItem(0));
		}
	}
	
	@SuppressWarnings("unused")
	private void onString(String message) {
		collect();
		listener.onMessage(Collections.singletonList(message));
	}
	
	@SuppressWarnings("unused")
	private void onObject(String message) {
		collect();
		CometSerializer serializer = client.getSerializer();
		if (serializer == null) {
			listener.onError(new SerializationException("Can not deserialize message with no serializer: " + message), true);
		}
		else {
			try {
				listener.onMessage(Collections.singletonList(serializer.parse(message)));
			}
			catch (SerializationException e) {
				listener.onError(e, true);
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void onConnected(int heartbeat) {
		body = iframe.getContentDocument().getBody();
		collect();
		listener.onConnected(heartbeat);
	}
	
	@SuppressWarnings("unused")
	private void onDisconnected() {
		body = null;
		if (expectingDisconnection) {
			listener.onDisconnected();
		}
		else {
			listener.onError(new CometException("Unexpected disconnection"), false);
		}
	}
	
	@SuppressWarnings("unused")
	private void onError(int statusCode, String message) {
		listener.onError(new StatusCodeException(statusCode, message), false);
	}
	
	@SuppressWarnings("unused")
	private void onHeartbeat() {
		listener.onHeartbeat();
	}
	
	@SuppressWarnings("unused")
	private void onRefresh() {
		listener.onRefresh();
	}
	
	@SuppressWarnings("unused")
	private void onTerminate() {
		expectingDisconnection = true;
	}
}
