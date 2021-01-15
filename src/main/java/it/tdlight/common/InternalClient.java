package it.tdlight.common;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class InternalClient implements ClientEventsHandler, TelegramClient {

	private final ConcurrentHashMap<Long, Handler> handlers = new ConcurrentHashMap<Long, Handler>();

	private final int clientId;
	private final InternalClientManager clientManager;
	private final Handler updateHandler;
	private final MultiHandler updatesHandler;
	private final ExceptionHandler defaultExceptionHandler;

	private final AtomicBoolean isClosed = new AtomicBoolean();

	public InternalClient(InternalClientManager clientManager,
		ResultHandler updateHandler,
		ExceptionHandler updateExceptionHandler,
		ExceptionHandler defaultExceptionHandler) {
		this.updateHandler = new Handler(updateHandler, updateExceptionHandler);
		this.updatesHandler = null;
		this.defaultExceptionHandler = defaultExceptionHandler;
		this.clientManager = clientManager;
		this.clientId = NativeClientAccess.create();

		clientManager.registerClient(clientId, this);
	}

	public InternalClient(InternalClientManager clientManager,
			UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		this.updateHandler = null;
		this.updatesHandler = new MultiHandler(updatesHandler, updateExceptionHandler);
		this.clientManager = clientManager;
		this.defaultExceptionHandler = defaultExceptionHandler;
		this.clientId = NativeClientAccess.create();

		clientManager.registerClient(clientId, this);
	}

	@Override
	public int getClientId() {
		return clientId;
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, Object[] events) {
		if (updatesHandler != null) {
			LongArrayList idsToFilter = new LongArrayList(eventIds);
			ObjectArrayList<Object> eventsToFilter = new ObjectArrayList<>(events);

			for (int i = eventIds.length - 1; i >= 0; i--) {
				if (eventIds[i] != 0) {
					idsToFilter.removeLong(i);
					eventsToFilter.remove(i);

					long eventId = eventIds[i];
					Object event = events[i];

					Handler handler = handlers.remove(eventId);
					handleResponse(eventId, event, handler);
				}
			}

			try {
				updatesHandler.getUpdatesHandler().onUpdates(eventsToFilter);
			} catch (Throwable cause) {
				handleException(updatesHandler.getExceptionHandler(), cause);
			}
		} else {
			for (int i = 0; i < eventIds.length; i++) {
				handleEvent(eventIds[i], events[i]);
			}
		}

		if (isClosed) {
			if (this.isClosed.compareAndSet(false, true)) {
				handleClose();
			}
		}
	}

	private void handleClose() {
		handlers.forEach((eventId, handler) -> {
			handleResponse(eventId, new Error(500, "Instance closed"), handler);
		});
		handlers.clear();
	}

	/**
	 * Handles only a response (not an update!)
	 */
	private void handleResponse(long eventId, Object event, Handler handler) {
		if (handler != null) {
			try {
				handler.getResultHandler().onResult(event);
			} catch (Throwable cause) {
				handleException(handler.getExceptionHandler(), cause);
			}
		} else {
			System.err.println("Unknown event id " + eventId + ", the event has been dropped!");
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, Object event) {
		if (updatesHandler != null || updateHandler == null) throw new IllegalStateException();
		Handler handler = eventId == 0 ? updateHandler : handlers.remove(eventId);
		handleResponse(eventId, event, handler);
	}

	private void handleException(ExceptionHandler exceptionHandler, Throwable cause) {
		if (exceptionHandler == null) {
			exceptionHandler = defaultExceptionHandler;
		}
		if (exceptionHandler != null) {
			try {
				exceptionHandler.onException(cause);
			} catch (Throwable ignored) {}
		}
	}

	@Override
	public void send(Function query, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
		if (isClosedAndMaybeThrow(query)) {
			resultHandler.onResult(new TdApi.Ok());
		}
		long queryId = clientManager.getNextQueryId();
		if (resultHandler != null) {
			handlers.put(queryId, new Handler(resultHandler, exceptionHandler));
		}
		NativeClientAccess.send(clientId, queryId, query);
	}

	@Override
	public Object execute(Function query) {
		if (isClosedAndMaybeThrow(query)) {
			return new TdApi.Ok();
		}
		return NativeClientAccess.execute(query);
	}

	/**
	 *
	 * @param function function used to check if the check will be enforced or not. Can be null
	 * @return true if closed
	 */
	private boolean isClosedAndMaybeThrow(Function function) {
		boolean closed = isClosed.get();
		if (closed) {
			if (function != null && function.getConstructor() == TdApi.Close.CONSTRUCTOR) {
				return true;
			} else {
				throw new IllegalStateException("The client is closed!");
			}
		}
		return false;
	}
}
