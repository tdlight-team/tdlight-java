package it.tdlight.tdlight;

import it.tdlight.common.CommonClient;

/**
 * Interface for interaction with TDLight.
 */
public class Client extends CommonClient {

	@Override
	protected String getImplementationName() {
		return "tdlight";
	}
}
