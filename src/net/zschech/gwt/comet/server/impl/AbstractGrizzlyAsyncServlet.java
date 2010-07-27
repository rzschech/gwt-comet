/*
 * Copyright 2010 Richard Zschech.
 * 
 * This is commercial code. Do not distribute.
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.zschech.gwt.comet.server.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.comet.CometWriter;

public abstract class AbstractGrizzlyAsyncServlet extends NonBlockingAsyncServlet {
	
	private final Field socketChannelField;
	
	private CometEngine cometEngine;
	private CometContext<?> cometContext;
	private Selector selector;
	
	public AbstractGrizzlyAsyncServlet() throws SecurityException, NoSuchFieldException {
		socketChannelField = CometWriter.class.getDeclaredField("socketChannel");
		socketChannelField.setAccessible(true);
	}
	
	@Override
	public void init(ServletContext context) throws ServletException {
		super.init(context);
		try {
			cometEngine = CometEngine.getEngine();
			cometContext = cometEngine.register(context.getContextPath());
			cometContext.setExpirationDelay(-1);
			cometContext.setBlockingNotification(true);
		}
		catch (IllegalStateException e) {
			throw new ServletException(e.getMessage());
		}
	}
	
	@Override
	protected void shutdown() {
		cometEngine.unregister(getServletContext().getContextPath());
		super.shutdown();
	}
	
	@Override
	public OutputStream getOutputStream(OutputStream outputStream) {
		return new GrizzyOutputStream(outputStream);
	}
	
	@Override
	public Object suspend(CometServletResponseImpl response, CometSessionImpl session, HttpServletRequest request) throws IOException {
		assert Thread.holdsLock(response);
		assert session == null || !Thread.holdsLock(session);
		
		// Unfortunately we have to flush the response line and headers before switching to async mode :-(
		response.flush();
		
		initSelector(response);
		
		CometHandlerImpl handler = new CometHandlerImpl(response);
		try {
			cometContext.addCometHandler(handler);
		}
		catch (IllegalStateException e) {
			throw new IOException(e.getMessage());
		}
		
		if (session != null) {
			GrizzyOutputStream asyncOutputStream = (GrizzyOutputStream) response.getAsyncOutputStream();
			asyncOutputStream.wrapped = new GrizzyAsyncBufferOutputStream(handler);
		}
		
		return handler;
	}
	
	@Override
	public void terminate(CometServletResponseImpl response, CometSessionImpl session, boolean serverInitiated, Object suspendInfo) {
		assert Thread.holdsLock(response);
		assert session == null || !Thread.holdsLock(session);
		
		final CometHandlerImpl handler = (CometHandlerImpl) suspendInfo;
		if (serverInitiated) {
			if (session != null) {
				handler.registerAsyncWrite();
			}
			else {
				cometContext.resumeCometHandler(handler);
			}
		}
	}
	
	@Override
	public void invalidate(CometSessionImpl session) {
		enqueued(session);
	}
	
	@Override
	public void enqueued(CometSessionImpl session) {
		CometServletResponseImpl response = session.getResponse();
		if (response != null) {
			if (response.setProcessing(true)) {
				synchronized (response) {
					if (!response.isTerminated()) {
						Object suspendInfo = response.getSuspendInfo();
						if (suspendInfo != null) {
							CometHandlerImpl handler = (CometHandlerImpl) suspendInfo;
							handler.registerAsyncWrite();
						}
					}
				}
			}
		}
	}
	
	private Selector initSelector(CometServletResponseImpl response) {
		if (selector != null) {
			return selector;
		}
		
		selector = getSelector(response);
		return selector;
	}
	
	protected abstract Selector getSelector(CometServletResponseImpl response);
	
	private class CometHandlerImpl implements CometHandler<Object> {
		
		private final CometServletResponseImpl response;
		private final boolean chunked;
		private volatile boolean active;
		private volatile ByteBuffer buffer;
		private volatile AtomicInteger activeFailureCount = new AtomicInteger();
		private AtomicBoolean registered = new AtomicBoolean();
		
		public CometHandlerImpl(CometServletResponseImpl response) {
			this.response = response;
			this.chunked = response.getResponse().containsHeader("Transfer-Encoding");
		}
		
		@Override
		public void attach(Object attachment) {
		}
		
		public void registerAsyncWrite() {
			assert Thread.holdsLock(response);
			// Unfortunately Grizzly does not setup it CometContext with the CometHandler immediately so we have to schedule it
			// until it is active. See CometEngine.handle(AsyncProcessorTask) executeServlet() is called before cometContext.addActiveHandler(cometTask)
			if (!active && !response.isTerminated() && activeFailureCount.get() < 100) {
				cometEngine.getThreadPool().execute(new Runnable() {
					@Override
					public void run() {
						try {
							cometContext.registerAsyncWrite(CometHandlerImpl.this);
							selector.wakeup();
							if (activeFailureCount.getAndSet(0) != 0) {
								log("Comet handler " + Integer.toHexString(hashCode()) + " active");
							}
						}
						catch (IllegalStateException e) {
//							log("Comet handler " + Integer.toHexString(hashCode()) + " not active yet, retrying: " + e.getMessage());
							activeFailureCount.incrementAndGet();
							registerAsyncWrite();
						}
					}
				});
			}
			else {
				if (registered.compareAndSet(false, true)) {
					try {
						cometContext.registerAsyncWrite(this);
						selector.wakeup();
						if (activeFailureCount.getAndSet(0) != 0) {
							log("Comet handler " + Integer.toHexString(hashCode()) + " active");
						}
					}
					catch (CancelledKeyException e) {
						if (!response.isTerminated()) {
							response.setTerminated(true);
						}
					}
					catch (IllegalStateException e) {
	//					log("Comet handler " + Integer.toHexString(hashCode()) + " not active yet, giving up: " + e.getMessage());
					}
				}
			}
		}
		
		@Override
		public void onInitialize(CometEvent event) throws IOException {
			synchronized (response) {
				if (response.checkSessionQueue(false)) {
					registerAsyncWrite();
				}
			}
		}
		
		@Override
		public void onInterrupt(CometEvent event) throws IOException {
			terminate();
		}
		
		@Override
		public void onTerminate(CometEvent event) throws IOException {
			terminate();
		}
		
		private void terminate() {
			synchronized (response) {
				if (!response.isTerminated()) {
					response.setTerminated(false);
				}
			}
		}
		
		@Override
		public void onEvent(CometEvent event) throws IOException {
			active = true;
			if (event.getType() == CometEvent.WRITE) {
				CometWriter writer = (CometWriter) event.attachment();
				
				try {
					// Unfortunately Grizzly's CometWriter assumes that the transfer encoding is going to be chunked and that
					// only one chunk is going to be written to the response so we have to write to the SocketChanel directly.
					// https://grizzly.dev.java.net/issues/show_bug.cgi?id=791
					SocketChannel socketChannel = (SocketChannel) socketChannelField.get(writer);
					
					GrizzyAsyncBufferOutputStream output = (GrizzyAsyncBufferOutputStream) ((GrizzyOutputStream) response.getAsyncOutputStream()).getWrapped();
					
					while (true) {
						if (buffer == null || !buffer.hasRemaining()) {
							synchronized (response) {
								int count;
								while ((count = output.getCount()) == 0 && response.checkSessionQueue(false)) {
									response.writeSessionQueue(true);
								}
								
								if (count == 0) {
									buffer = null;
									break;
								}
								
								byte[] bytes = output.getBytes();
								if (chunked) {
									byte[] chunkLength = Integer.toHexString(count).getBytes("ASCII");
									
									buffer = ByteBuffer.allocate(chunkLength.length + count + 4);
									buffer.put(chunkLength);
									buffer.put((byte) '\r');
									buffer.put((byte) '\n');
									buffer.put(bytes, 0, count);
									buffer.put((byte) '\r');
									buffer.put((byte) '\n');
									buffer.flip();
								}
								else {
									buffer = ByteBuffer.wrap(bytes, 0, count);
								}
								output.clear();
							}
						}
						
						int write = socketChannel.write(buffer);
						if (write <= 0) {
							// can't write any more
							cometContext.registerAsyncWrite(this);
							break;
						}
						else if (!buffer.hasRemaining()) {
							buffer = null;
						}
					}
					
					if (buffer == null) {
						if (response.isTerminated()) {
							cometContext.resumeCometHandler(this);
						}
						else {
							registered.set(false);
							response.setProcessing(false);
						}
					}
				}
				catch (IllegalArgumentException e) {
					log("Error accessing socket chanel", e);
				}
				catch (IllegalAccessException e) {
					log("Error accessing socket chanel", e);
				}
			}
		}
	}
	
	private static class GrizzyOutputStream extends OutputStream {
		
		private volatile OutputStream wrapped;
		
		public GrizzyOutputStream(OutputStream wrapped) {
			this.wrapped = wrapped;
		}
		
		@Override
		public void write(byte[] b) throws IOException {
			wrapped.write(b);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			wrapped.write(b, off, len);
		}
		
		@Override
		public void write(int b) throws IOException {
			wrapped.write(b);
		}
		
		@Override
		public void close() throws IOException {
			wrapped.close();
		}
		
		@Override
		public void flush() throws IOException {
			wrapped.flush();
		}
		
		public OutputStream getWrapped() {
			return wrapped;
		}
	}
	
	private static class GrizzyAsyncBufferOutputStream extends ByteArrayOutputStream {
		
		private final CometHandlerImpl handler;
		
		private GrizzyAsyncBufferOutputStream(CometHandlerImpl handler) {
			this.handler = handler;
		}
		
		@Override
		public void write(byte[] b) {
			write(b, 0, b.length);
		}
		
		@Override
		public void write(byte[] b, int off, int len) {
			super.write(b, off, len);
			handler.registerAsyncWrite();
		}
		
		@Override
		public void write(int b) {
			super.write(b);
			handler.registerAsyncWrite();
		}
		
		public byte[] getBytes() {
			return buf;
		}
		
		public int getCount() {
			return count;
		}
		
		public void clear() {
			count = 0;
			buf = new byte[buf.length];
		}
	}
}
