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

import java.lang.reflect.Field;
import java.nio.channels.Selector;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;
import org.apache.catalina.session.StandardSessionFacade;

/**
 * AsyncServlet implementation for GlassFish.
 * 
 * @author Richard Zschech
 */
public class GlassFishAsyncServlet extends AbstractGrizzlyAsyncServlet {
	
	private final Field sessionField;
	
	public GlassFishAsyncServlet() throws SecurityException, NoSuchFieldException {
		sessionField = StandardSessionFacade.class.getDeclaredField("session");
		sessionField.setAccessible(true);
	}
	
	@Override
	protected boolean access(HttpSession httpSession) {
		try {
			Session catalinaSession = (Session) sessionField.get(httpSession);
			catalinaSession.access();
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
	}
	
	@Override
	protected Selector getSelector(CometServletResponseImpl response) {
		return (Selector) get("response.facade.response.coyoteResponse.hook.selectorHandler.selector", response.getResponse());
	}
}
