package it.tdlight.client;

import java.util.Objects;
import java.util.StringJoiner;

public final class APIToken {

	private int apiID;
	private String apiHash;

	/**
	 * Obtain your API token here: <a href="https://my.telegram.org/auth?to=apps">https://my.telegram.org/auth?to=apps</a>
	 */
	public APIToken(int apiID, String apiHash) {
		this.apiID = apiID;
		this.apiHash = apiHash;
	}

	/**
	 * Example token
	 */
	public static APIToken example() {
		int apiID = 94575;
		String apiHash = "a3406de8d171bb422bb6ddf3bbd800e2";
		return new APIToken(apiID, apiHash);
	}

	public int getApiID() {
		return apiID;
	}

	public void setApiID(int apiID) {
		this.apiID = apiID;
	}

	public String getApiHash() {
		return apiHash;
	}

	public void setApiHash(String apiHash) {
		this.apiHash = apiHash;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		APIToken apiData = (APIToken) o;
		return apiID == apiData.apiID && Objects.equals(apiHash, apiData.apiHash);
	}

	@Override
	public int hashCode() {
		return Objects.hash(apiID, apiHash);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", APIToken.class.getSimpleName() + "[", "]")
				.add("apiID=" + apiID)
				.add("apiHash='" + apiHash + "'")
				.toString();
	}
}
