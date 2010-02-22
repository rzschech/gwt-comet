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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

public class CometTestEntryPoint implements EntryPoint {
	
	private CometClient cometClient;
	private HTML log;
	
	@Override
	public void onModuleLoad() {
		
		GWT.setUncaughtExceptionHandler(new GWT.UncaughtExceptionHandler() {
			@Override
			public void onUncaughtException(Throwable e) {
				log(string(e));
				e.printStackTrace();
			}
		});
		
		RootPanel.get().add(new Button("Clear", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				log.setHTML("");
			}
		}));
		
		RootPanel.get().add(new Button("Stop", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				stop();
			}
		}));
		
		RootPanel.get().add(new Button("Throughput", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				throughputTest();
			}
		}));

		RootPanel.get().add(new Button("RPC Serialize", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				rpcSerializeTest();
			}
		}));
		
		RootPanel.get().add(new Button("DE_RPC Serialize", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				derpcSerializeTest();
			}
		}));
		
		RootPanel.get().add(new Button("Latency", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				latencyTest();
			}
		}));
		
		RootPanel.get().add(new Button("Connection", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				connectionTest();
			}
		}));
		
		RootPanel.get().add(new Button("Padding", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				paddingTest();
			}
		}));
		
		RootPanel.get().add(new Button("RPC Escape", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				rpcEscapeTest();
			}
		}));
		
		RootPanel.get().add(new Button("DE_RPC Escape", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				derpcEscapeTest();
			}
		}));
		
		RootPanel.get().add(new Button("Error", new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				errorTest();
			}
		}));
		
		log = new HTML();
		RootPanel.get().add(log);
	}
	
	public void log(String text) {
		log.setHTML(log.getHTML() + "<br/>" + text.replace("\n", "<br/>"));
	}
	
	private void stop() {
		if (cometClient != null) {
			cometClient.stop();
			cometClient = null;
		}
	}
	//ch 20.8 11.34
	private void start(String url, CometListener listener) {
		start(url, null, listener);
	}
	
	private void start(String url, CometSerializer serializer, CometListener listener) {
		stop();
//		url = "http://test.com:8888" + url.substring(url.lastIndexOf('8')+1);
		cometClient = new CometClient(url, serializer, listener);
		cometClient.start();
	}
	
	public void throughputTest() {
		final int c = 1000;
		final int b = 10;
		
		start(GWT.getModuleBaseURL() + "throughput?count=" + c + "&batch=" + b + "&length=" + (c * b * 100), new CometListener() {
			double start = Duration.currentTimeMillis();
			double connected;
			double disconnected;
			int count;
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
				count += messages.size();
			}
			
			@Override
			public void onConnected(int hearbeat) {
				connected = Duration.currentTimeMillis();
				log("Connected " + (connected - start) + "ms heartbeat: " + hearbeat);
			}
			
			@Override
			public void onDisconnected() {
				disconnected = Duration.currentTimeMillis();
				log("Disconnected " + (disconnected - connected) + "ms");
				log("Count " + count);
				log("Rate " + count / (disconnected - connected) * 1000 + "/s");
				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}

			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
				stop();
			}
		});
	}
	
	public void latencyTest() {
		final int c = 10000;
		final int d = 10;
		start(GWT.getModuleBaseURL() + "latency?count=" + c + "&delay=" + d, new CometListener() {
			double start = Duration.currentTimeMillis();
			double connected;
			double disconnected;
			double latency;
			int count;
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
				double now = Duration.currentTimeMillis();
				for (Serializable message : messages) {
//					latency += now - Double.parseDouble(message.string());
					
					if (count != Integer.parseInt(message.toString())) {
						log("expected count " + count + " actual " + message);
					}
					count++;
				}
				
				
//				count += messages.size();
				if (count % 1000 == 0) {
					log(String.valueOf(count));
				}
			}
			
			@Override
			public void onConnected(int hearbeat) {
				connected = Duration.currentTimeMillis();
				log("Connected " + (connected - start) + "ms heartbeat: " + hearbeat);
			}
			
			@Override
			public void onDisconnected() {
				disconnected = Duration.currentTimeMillis();
				log("Disconnected " + (disconnected - connected) + "ms");
				log("Count " + count);
				log("Rate " + count / (disconnected - connected) * 1000 + "/s");
				log("Latency " + latency / count + "ms");
//				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}
			
			@Override
			public void onRefresh() {
				log("Refresh " + (Duration.currentTimeMillis() - connected) + "ms");
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
				stop();
			}
		});
	}
	
	public void connectionTest() {
		final int d = 1000 * 1000;
		start(GWT.getModuleBaseURL() + "connection?delay=" + d, new CometListener() {
			double start = Duration.currentTimeMillis();
			double connected;
			double heartbeat;
			double disconnected;
			int count;
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
				count += messages.size();
			}
			
			@Override
			public void onConnected(int hearbeat) {
				connected = Duration.currentTimeMillis();
				log("Connected " + (connected - start) + "ms heartbeat: " + hearbeat);
			}
			
			@Override
			public void onDisconnected() {
				disconnected = Duration.currentTimeMillis();
				log("Disconnected " + (disconnected - connected) + "ms");
				log("Count " + count);
//				stop();
			}
			
			@Override
			public void onHeartbeat() {
				heartbeat = Duration.currentTimeMillis();
				log("Heartbeat " + (heartbeat - connected) + "ms");
			}
			
			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
//				stop();
			}
		});
	}
	
	public void paddingTest() {
		paddingTest(0, 8*1024);
	}
	
	public void paddingTest(final int min, final int max) {
		final int p = (min + max) / 2;
		log("Padding test: " + min + " " + max + " " + p);
		if (min == max) {
			return;
		}
		start(GWT.getModuleBaseURL() + "connection?delay=2000&padding=" + p, new CometListener() {
			@Override
			public void onMessage(List<? extends Serializable> messages) {
			}
			
			@Override
			public void onConnected(int hearbeat) {
				stop();
				paddingTest(min, p - 1);
			}
			
			@Override
			public void onDisconnected() {
				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}
			
			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				stop();
				paddingTest(p + 1, max);
			}
		});
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
	
	public void rpcEscapeTest() {
		CometSerializer serializer = GWT.create(RPCTestCometSerializer.class);
		escapeTest(serializer);
	}
	
	public void derpcEscapeTest() {
		CometSerializer serializer = GWT.create(DeRPCTestCometSerializer.class);
		escapeTest(serializer);
	}
	
	public void escapeTest(final CometSerializer serializer) {
		start(GWT.getModuleBaseURL() + "escape", serializer, new CometListener() {
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
				
				for (Serializable m : messages) {
					String message;
					if (m instanceof TestData[][]) {
						log("GWT Serialized");
						message = ((TestData[][]) m)[0][0].string;
					}
					else if (m instanceof String) {
						log("String");
						message = (String) m;
					}
					else {
						log(String.valueOf(m));
						continue;
					}
					
					if (ESCAPE.length() != message.length()) {
						log("expected message length " + ESCAPE.length() + " acutal " + message.length());
					}
					else {
						for (int i = 0; i < ESCAPE.length(); i++) {
							char expected = ESCAPE.charAt(i);
							char actual = message.charAt(i);
							if (expected != actual) {
								log("expected character " + expected + " 0x" + Integer.toHexString(expected) + " actual " + actual + " 0x" + Integer.toHexString(actual));
							}
						}
					}
					log("ok");
				}
			}
			
			@Override
			public void onConnected(int hearbeat) {
			}
			
			@Override
			public void onDisconnected() {
				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}
			
			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
				stop();
			}
		});
	}

	@SerialTypes( mode=SerialMode.RPC, value={ TestData[][].class })
	public static abstract class RPCTestCometSerializer extends CometSerializer {
	}
	
	@SerialTypes( mode=SerialMode.DE_RPC, value={ TestData[][].class })
	public static abstract class DeRPCTestCometSerializer extends CometSerializer {
	}
	
	public static class TestData implements Serializable {
		public String string;
		public int integer;
		public Integer[] data = new Integer[1];
		public TestData() {
		}
		
		public TestData(int integer, String string) {
			this.integer = integer;
			this.string = string;
		}
	}

	public void rpcSerializeTest() {
		CometSerializer serializer = GWT.create(RPCTestCometSerializer.class);
		serializeTest(serializer);
	}

	public void derpcSerializeTest() {
		CometSerializer serializer = GWT.create(DeRPCTestCometSerializer.class);
		serializeTest(serializer);
	}
	
	public void serializeTest(final CometSerializer serializer) {
		
		final int c = 1000;
		final int b = 10;
		start(GWT.getModuleBaseURL() + "serialize?count=" + c + "&batch=" + b + "&length=" + (c * b * 100), serializer, new CometListener() {
			double start = Duration.currentTimeMillis();
			double connected;
			double disconnected;
			int count;
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
				for (Serializable message : messages) {
					TestData[][] data = (TestData[][]) message;
					if (count != data[0][0].integer) {
						log("expected count " + count + " actual " + data[0][0].integer);
					}
					count++;
				}
			}
			
			@Override
			public void onConnected(int hearbeat) {
				connected = Duration.currentTimeMillis();
				log("Connected " + (connected - start) + "ms heartbeat: " + hearbeat);
			}
			
			@Override
			public void onDisconnected() {
				disconnected = Duration.currentTimeMillis();
				log("Disconnected " + (disconnected - connected) + "ms");
				log("Count " + count);
				log("Rate " + count / (disconnected - connected) * 1000 + "/s");
				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}
			
			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
				stop();
			}
		});
	}

	public void errorTest() {
		start(GWT.getModuleBaseURL() + "error", new CometListener() {
			double start = Duration.currentTimeMillis();
			double connected;
			double disconnected;
			int count;
			
			@Override
			public void onMessage(List<? extends Serializable> messages) {
			}
			
			@Override
			public void onConnected(int hearbeat) {
				connected = Duration.currentTimeMillis();
				log("Connected " + (connected - start) + "ms heartbeat: " + hearbeat);
			}
			
			@Override
			public void onDisconnected() {
				disconnected = Duration.currentTimeMillis();
				log("Disconnected " + (disconnected - connected) + "ms");
				log("Count " + count);
				log("Rate " + count / (disconnected - connected) * 1000 + "/s");
//				stop();
			}
			
			@Override
			public void onHeartbeat() {
			}
			
			@Override
			public void onRefresh() {
			}
			
			@Override
			public void onError(Throwable exception, boolean connected) {
				log(connected + " " + string(exception));
//				stop();
			}
		});
	}

	private static String string(Throwable exception) {
		String result = exception.toString();
		exception = exception.getCause();
		while (exception != null) {
			result += "\n" + exception.toString();
			exception = exception.getCause();
		}
		return result;
	}
}
