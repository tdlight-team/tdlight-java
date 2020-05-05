package it.ernytech.tdlib.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;

public interface BoundedExecutorService extends ExecutorService {

	static BoundedExecutorService create(int maxQueueSize,
			int corePoolSize,
			int maxPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			@Nullable BiConsumer<Boolean, Integer> queueSizeStatus) {
		return new BoundedExecutorServiceImpl(maxQueueSize, corePoolSize, maxPoolSize, keepAliveTime, unit,
				Executors.defaultThreadFactory(), queueSizeStatus);
	}
	static BoundedExecutorService create(int maxQueueSize,
			int corePoolSize,
			int maxPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			ThreadFactory threadFactory,
			@Nullable BiConsumer<Boolean, Integer> queueSizeStatus) {
		return new BoundedExecutorServiceImpl(maxQueueSize, corePoolSize, maxPoolSize, keepAliveTime, unit, threadFactory, queueSizeStatus);
	}

	<T> Future<T> submitButBlockIfFull(Callable<T> task) throws InterruptedException;

	void executeButBlockIfFull(Runnable task) throws InterruptedException;
}
