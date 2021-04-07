module tdlight.java {
	requires tdlight.api;
	requires org.reactivestreams;
	requires it.unimi.dsi.fastutil;
	requires org.slf4j;
	exports it.tdlight.tdlight;
	exports it.tdlight.tdlib;
	exports it.tdlight.common;
	exports it.tdlight.common.utils;
	exports it.tdlight.common.internal;
}