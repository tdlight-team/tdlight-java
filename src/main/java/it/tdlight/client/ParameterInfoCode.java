package it.tdlight.client;

import it.tdlight.jni.TdApi.AuthenticationCodeType;
import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoCode implements ParameterInfo {

	private final String phoneNumber;
	private final AuthenticationCodeType nextType;
	private final int timeout;
	private final AuthenticationCodeType type;

	public ParameterInfoCode(String phoneNumber,
			AuthenticationCodeType nextType,
			int timeout,
			AuthenticationCodeType type) {
		this.phoneNumber = phoneNumber;
		this.nextType = nextType;
		this.timeout = timeout;
		this.type = type;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public AuthenticationCodeType getNextType() {
		return nextType;
	}

	public int getTimeout() {
		return timeout;
	}

	public AuthenticationCodeType getType() {
		return type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoCode that = (ParameterInfoCode) o;
		return timeout == that.timeout && Objects.equals(phoneNumber, that.phoneNumber) && Objects.equals(nextType,
				that.nextType
		) && Objects.equals(type, that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(phoneNumber, nextType, timeout, type);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoCode.class.getSimpleName() + "[", "]")
				.add("phoneNumber='" + phoneNumber + "'")
				.add("nextType=" + nextType)
				.add("timeout=" + timeout)
				.add("type=" + type)
				.toString();
	}
}
