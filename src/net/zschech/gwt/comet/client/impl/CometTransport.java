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

import net.zschech.gwt.comet.client.CometClient;
import net.zschech.gwt.comet.client.CometListener;

import com.google.gwt.core.client.Duration;

/**
 * This is the base class for the comet implementations
 * 
 * @author Richard Zschech
 */
public abstract class CometTransport {
	
	protected CometClient client;
	protected CometListener listener;
	
	public void initiate(CometClient client, CometListener listener) {
		this.client = client;
		this.listener = listener;
	}
	
	public abstract void connect();
	
	public abstract void disconnect();
	
	public String getUrl() {
		String url = client.getUrl();
		return url + (url.contains("?") ? "&" : "?") + Integer.toString((int)(Duration.currentTimeMillis() % Integer.MAX_VALUE), Character.MAX_RADIX).toUpperCase();
	}
}
