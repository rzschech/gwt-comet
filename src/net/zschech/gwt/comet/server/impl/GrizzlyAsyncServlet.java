/*
 * Copyright 2010 Richard Zschech.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Selector;

import javax.servlet.http.HttpSession;

import com.sun.grizzly.http.servlet.HttpSessionImpl;

/**
 * AsyncServlet implementation for Grizzly embedded.
 * 
 * @author Richard Zschech
 */
public class GrizzlyAsyncServlet extends AbstractGrizzlyAsyncServlet {
	
	private final Method accessMethod;
	
	public GrizzlyAsyncServlet() throws SecurityException, NoSuchFieldException, NoSuchMethodException {
		accessMethod = HttpSessionImpl.class.getDeclaredMethod("access");
		accessMethod.setAccessible(true);
	}
	
	@Override
	protected boolean access(HttpSession httpSession) {
		try {
			accessMethod.invoke(httpSession);
			return true;
		}
		catch (IllegalArgumentException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (IllegalAccessException e) {
			log("Error updating session last access time", e);
			return false;
		}
		catch (InvocationTargetException e) {
			log("Error updating session last access time", e);
			return false;
		}
	}

	@Override
	protected Selector getSelector(CometServletResponseImpl response) {
		return (Selector) get("response.response.hook.selectorHandler.selector", response.getResponse());
	}
}
