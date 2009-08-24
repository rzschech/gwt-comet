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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class FlushableDeflaterOutputStream extends DeflaterOutputStream {
	
	public FlushableDeflaterOutputStream(final OutputStream out) {
		// Using Deflater with nowrap == true will ommit headers and trailers
		super(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
	}
	
	private static final byte[] EMPTYBYTEARRAY = new byte[0];
	
	/**
	 * Insure all remaining data will be output.
	 */
	public void flush() throws IOException {
		/**
		 * Now this is tricky: We force the Deflater to flush its data by
		 * switching compression level. As yet, a perplexingly simple workaround
		 * for http://developer.java.sun.com/developer/bugParade/bugs/4255743.html
		 */
		if (!def.finished()) {
			def.setInput(EMPTYBYTEARRAY, 0, 0);
			
			def.setLevel(Deflater.NO_COMPRESSION);
			deflate();
			
			def.setLevel(Deflater.DEFAULT_COMPRESSION);
			deflate();
			
			out.flush();
		}
	}
}
