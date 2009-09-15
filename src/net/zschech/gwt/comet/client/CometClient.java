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
package net.zschech.gwt.comet.client;

import java.io.Serializable;
import java.util.List;

import net.zschech.gwt.comet.client.impl.CometTransport;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.user.client.Timer;

/**
 * This class is the Comet client. It will connect to the given url and notify the given {@link CometListener} of comet
 * events. To receive GWT serialized objects supply a {@link CometSerializer} method to parse the messages.
 * 
 * The sequence of events are as follows: The application calls {@link CometClient#start()}.
 * {@link CometListener#onConnected(int)} gets called when the connection is established.
 * {@link CometListener#onMessage(List)} gets called when messages are received from the server.
 * {@link CometListener#onDisconnected()} gets called when the connection is disconnected this includes connection
 * refreshes. {@link CometListener#onError(Throwable, boolean)} gets called if there is an error with the connection.
 * 
 * The Comet client will attempt to maintain to connection when disconnections occur until the application calls
 * {@link CometClient#stop()}.
 * 
 * The server sends heart beat messages to ensure the connection is maintained and that disconnections can be detected
 * in all cases.
 * 
 * @author Richard Zschech
 */
public class CometClient {
	
	private final String url;
	private final CometSerializer serializer;
	private final CometListener listener;
	private final CometTransport transport;
	
	private final Timer connectionTimer = createConnectionTimer();
	private final Timer reconnectionTimer = createReconnectionTimer();
	private final Timer heartbeatTimer = createHeartbeatTimer();
	
	private boolean running;
	
	private int connectionTimeout = 10000;
	private int reconnectionTimout = 1000;
	private int heartbeatTimeout;
	private double lastReceivedTime;
	
	public CometClient(String url, CometListener listener) {
		this(url, null, listener);
	}
	
	public CometClient(String url, CometSerializer serializer, CometListener listener) {
		this.url = url;
		this.serializer = serializer;
		this.listener = listener;
		
		transport = GWT.create(CometTransport.class);
		transport.initiate(this, new CometClientImplListener());
	}
	
	public String getUrl() {
		return url;
	}
	
	public CometSerializer getSerializer() {
		return serializer;
	}
	
	public CometListener getListener() {
		return listener;
	}
	
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	public int getConnectionTimeout() {
		return connectionTimeout;
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public void start() {
		if (!running) {
			running = true;
			doConnect();
		}
	}
	
	public void stop() {
		if (running) {
			running = false;
			doDisconnect();
		}
	}
	
	private void doConnect() {
		connectionTimer.schedule(connectionTimeout);
		transport.connect();
	}
	
	private void doDisconnect() {
		connectionTimer.cancel();
		reconnectionTimer.cancel();
		heartbeatTimer.cancel();
		transport.disconnect();
	}
	
	private Timer createConnectionTimer() {
		return new Timer() {
			@Override
			public void run() {
				doDisconnect();
				doOnError(new CometTimeoutException(url, connectionTimeout), false);
			}
		};
	}
	
	private Timer createHeartbeatTimer() {
		return new Timer() {
			@Override
			public void run() {
				double currentTimeMillis = Duration.currentTimeMillis();
				double difference = currentTimeMillis - lastReceivedTime;
				if (difference > heartbeatTimeout) {
					doDisconnect();
					doOnError(new CometException("Heartbeat failed"), false);
				}
				else {
					// we have received a message since the timer was schedule so reschedule it.
					schedule(heartbeatTimeout - (int) difference);
				}
			}
		};
	}
	
	private Timer createReconnectionTimer() {
		return new Timer() {
			@Override
			public void run() {
				if (running) {
					doConnect();
				}
			}
		};
	}
	
	private void doOnConnected(int heartbeat) {
		heartbeatTimeout = heartbeat + connectionTimeout;
		lastReceivedTime = Duration.currentTimeMillis();
		
		connectionTimer.cancel();
		reconnectionTimer.cancel();
		heartbeatTimer.cancel();
		heartbeatTimer.schedule(heartbeatTimeout);
		
		try {
			listener.onConnected(heartbeat);
		}
		catch (RuntimeException e) {
			uncaught(e);
		}
		catch (Error e) {
			uncaught(e);
		}
	}
	
	private void doOnDisconnected() {
		connectionTimer.cancel();
		reconnectionTimer.cancel();
		heartbeatTimer.cancel();
		try {
			listener.onDisconnected();
		}
		catch (RuntimeException e) {
			uncaught(e);
		}
		catch (Error e) {
			uncaught(e);
		}
		
		if (running) {
			doConnect();
		}
	}
	
	private void doOnHeartbeat() {
		lastReceivedTime = Duration.currentTimeMillis();
		try {
			listener.onHeartbeat();
		}
		catch (RuntimeException e) {
			uncaught(e);
		}
		catch (Error e) {
			uncaught(e);
		}
	}
	
	private void doOnError(Throwable exception, boolean connected) {
		if (!connected) {
			doDisconnect();
		}
		else {
			connectionTimer.cancel();
			reconnectionTimer.cancel();
			heartbeatTimer.cancel();
		}
		
		try {
			listener.onError(exception, connected);
		}
		catch (RuntimeException e) {
			uncaught(e);
		}
		catch (Error e) {
			uncaught(e);
		}
		
		if (!connected && running) {
			reconnectionTimer.schedule(reconnectionTimout);
		}
	}
	
	private void doOnMessage(List<? extends Serializable> messages) {
		lastReceivedTime = Duration.currentTimeMillis();
		try {
			listener.onMessage(messages);
		}
		catch (RuntimeException e) {
			uncaught(e);
		}
		catch (Error e) {
			uncaught(e);
		}
	}
	
	private void uncaught(RuntimeException e) {
		UncaughtExceptionHandler uncaughtExceptionHandler = GWT.getUncaughtExceptionHandler();
		if (uncaughtExceptionHandler != null) {
			uncaughtExceptionHandler.onUncaughtException(e);
		}
		else {
			throw e;
		}
	}
	
	private void uncaught(Error e) {
		UncaughtExceptionHandler uncaughtExceptionHandler = GWT.getUncaughtExceptionHandler();
		if (uncaughtExceptionHandler != null) {
			uncaughtExceptionHandler.onUncaughtException(e);
		}
		else {
			throw e;
		}
	}
	
	private class CometClientImplListener implements CometListener {
		
		@Override
		public void onConnected(int heartbeat) {
			doOnConnected(heartbeat);
		}
		
		@Override
		public void onDisconnected() {
			doOnDisconnected();
		}
		
		@Override
		public void onHeartbeat() {
			doOnHeartbeat();
		}
		
		@Override
		public void onError(Throwable exception, boolean connected) {
			doOnError(exception, connected);
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			doOnMessage(messages);
		}
	}

	public native static JsArrayString split(String string, String separator) /*-{
		return string.split(separator);
	}-*/;
	
	/*
	 * @Override public void start() { if (!closing) { Window.addWindowCloseListener(windowListener); super.start(); } }
	 * 
	 * @Override public void stop() { super.stop(); Window.removeWindowCloseListener(windowListener); }
	 * 
	 * private WindowCloseListener windowListener = new WindowCloseListener() {
	 * 
	 * @Override public void onWindowClosed() { closing = true; }
	 * 
	 * @Override public String onWindowClosing() { return null; } };
	 */
}
