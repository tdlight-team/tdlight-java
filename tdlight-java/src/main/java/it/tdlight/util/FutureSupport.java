package it.tdlight.util;

import java.util.concurrent.CompletableFuture;

public class FutureSupport {

	public static <T> CompletableFuture<T> copy(CompletableFuture<T> future) {
		return CompletableFuture.completedFuture(true).thenCompose(ignored -> future);
	}
}
