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

import net.zschech.gwt.comet.server.CometServlet;

import com.google.gwt.user.server.rpc.SerializationPolicy;

/**
 * The CometServletResponse for the {@link IEHTMLFileCometTransport}
 * 
 * @author Richard Zschech
 */
public class IEHTMLFileCometServletResponse extends ManagedStreamCometServletResponseImpl {
	
	// IE requires padding to start processing the page.
	private static final int PADDING_REQUIRED = 256;
	
	private static final String HEAD = "<html><body onload='parent.d()'><script>parent.c(";
	private static final String TAIL = ");var s=parent.s;var o=parent.o;var h=parent.h;</script>";
	
	private static final String PADDING_STRING;
	static {
		// the required padding minus the length of the heading
		int capacity = PADDING_REQUIRED - HEAD.length() - TAIL.length();
		char[] padding = new char[capacity];
		for (int i = 0; i < capacity; i++) {
			padding[i] = ' ';
		}
		PADDING_STRING = new String(padding);
	}
	
	public IEHTMLFileCometServletResponse(HttpServletRequest request, HttpServletResponse response, SerializationPolicy serializationPolicy, CometServlet servlet, AsyncServlet async, int heartbeat) {
		super(request, response, serializationPolicy, servlet, async, heartbeat);
	}
	
	@Override
	public void initiate() throws IOException {
		getResponse().setContentType("text/html");
		
		super.initiate();
		
		String heading = HEAD + getHeartbeat() + TAIL;
		writer.append(heading);
	}
	
	@Override
	protected CharSequence getPadding(int written) {
		if (padding != null) {
			if (written < padding) {
				StringBuilder result = new StringBuilder(padding - written);
				for (int i = written; i < padding; i++) {
					result.append(' ');
				}
				return result;
			}
			else {
				return null;
			}
		}
		
		int padding = PADDING_REQUIRED;
		if (written < padding) {
			return PADDING_STRING.substring(0, padding - written);
		}
		else {
			return null;
		}
	}
	
	@Override
	protected void doSendError(int statusCode, String message) throws IOException {
		getResponse().setContentType("text/html");
		writer.append("<html><script>parent.e(").append(Integer.toString(statusCode));
		if (message != null) {
			writer.append(",\'").append(escapeString(message)).append('\'');
		}
		writer.append(")</script></html>");
	}
	
	@Override
	protected void doWrite(List<? extends Serializable> messages) throws IOException {
		writer.append("<script>");
		for (Serializable message : messages) {
			CharSequence string;
			char event;
			if (message instanceof CharSequence) {
				string = escapeString((CharSequence) message);
				event = 's';
			}
			else {
				string = escapeObject(serialize(message));
				event = 'o';
			}
			writer.append(event).append("('").append(string).append("');");
		}
		writer.append("</script>");
	}
	
	@Override
	protected boolean isOverMaxLength(int written) {
		if (length != null) {
			return written > length;
		}
		else {
			return written > 4 * 1024 * 1024;
		}
	}
	
	@Override
	protected void doHeartbeat() throws IOException {
		writer.append("<script>h();</script>");
	}
	
	@Override
	protected void doTerminate() throws IOException {
		writer.append("<script>parent.t();</script>");
	}
	
	private static CharSequence escapeString(CharSequence string) {
		int length = (string != null) ? string.length() : 0;
		int i = 0;
		loop: while (i < length) {
			char ch = string.charAt(i);
			switch (ch) {
			case '\'':
			case '\\':
			case '/':
			case '\b':
			case '\f':
			case '\n':
			case '\r':
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
			case '\'':
				str.append("\\\'");
				break;
			case '\\':
				str.append("\\\\");
				break;
			case '/':
				str.append("\\/");
				break;
			case '\b':
				str.append("\\b");
				break;
			case '\f':
				str.append("\\f");
				break;
			case '\n':
				str.append("\\n");
				break;
			case '\r':
				str.append("\\r");
				break;
			case '\t':
				str.append("\\t");
				break;
			default:
				str.append(ch);
			}
			i++;
		}
		return str;
	}
	
	private static CharSequence escapeObject(CharSequence string) {
		int length = (string != null) ? string.length() : 0;
		int i = 0;
		loop: while (i < length) {
			char ch = string.charAt(i);
			switch (ch) {
			case '\'':
			case '\\':
			case '/':
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
			case '\'':
				str.append("\\\'");
				break;
			case '\\':
				str.append("\\\\");
				break;
			case '/':
				str.append("\\/");
				break;
			default:
				str.append(ch);
			}
			i++;
		}
		return str;
	}
}
