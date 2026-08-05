package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ClientFactoryLifecycleTest {

	@Test
	public void closedFactoryRejectsAllNewClients() {
		ClientFactoryImpl factory = new ClientFactoryImpl(false);
		factory.close();

		assertThrows(IllegalStateException.class, factory::createClient);
		assertThrows(IllegalStateException.class, factory::createReactive);
		factory.close();
	}

	@Test
	public void startupFailureIsPropagatedAndLeavesFactoryStateUnavailable() {
		IllegalStateException startupFailure = new IllegalStateException("receiver startup failed");
		InternalClientsState state = new InternalClientsState(new AtomicInteger(100)::incrementAndGet) {
			@Override
			protected void onStart() {
				throw startupFailure;
			}
		};

		IllegalStateException first = assertThrows(IllegalStateException.class,
				() -> state.createAndRegisterClient(new NoOpClientEventsHandler()));
		assertSame(startupFailure, first);
		IllegalStateException second = assertThrows(IllegalStateException.class,
				() -> state.createAndRegisterClient(new NoOpClientEventsHandler()));
		assertSame(startupFailure, second.getCause());
	}

	@Test
	public void failedCommonFactoryAcquisitionDoesNotLeakAReference() {
		IllegalStateException startupFailure = new IllegalStateException("factory startup failed");
		AtomicInteger creations = new AtomicInteger();
		RecordingClientFactory delegate = new RecordingClientFactory();
		ClientFactoryImpl.CommonClientFactory common = new ClientFactoryImpl.CommonClientFactory(() -> {
			if (creations.getAndIncrement() == 0) {
				throw startupFailure;
			}
			return delegate;
		});

		assertSame(startupFailure, assertThrows(IllegalStateException.class, common::acquire));
		common.acquire();
		common.close();

		assertEquals(2, creations.get());
		assertEquals(1, delegate.closeCalls.get());
	}

	@Test
	public void commonFactoryRejectsUnbalancedClose() {
		RecordingClientFactory delegate = new RecordingClientFactory();
		ClientFactoryImpl.CommonClientFactory common = new ClientFactoryImpl.CommonClientFactory(() -> delegate);
		common.acquire();
		common.close();

		assertThrows(IllegalStateException.class, common::close);
		assertEquals(1, delegate.closeCalls.get());
	}

	private static final class RecordingClientFactory implements ClientFactory {

		private final AtomicInteger closeCalls = new AtomicInteger();

		@Override
		public TelegramClient createClient() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ReactiveTelegramClient createReactive() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() {
			closeCalls.incrementAndGet();
		}
	}

	private static final class NoOpClientEventsHandler implements ClientEventsHandler {

		@Override
		public int getClientId() {
			return 0;
		}

		@Override
		public void handleEvents(boolean isClosed,
				long[] eventIds,
				it.tdlight.jni.TdApi.Object[] events,
				int arrayOffset,
				int arrayLength) {
		}
	}
}
