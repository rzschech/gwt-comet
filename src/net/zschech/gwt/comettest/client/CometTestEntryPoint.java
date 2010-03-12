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
package net.zschech.gwt.comettest.client;

import java.io.Serializable;
import java.util.List;

import net.zschech.gwt.comet.client.CometClient;
import net.zschech.gwt.comet.client.CometListener;
import net.zschech.gwt.comet.client.CometSerializer;
import net.zschech.gwt.comet.client.SerialMode;
import net.zschech.gwt.comet.client.SerialTypes;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;

public class CometTestEntryPoint implements EntryPoint {
	
	private CometTestServiceAsync cometTestService;
	private CometTest cometTest;
	
	private HTML messages;
	private ScrollPanel scrollPanel;
	
	@Override
	public void onModuleLoad() {
		GWT.setUncaughtExceptionHandler(new GWT.UncaughtExceptionHandler() {
			@Override
			public void onUncaughtException(Throwable e) {
				output("uncaught " + string(e), "red");
				e.printStackTrace();
			}
		});
		
		cometTestService = GWT.create(CometTestService.class);
		
		messages = new HTML();
		scrollPanel = new ScrollPanel();
		scrollPanel.setHeight("250px");
		scrollPanel.add(messages);
		
		RootPanel.get().add(scrollPanel);

		CometTest[][] tests = new CometTest[][]{{
			new ConnectionTest(true),
			new ConnectionTest(false),
		}, {
			new ErrorTest(true),
			new ErrorTest(false),
		}, {
			new EscapeTest(true, null),
			new EscapeTest(true, SerialMode.RPC),
			new EscapeTest(true, SerialMode.DE_RPC),
		}, {
			new EscapeTest(false, null),
			new EscapeTest(false, SerialMode.RPC),
			new EscapeTest(false, SerialMode.DE_RPC),
		}, {
			new ThroughputTest(true, true, null),
			new ThroughputTest(true, true, SerialMode.RPC),
			new ThroughputTest(true, true, SerialMode.DE_RPC),
		}, {
			new ThroughputTest(true, false, null),
			new ThroughputTest(true, false, SerialMode.RPC),
			new ThroughputTest(true, false, SerialMode.DE_RPC),
		}, {
			new ThroughputTest(false, false, null),
			new ThroughputTest(false, false, SerialMode.RPC),
			new ThroughputTest(false, false, SerialMode.DE_RPC),
		}, {
			new LatencyTest(true, true, null),
			new LatencyTest(true, true, SerialMode.RPC),
			new LatencyTest(true, true, SerialMode.DE_RPC),
		}, {
			new LatencyTest(true, false, null),
			new LatencyTest(true, false, SerialMode.RPC),
			new LatencyTest(true, false, SerialMode.DE_RPC),
		}, {
			new LatencyTest(false, false, null),
			new LatencyTest(false, false, SerialMode.RPC),
			new LatencyTest(false, false, SerialMode.DE_RPC),
		}, {
			new OrderTest(true, true, null),
			new OrderTest(true, true, SerialMode.RPC),
			new OrderTest(true, true, SerialMode.DE_RPC),
		}, {
			new OrderTest(true, false, null),
			new OrderTest(true, false, SerialMode.RPC),
			new OrderTest(true, false, SerialMode.DE_RPC),
		}, {
			new OrderTest(false, false, null),
			new OrderTest(false, false, SerialMode.RPC),
			new OrderTest(false, false, SerialMode.DE_RPC),
		}};
		
		FlowPanel controls = new FlowPanel();
		controls.add(new Button("stop", new ClickHandler() {
			@Override
			public void onClick(ClickEvent arg0) {
				if (cometTest != null) {
					cometTest.stop();
					cometTest = null;
				}
			}
		}));
		controls.add(new Button("clear", new ClickHandler() {
			@Override
			public void onClick(ClickEvent arg0) {
				messages.setHTML("");
			}
		}));
		RootPanel.get().add(controls);
		
		for (CometTest[] typeTests : tests) {
			controls = new FlowPanel();
			for (CometTest t : typeTests) {
				final CometTest test = t;
				controls.add(new Button(test.name, new ClickHandler() {
					@Override
					public void onClick(ClickEvent arg0) {
						if (cometTest != null) {
							cometTest.stop();
						}
						cometTest = test;
						test.start();
					}
				}));
			}
			RootPanel.get().add(controls);
		}
	}
	
	public static final char ESCAPE_START = 32;
	public static final char ESCAPE_END = 127;
	public static final String ESCAPE;
	static {
		StringBuilder result = new StringBuilder();
		result.append(' '); // event source discards prefixed spaces
		for (char i = ESCAPE_START; i <= ESCAPE_END; i++) {
			result.append(i);
		}
		result.append("\n\r\r\n\\/\b\f\n\t");
		result.append("')</script>");
		result.append(' ');
		ESCAPE = result.toString();
	}
	
	@SerialTypes(mode = SerialMode.RPC, value = { TestData.class })
	public static abstract class RPCTestCometSerializer extends CometSerializer {
	}
	
	@SerialTypes(mode = SerialMode.DE_RPC, value = { TestData.class })
	public static abstract class DeRPCTestCometSerializer extends CometSerializer {
	}
	
	public static class TestData implements Serializable {
		private static final long serialVersionUID = 2554091659231006755L;
		public double d;
		public String s;
		
		public TestData() {
		}
		
		public TestData(double d, String s) {
			this.d = d;
			this.s = s;
		}
	}
	
	abstract class CometTest implements CometListener {
		
		final String name;
		final boolean session;
		
		CometClient cometClient;
		
		double startTime;
		double stopTime;
		double connectedTime;
		int connectedCount;
		double disconnectedTime;
		int disconnectedCount;
		int errorCount;
		int heartbeatCount;
		int refreshCount;
		int messageCount;
		int messagesCount;

		String failure;
		boolean pass;
		
		CometTest(String name, boolean session) {
			this.name = name + " session=" + session;
			this.session = session;
		}
		
		abstract void start();
		
		void start(String url) {
			start(url, (CometSerializer)null);
		}
		
		void start(String url, SerialMode mode) {
			final CometSerializer serializer;
			if (mode == null) {
				url = url + (url.contains("?") ? "&" : "?") + "mode=string";
				serializer = null;
			}
			else if (mode == SerialMode.RPC) {
				serializer = GWT.create(RPCTestCometSerializer.class);
			}
			else {
				serializer = GWT.create(DeRPCTestCometSerializer.class);
			}
			start(url + (url.contains("?") ? "&" : "?") + "session=" + session, serializer);
		}
		
		private void start(final String url, final CometSerializer serializer) {
			reset();
			cometTestService.invalidateSession(new AsyncCallback<Boolean>() {
				@Override
				public void onSuccess(Boolean result) {
					if (session) {
						cometTestService.createSession(new AsyncCallback<Boolean>() {
							@Override
							public void onSuccess(Boolean result) {
								doStart(url, serializer);
							}
							
							@Override
							public void onFailure(Throwable error) {
								output("create session failure " + string(error), "red");
							}
						});
					}
					else {
						doStart(url, serializer);
					}
				}
				
				@Override
				public void onFailure(Throwable error) {
					output("invalidate session failure " + string(error), "red");
				}
			});
		}
		
		void reset() {
			startTime = 0;
			stopTime = 0;
			connectedTime = 0;
			connectedCount = 0;
			disconnectedTime = 0;
			disconnectedCount = 0;
			errorCount = 0;
			heartbeatCount = 0;
			refreshCount = 0;
			messageCount = 0;
			messagesCount = 0;
			
			pass = false;
			failure = null;
		}

		private void doStart(String url, CometSerializer serializer) {
			cometClient = new CometClient(url, serializer, this);
			cometClient.start();
			
			startTime = Duration.currentTimeMillis();
			output("start " + name, "black");
		}
		
		void stop() {
			if (cometClient != null) {
				cometClient.stop();
				cometClient = null;
			}
			cometTest = null;
			stopTime = Duration.currentTimeMillis();
			output("stop " + name + " " + (stopTime - startTime) + "ms", "black");
			if (pass && failure == null) {
				output("pass!", "lime");
			}
			else {
				output("fail  :\n" + (failure == null ? "unknown" : failure), "red");
			}
		}
		
		void outputStats() {
			output("count     : " + messageCount, "black");
			output("rate      : " + messageCount / (disconnectedTime - connectedTime) * 1000 + "/s", "black");
			output("batch size: " + (double) messageCount / (double) messagesCount, "black");
		}
		
		@Override
		public void onConnected(int heartbeat) {
			connectedTime = Duration.currentTimeMillis();
			connectedCount++;
			output("connected " + connectedCount + " " + (connectedTime - startTime) + "ms heartbeat: " + heartbeat, "silver");
		}
		
		@Override
		public void onDisconnected() {
			disconnectedTime = Duration.currentTimeMillis();
			disconnectedCount++;
			output("disconnected " + disconnectedCount + " " + (disconnectedTime - connectedTime) + "ms", "silver");
			stop();
		}
		
		@Override
		public void onError(Throwable exception, boolean connected) {
			double errorTime = Duration.currentTimeMillis();
			errorCount++;
			output("error " + errorCount + " " + (errorTime - startTime) + "ms " + connected + " " + exception, "red");
			fail(exception.toString());
			stop();
		}
		
		@Override
		public void onHeartbeat() {
			double heartbeatTime = Duration.currentTimeMillis();
			heartbeatCount++;
			output("heartbeat " + heartbeatCount + " " + (heartbeatTime - connectedTime) + "ms", "silver");
		}
		
		@Override
		public void onRefresh() {
			double refreshTime = Duration.currentTimeMillis();
			refreshCount++;
			output("refresh " + refreshCount + " " + (refreshTime - connectedTime) + "ms", "silver");
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			messagesCount++;
			messageCount += messages.size();
		}
		
		void assertTrue(String message, boolean b) {
			if (!b) {
				fail(message);
			}
			else {
				pass = true;
			}
		}
		
		void pass() {
			pass = true;
		}
		
		void fail(String message) {
			if (failure == null) {
				failure = message;
			}
			else {
				failure += "\n" + message;
			}
		}
	}
	
	class ConnectionTest extends CometTest {
		
		private final int connectionTime = 120 * 1000;
		
		ConnectionTest(boolean session) {
			super("connection", session);
		}
		
		@Override
		void start() {
			String url = GWT.getModuleBaseURL() + "connection?delay=" + connectionTime;
			super.start(url);
		}
		
		@Override
		void stop() {
			assertTrue("connection time", disconnectedTime - connectedTime >= connectionTime - 100);
			super.stop();
			outputStats();
		}
	}
	
	class ErrorTest extends CometTest {
		
		ErrorTest(boolean session) {
			super("error", session);
		}
		
		@Override
		void start() {
			String url = GWT.getModuleBaseURL() + "error";
			super.start(url);
		}
		
		@Override
		public void onError(Throwable exception, boolean connected) {
			double errorTime = Duration.currentTimeMillis();
			errorCount++;
			output("error " + errorCount + " " + (errorTime - startTime) + "ms " + connected + " " + exception, "lime");
			pass();
			stop();
		}
	}
	
	class EscapeTest extends CometTest {
		
		private final SerialMode mode;
		
		EscapeTest(boolean session, SerialMode mode) {
			super("escape mode=" + mode, session);
			this.mode = mode;
		}
		
		@Override
		void start() {
			String url = GWT.getModuleBaseURL() + "escape";
			
			super.start(url, mode);
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			super.onMessage(messages);
			pass();
			for (Serializable m : messages) {
				String type;
				String message;
				if (m instanceof TestData) {
					type = "gwt serialized object";
					message = ((TestData) m).s;
				}
				else if (m instanceof String) {
					type = "string";
					message = (String) m;
				}
				else if (m == null) {
					continue;
				}
				else {
					fail("unexpected object " + m.getClass() + " " + m);
					continue;
				}
				
				if (ESCAPE.length() != message.length()) {
					fail(type + " expected message length " + ESCAPE.length() + " acutal " + message.length());
				}
				else {
					for (int i = 0; i < ESCAPE.length(); i++) {
						char expected = ESCAPE.charAt(i);
						char actual = message.charAt(i);
						if (expected != actual) {
							fail(type + " expected character " + expected + " 0x" + Integer.toHexString(expected) + " actual " + actual + " 0x" + Integer.toHexString(actual));
						}
					}
				}
			}
		}
	}
	
	abstract class MessagingTest extends CometTest {
		
		private final String url;
		private final boolean refresh;
		private final SerialMode mode;
		
		private final int count;
		private final int batch;
		private final int delay;
		
		MessagingTest(String name, boolean session, boolean refresh, SerialMode mode, int count, int batch, int delay) {
			super(name + " refresh=" + refresh + " mode=" + mode, session);
			this.url = name;
			this.refresh = refresh;
			this.mode = mode;
			this.count = count;
			this.batch = batch;
			this.delay = delay;
		}
		
		@Override
		void start() {
			String url = GWT.getModuleBaseURL() + this.url + "?count=" + count + "&batch=" + batch;
			if (mode == null) {
				url += "&mode=string";
			}
			if (!refresh) {
				url += "&length=" + (count * batch * 1000);
			}
			
			url += "&delay=" + delay;
			
			super.start(url, mode);
		}
		
		@Override
		void stop() {
			assertTrue("count", count * batch == messageCount);
			super.stop();
			outputStats();
		}
	}
	
	class ThroughputTest extends MessagingTest {
		
		ThroughputTest(boolean session, boolean refresh, SerialMode mode) {
			super("throughput", session, refresh, mode, 10, 10, 0);
		}
		
		@Override
		void stop() {
			super.stop();
		}
	}
	
	class LatencyTest extends MessagingTest {
		
		private double latency;
		
		LatencyTest(boolean session, boolean refresh, SerialMode mode) {
			super("latency", session, refresh, mode, 100, 1, 10);
		}
		
		void reset() {
			super.reset();
			latency = 0;
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			super.onMessage(messages);
			double now = Duration.currentTimeMillis();
			for (Serializable m : messages) {
				double message;
				if (m instanceof TestData) {
					message = ((TestData) m).d;
				}
				else if (m instanceof String) {
					message = Double.parseDouble((String) m);
				}
				else {
					continue;
				}
				latency += now - message;
			}
		}
		
		@Override
		void outputStats() {
			super.outputStats();
			output("latency   : " + latency / messageCount + "ms", "black");
		}
	}
	
	class OrderTest extends MessagingTest {
		
		OrderTest(boolean session, boolean refresh, SerialMode mode) {
			super("order", session, refresh, mode, 100, 1, 0);
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			int count = messageCount;
			super.onMessage(messages);
			for (Serializable m : messages) {
				double message;
				if (m instanceof TestData) {
					message = ((TestData) m).d;
				}
				else if (m instanceof String) {
					message = Double.parseDouble((String) m);
				}
				else {
					continue;
				}
				
				assertTrue("expected count " + count + " actual " + message, count == message);
				count++;
			}
		}
	}
	
	// public void paddingTest() {
	// paddingTest(0, 8 * 1024);
	// }
	
	// public void paddingTest(final int min, final int max) {
	// final int p = (min + max) / 2;
	// output("Padding test: " + min + " " + max + " " + p);
	// if (min == max) {
	// return;
	// }
	// start(GWT.getModuleBaseURL() + "connection?delay=2000&padding=" + p, new CometListener() {
	// @Override
	// public void onMessage(List<? extends Serializable> messages) {
	// }
	//			
	// @Override
	// public void onConnected(int hearbeat) {
	// stop();
	// paddingTest(min, p - 1);
	// }
	//			
	// @Override
	// public void onDisconnected() {
	// stop();
	// }
	//			
	// @Override
	// public void onHeartbeat() {
	// }
	//			
	// @Override
	// public void onRefresh() {
	// }
	//			
	// @Override
	// public void onError(Throwable exception, boolean connected) {
	// stop();
	// paddingTest(p + 1, max);
	// }
	// });
	// }
	//	
	//	
	private static String string(Throwable exception) {
		String result = exception.toString();
		exception = exception.getCause();
		while (exception != null) {
			result += "\n" + exception.toString();
			exception = exception.getCause();
		}
		return result;
	}
	
	public void output(String text, String color) {
		DivElement div = Document.get().createDivElement();
		div.setInnerText(text);
		div.setAttribute("style", "font-family:monospace;white-space:pre;color:" + color);
		messages.getElement().appendChild(div);
		scrollPanel.scrollToBottom();
	}
}
