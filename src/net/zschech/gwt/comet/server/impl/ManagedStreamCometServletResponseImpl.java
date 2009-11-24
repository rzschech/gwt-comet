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
	private boolean refresh;
	
	protected Integer padding;
	protected Integer length;
	
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
		String paddingParameter = getRequest().getParameter("padding");
		if (paddingParameter != null) {
			padding = Integer.parseInt(paddingParameter);
		}
		
		String lengthParameter = getRequest().getParameter("length");
		if (lengthParameter != null) {
			length = Integer.parseInt(lengthParameter);
		}
		
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
		checkLength();
	}
	
	@Override
	public synchronized void heartbeat() throws IOException {
		super.heartbeat();
		checkLength();
	}
	
	private void checkLength() throws IOException {
		int count = countOutputStream.getCount();
		if (!refresh && isOverRefreshLength(count)) {
			refresh = true;
			System.out.println("- doRefresh " + this.hashCode());
			doRefresh();
		}
		else if (isOverTerminateLength(count)) {
			terminate();
		}
	}
	
	protected abstract void doRefresh() throws IOException;
	
	protected abstract CharSequence getPadding(int written);
	
	protected abstract boolean isOverRefreshLength(int written);

	protected abstract boolean isOverTerminateLength(int written);
}
