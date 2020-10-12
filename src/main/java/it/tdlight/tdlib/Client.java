package it.tdlight.tdlib;

import it.tdlight.common.CommonClient;

/**
 * Interface for interaction with TDLib.
 */
public class Client extends CommonClient {

	@Override
	protected String getImplementationName() {
		return "tdlib";
	}
}
