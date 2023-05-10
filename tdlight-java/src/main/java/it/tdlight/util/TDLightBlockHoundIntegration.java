package it.tdlight.util;


import reactor.blockhound.BlockHound.Builder;
import reactor.blockhound.integration.BlockHoundIntegration;

public class TDLightBlockHoundIntegration implements BlockHoundIntegration {

	@Override
	public void applyTo(Builder builder) {
		builder.nonBlockingThreadPredicate(current -> current.or(t -> {
			if (t.getName() == null) {
				return false;
			}
			return t.getName().equals("TDLib thread");
		}));
	}
}
