gwt-comet
=========

A Comet Implementation for the Google Web Toolkit

This is the new GitHub home of the [Google Code project](https://code.google.com/p/gwt-comet)

Overview
========

This gwt-comet library provides an efficient Comet implementation for GWT.

The library implements Comet by streaming messages over long lived HTTP requests to minimise latency and bandwidth requirements and maximise the throughput.  This is opposed to many other implementations which use polling or long polling techniques.

Features
--------

* Native Comet implementations specific to browsers removing loading artifacts, reducing latency and bandwidth.
* Supports string and GWT-serialized messages.
* Supports deflate compression.
* Supports heartbeat messages to detect connection issues.
* Support cross domain connections. (only sub-domain connections are supported in IE).
* Support for directly sending messages to the HTTP response or establishing a Comet session for message queue based processing.
* Support for keeping the HTTP session alive when only the comet connection is in use and no other client HTTP requests are being made which would normally cause the HTTP session to timeout.

Browsers
--------

The latest Safari and Chrome implementations use [Server-Sent Events](http://dev.w3.org/html5/eventsource/)

The Firefox and older Safari and Chrome implementations use a XMLHTTPRequest and listen for ```onreadystatechange``` events to process the incoming messages.

The Opera implementation uses Opera's built in proprietary [Server-Sent Events](http://my.opera.com/WebApplications/blog/show.dml/438711).

The Internet Explorer implementation is based on the [ActiveXObject HTML file transport](http://cometdaily.com/2007/11/18/ie-activexhtmlfile-transport-part-ii/).
Put simply an ActiveXObject HTML file containing an ```<iframe>``` referencing the appropriate server url is created. The server sends ```<script>``` tags to the the ```<iframe>``` which are then executed. The Java Script in the ```<script>``` tags contain a call to a GWT method with a string parameter containing the message payload. 
This implementation has several improvements over the implementation on which it is based:

* Connection/disconnection events.
* Minimal initial padding ensuring IE starts processing the ```<iframe>``` document immediately.
* Minification of the Java Script turning the ```parent.callback``` method into a single character to minimize bandwidth requirements.
* Support for batching multiple messages into a single ```<script>``` tag to minimize bandwidth.
* removal of ```<script>``` tags from the DOM once they are not needed to minimise memory requirements.

Servers
-------

gwt-comet takes advantage of Glassfish's and embedded Grizzly's support for non-blocking sending and receiving and a pool of threads servicing the Comet connections.

Otherwise by default standard servlets are supported requiring one thread per Comet connection which will work on any Java web application server.

Downloads
---------

There are a few downloads:

* gwt-comet.jar the library required to write code against.
* gwt-comet-examples.war a sample war file including a gwt client which tests various aspects of the Comet implementation including throughput, latency, serialization and connection maintenance and a gwt chat client. To try these out deploy the war in your favorite web server and navigate to the url ```<context>/net.zschech.gwt.comettest.CometTest/CometTest.html``` and ```<context>/net.zschech.gwt.chat.Chat/Chat.html```
* gwt-event-source.jar provides a GWT client implementation of [EventSource](https://code.google.com/p/gwt-comet/wiki/EventSource).
* gwt-web-sockets.jar provides a GWT client implementation of [WebSockets](https://code.google.com/p/gwt-comet/wiki/WebSockets).

More Info
---------

* [Getting Started](https://code.google.com/p/gwt-comet/wiki/GettingStarted)
* [Road Map](https://code.google.com/p/gwt-comet/wiki/RoadMap)
* [Release Notes](https://code.google.com/p/gwt-comet/wiki/ReleaseNotes)

If you use this project let me know by sending me, the project owner, an email.
If you have any questions, thoughts, or ideas send them to the [discussion group](http://groups.google.com/group/gwt-comet).

Checkout my other projects: [gwt-java-math](http://code.google.com/p/gwt-java-math).
