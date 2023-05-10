module tdlight.java {
	requires tdlight.api;
	requires org.reactivestreams;
	requires org.slf4j;
	requires jctools.core;
	requires jdk.unsupported;
	requires static com.google.zxing;
	requires static reactor.blockhound;
	exports it.tdlight.tdnative;
	exports it.tdlight;
	exports it.tdlight.util;
	exports it.tdlight.client;
}
