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
import java.io.Serializable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.zschech.gwt.comet.client.impl.HTTPRequestCometTransport;
import net.zschech.gwt.comet.server.CometServlet;

import com.google.gwt.user.server.rpc.SerializationPolicy;

/**
 * The CometServletResponse for the {@link HTTPRequestCometTransport}
 * 
 * @author Richard Zschech
 */
public class HTTPRequestCometServletResponse extends ManagedStreamCometServletResponseImpl {
	
	private static final int MAX_PADDING_REQUIRED = 256;
	private static final String PADDING_STRING;
	static {
		char[] padding = new char[MAX_PADDING_REQUIRED];
		for (int i = 0; i < padding.length - 1; i++) {
			padding[i] = '*';
		}
		padding[padding.length - 1] = '\n';
		PADDING_STRING = new String(padding);
	}
	
	private final boolean chrome;
	private int clientMemory;
	
	public HTTPRequestCometServletResponse(HttpServletRequest request, HttpServletResponse response, SerializationPolicy serializationPolicy, CometServlet servlet, AsyncServlet async, int heartbeat) {
		super(request, response, serializationPolicy, servlet, async, heartbeat);
		
		String userAgent = getRequest().getHeader("User-Agent");
		chrome = userAgent != null && userAgent.contains("Chrome");
	}
	
	@Override
	public void initiate() throws IOException {
		getResponse().setContentType("text/plain");
		
		super.initiate();
		
		// send connection event to client
		writer.append('!').append(String.valueOf(getHeartbeat())).append('\n');
	}
	
	@Override
	protected CharSequence getPadding(int written) {
		if (getRequest().getParameter("padding") != null) {
			// System.out.println("Written " + written);
			int padding = Integer.parseInt(getRequest().getParameter("padding"));
			if (written < padding) {
				StringBuilder result = new StringBuilder(padding - written);
				for (int i = written; i < padding - 2; i++) {
					result.append('*');
				}
				result.append('\n');
				return result;
			}
			else {
				return null;
			}
		}
		
		int paddingRequired;
		if (chrome) {
			if (getRequest().getScheme().equals("https")) {
				paddingRequired = 64;
			}
			else {
				paddingRequired = 42;
			}
		}
		else {
			paddingRequired = 0;
		}
		
		if (written < paddingRequired) {
			return PADDING_STRING.substring(written);
		}
		else {
			return null;
		}
	}
	
	@Override
	protected void doSendError(int statusCode, String message) throws IOException {
		getResponse().setStatus(statusCode);
		if (message != null) {
			writer.append(message);
		}
	}
	
	@Override
	protected void doWrite(List<? extends Serializable> messages) throws IOException {
		clientMemory *= 2;
		for (Serializable message : messages) {
			CharSequence string;
			if (message instanceof CharSequence) {
				string = escape((CharSequence) message);
				if (string == message) {
					writer.append('|');
				}
				else {
					writer.append(']');
				}
			}
			else {
				string = serialize(message);
			}
			
			writer.append(string).append('\n');
			clientMemory += string.length() + 1;
		}
	}
	
	@Override
	protected boolean isOverMaxLength(int written) {
//		 if (chrome) {
//		 Chrome seems to have a problem with lots of small messages consuming lots of memory.
//		 I'm guessing for each readyState = 3 event it copies the responseText from its IO system to its JavaScript
//		 engine and does not clean up all the events until the HTTP request is finished.
		 return clientMemory > 1024 * 1024;
//		 }
//		 else {
//		return false;//written > 2 * 1024 * 1024;
//		 }
	}
	
	@Override
	protected void doHeartbeat() throws IOException {
		writer.append("#\n");
	}
	
	@Override
	protected void doTerminate() throws IOException {
		writer.append("?\n");
	}
	
	static CharSequence escape(CharSequence string) {
		int length = (string != null) ? string.length() : 0;
		int i = 0;
		loop: while (i < length) {
			char ch = string.charAt(i);
			switch (ch) {
			case '\\':
			case '\n':
				break loop;
			}
			i++;
		}
		
		if (i == length)
			return string;
		
		StringBuilder str = new StringBuilder(string.length() * 2);
		str.append(string, 0, i);
		while (i < length) {
			char ch = string.charAt(i);
			switch (ch) {
			case '\\':
				str.append("\\\\");
				break;
			case '\n':
				str.append("\\n");
				break;
			default:
				str.append(ch);
			}
			i++;
		}
		return str;
	}
}
