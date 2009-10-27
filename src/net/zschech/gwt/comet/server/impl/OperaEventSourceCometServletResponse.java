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

import net.zschech.gwt.comet.client.impl.OperaEventSourceCometTransport;
import net.zschech.gwt.comet.server.CometServlet;

import com.google.gwt.user.server.rpc.SerializationPolicy;

/**
 * The CometServletResponse for the {@link OperaEventSourceCometTransport}
 * 
 * @author Richard Zschech
 */
public class OperaEventSourceCometServletResponse extends CometServletResponseImpl {
	
	public OperaEventSourceCometServletResponse(HttpServletRequest request, HttpServletResponse response, SerializationPolicy serializationPolicy, CometServlet servlet, AsyncServlet async, int heartbeat) {
		super(request, response, serializationPolicy, servlet, async, heartbeat);
	}
	
	@Override
	public void initiate() throws IOException {
		getResponse().setContentType("application/x-dom-event-stream");
		
		super.initiate();
		
		writer.append("Event: c\ndata: c").append(String.valueOf(getHeartbeat())).append("\n\n");
	}
	
	@Override
	protected void doSendError(int statusCode, String message) throws IOException {
		getResponse().setContentType("application/x-dom-event-stream");
		writer.append("Event: c\ndata: e").append(String.valueOf(statusCode));
		if (message != null) {
			writer.append(' ').append(HTTPRequestCometServletResponse.escape(message));
		}
		writer.append("\n\n");
	}
	
	@Override
	protected void doSuspend() throws IOException {
	}
	
	@Override
	protected void doWrite(List<? extends Serializable> messages) throws IOException {
		for (Serializable message : messages) {
			CharSequence string;
			char event;
			if (message instanceof CharSequence) {
				string = (CharSequence) message;
				event = 's';
			}
			else {
				string = serialize(message);
				event = 'o';
			}
			writer.append("Event: ").append(event).append('\n');
			writer.append("data: ").append(HTTPRequestCometServletResponse.escape(string)).append("\n\n");
		}
	}
	
	@Override
	protected void doHeartbeat() throws IOException {
		writer.append("Event: c\ndata: h\n\n");
	}
	
	@Override
	public void doTerminate() throws IOException {
		writer.append("Event: c\ndata: d\n\n");
	}
}
