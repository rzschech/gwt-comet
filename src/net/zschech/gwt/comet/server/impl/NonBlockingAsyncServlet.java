package net.zschech.gwt.comet.server.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

public abstract class NonBlockingAsyncServlet extends AsyncServlet {
	
	private ScheduledExecutorService scheduledExecutor;
	
	@Override
	protected void init(ServletContext context) throws ServletException {
		super.init(context);
		
		scheduledExecutor = new RemoveOnCancelScheduledThreadPoolExecutor(1, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				String name = getServletContext().getServletContextName();
				if (name == null || name.isEmpty()) {
					name = getServletContext().getContextPath();
				}
				return new Thread(runnable, "gwt-comet " + name);
			}
		});
	}
	
	@Override
	protected void shutdown() {
		scheduledExecutor.shutdown();
	}
	
	protected ScheduledExecutorService getScheduledExecutor() {
		return scheduledExecutor;
	}

	@Override
	public ScheduledFuture<?> scheduleHeartbeat(final CometServletResponseImpl response, CometSessionImpl session) {
		assert Thread.holdsLock(response);
		return scheduledExecutor.schedule(new Runnable() {
			@Override
			public void run() {
				response.tryHeartbeat();
			}
		}, response.getHeartbeat(), TimeUnit.MILLISECONDS);
	}
	
	@Override
	public ScheduledFuture<?> scheduleSessionKeepAlive(final CometServletResponseImpl response, final CometSessionImpl session) {
		assert Thread.holdsLock(response);
		try {
			HttpSession httpSession = session.getHttpSession();
			if (access(httpSession)) {
				return null;
			}
			
			long keepAliveTime = response.getSessionKeepAliveScheduleTime();
			if (keepAliveTime <= 0) {
				response.tryTerminate();
				return null;
			}
			else if (keepAliveTime == Long.MAX_VALUE) {
				return null;
			}
			else {
				return scheduledExecutor.schedule(new Runnable() {
					@Override
					public void run() {
						session.setLastAccessedTime(System.currentTimeMillis());
						response.scheduleSessionKeepAlive();
					}
				}, keepAliveTime, TimeUnit.MILLISECONDS);
			}
		}
		catch (IllegalStateException e) {
			// the session has been invalidated
			response.tryTerminate();
			return null;
		}
	}
}
