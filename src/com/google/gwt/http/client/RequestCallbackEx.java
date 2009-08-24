package com.google.gwt.http.client;

public interface RequestCallbackEx extends RequestCallback {
	
	void onResponseReceiving(Request request, Response response);
}
