package it.tdlight.tdlight;

import it.tdlight.tdlib.NativeClient;
import it.tdlight.tdlib.TdApi;
import it.tdlight.tdlib.TdApi.AuthorizationStateClosed;
import it.tdlight.tdlib.TdApi.AuthorizationStateClosing;
import it.tdlight.tdlib.TdApi.AuthorizationStateWaitTdlibParameters;
import it.tdlight.tdlib.TdApi.GetOption;
import it.tdlight.tdlib.TdApi.Object;
import it.tdlight.tdlib.TdApi.SetOption;
import it.tdlight.tdlib.TdApi.UpdateAuthorizationState;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Interface for interaction with TDLib.
 */
public class Client extends NativeClient implements TelegramClient {

	private ClientState state = ClientState.of(false, 0, false, false, false);
	private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

	/**
	 * Creates a new TDLib client.
	 */
	public Client() {
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			System.exit(1);
		}
		this.initializeClient();
	}

	@Override
	public void send(Request request) {
		long clientId;
		stateLock.readLock().lock();
		try {
			requireInitialized();
			requireReadyToSend(request.getFunction().getConstructor());
			clientId = state.getClientId();
		} finally {
			stateLock.readLock().unlock();
		}

		nativeClientSend(clientId, request.getId(), request.getFunction());
	}

	@Override
	public List<Response> receive(double timeout, int eventsSize, boolean receiveResponses, boolean receiveUpdates) {
		long clientId;
		stateLock.readLock().lock();
		try {
			if (!state.isInitialized()) {
				sleep(timeout);
				return Collections.emptyList();
			}
			requireInitialized();
			if (!state.isReadyToReceive()) {
				sleep(timeout);
				return Collections.emptyList();
			}
			requireReadyToReceive();
			clientId = state.getClientId();
		} finally {
			stateLock.readLock().unlock();
		}

		return Arrays.asList(this.internalReceive(clientId, timeout, eventsSize, receiveResponses, receiveUpdates));
	}

	private void sleep(double timeout) {
		long nanos = (long) (timeout * 1000000000d);
		int nanosPart = (int) (nanos % 1000000L);
		long millis = Duration.ofNanos(nanos - nanosPart).toMillis();
		try {
			Thread.sleep(millis, nanosPart);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Response receive(double timeout, boolean receiveResponses, boolean receiveUpdates) {
		long clientId;
		stateLock.readLock().lock();
		try {
			if (!state.isInitialized()) {
				sleep(timeout);
				return null;
			}
			requireInitialized();
			if (!state.isReadyToReceive()) {
				sleep(timeout);
				return null;
			}
			requireReadyToReceive();
			clientId = state.getClientId();
		} finally {
			stateLock.readLock().unlock();
		}

		Response[] responses = this.internalReceive(clientId, timeout, 1, receiveResponses, receiveUpdates);

		if (responses.length > 0) {
			return responses[0];
		}

		return null;
	}

	private Response[] internalReceive(long clientId, double timeout, int eventsSize, boolean receiveResponses, boolean receiveUpdates) {
		long[] eventIds = new long[eventsSize];
		TdApi.Object[] events = new TdApi.Object[eventsSize];

		int resultSize = nativeClientReceive(clientId, eventIds, events, timeout, receiveResponses, receiveUpdates);

		Response[] responses = new Response[resultSize];

		for (int i = 0; i < resultSize; i++) {
			responses[i] = new Response(eventIds[i], events[i]);
			if (eventIds[i] == 0) {
				handleStateEvent(events[i]);
			}
		}

		return responses;
	}

	private void handleStateEvent(Object event) {
		if (event == null) {
			return;
		}

		if (event.getConstructor() != UpdateAuthorizationState.CONSTRUCTOR) {
			return;
		}

		UpdateAuthorizationState updateAuthorizationState = (UpdateAuthorizationState) event;

		switch (updateAuthorizationState.authorizationState.getConstructor()) {
			case AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
				stateLock.writeLock().lock();
				try {
					state.setReadyToSend(true);
				} finally {
					stateLock.writeLock().unlock();
				}
				break;
			case AuthorizationStateClosing.CONSTRUCTOR:
				stateLock.writeLock().lock();
				try {
					state.setReadyToSend(false);
				} finally {
					stateLock.writeLock().unlock();
				}
				break;
			case AuthorizationStateClosed.CONSTRUCTOR:
				stateLock.writeLock().lock();
				try {
					state.setReadyToSend(false).setReadyToReceive(false);
				} finally {
					stateLock.writeLock().unlock();
				}
				break;
		}
	}

	@Override
	public Response execute(Request request) {
		stateLock.readLock().lock();
		try {
			requireInitialized();
			requireReadyToSend(request.getFunction().getConstructor());
		} finally {
			stateLock.readLock().unlock();
		}

		Object object = nativeClientExecute(request.getFunction());
		return new Response(0, object);
	}

	@Override
	public void destroyClient() {
		stateLock.writeLock().lock();
		try {
			if (state.isInitialized() && state.hasClientId()) {
				if (state.isReadyToSend() || state.isReadyToReceive()) {
					throw new IllegalStateException("You need to close the Client before destroying it!");
				}
				destroyNativeClient(this.state.getClientId());
				state = ClientState.of(false, 0, false, false, false);
			}
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	@Override
	public void initializeClient() {
		stateLock.writeLock().lock();
		try {
			if (!state.isInitialized() && !state.hasClientId()) {
				long clientId = createNativeClient();
				state = ClientState.of(true, clientId, true, true, false);
			}
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	private void requireInitialized() {
		if (!state.isInitialized() || !state.hasClientId()) {
			throw new IllegalStateException("Client not initialized");
		}
	}

	private void requireReadyToSend(int constructor) {
		if (!state.isReadyToSend()) {
			switch (constructor) {
				case SetOption.CONSTRUCTOR:
				case GetOption.CONSTRUCTOR:
				case TdApi.SetTdlibParameters.CONSTRUCTOR:
					return;
			}
			throw new IllegalStateException("Client not ready to send");
		}
	}

	private void requireReadyToReceive() {
		if (!state.isReadyToReceive()) {
			throw new IllegalStateException("Client not ready to receive");
		}
	}
}
