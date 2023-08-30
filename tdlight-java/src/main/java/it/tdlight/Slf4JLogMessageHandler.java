package it.tdlight;

import it.tdlight.tdnative.NativeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4JLogMessageHandler implements NativeClient.LogMessageHandler {

	public static final Logger LOG = LoggerFactory.getLogger("it.tdlight.TDLight");

	@Override
	public void onLogMessage(int verbosityLevel, String message) {
		switch (verbosityLevel) {
			case -1:
			case 0:
			case 1:
				LOG.error(message);
				break;
			case 2:
				LOG.warn(message);
				break;
			case 3:
				LOG.info(message);
				break;
			case 4:
				LOG.debug(message);
				break;
			default:
				LOG.trace(message);
				break;
		}
	}
}
