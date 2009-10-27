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
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.zschech.gwt.comet.server.CometServlet;

import com.google.gwt.user.server.rpc.SerializationPolicy;

public abstract class ManagedStreamCometServletResponseImpl extends CometServletResponseImpl {
	private CountOutputStream countOutputStream;
	
	public ManagedStreamCometServletResponseImpl(HttpServletRequest request, HttpServletResponse response, SerializationPolicy serializationPolicy, CometServlet servlet, AsyncServlet async, int heartbeat) {
		super(request, response, serializationPolicy, servlet, async, heartbeat);
	}
	
	@Override
	protected OutputStream getOutputStream(OutputStream outputStream) {
		countOutputStream = new CountOutputStream(outputStream);
		return countOutputStream;
	}
	
	@Override
	public void doSuspend() throws IOException {
		countOutputStream.setIgnoreFlush(true);
		writer.flush();
		countOutputStream.setIgnoreFlush(false);
		int written = countOutputStream.getCount();
		CharSequence padding = getPadding(written);
		if (padding != null) {
			writer.append(padding);
		}
	}
	
	@Override
	public synchronized void write(List<? extends Serializable> messages, boolean flush) throws IOException {
		super.write(messages, flush);
		if (isOverMaxLength(countOutputStream.getCount())) {
			terminate();
		}
	}
	
	@Override
	public synchronized void heartbeat() throws IOException {
		super.heartbeat();
		if (isOverMaxLength(countOutputStream.getCount())) {
			terminate();
		}
	}
	
	protected abstract CharSequence getPadding(int written);
	
	protected abstract boolean isOverMaxLength(int written);
}
