package it.tdlight.client;

import it.tdlight.jni.TdApi.AuthenticationCodeType;
import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoEmailAddress implements ParameterInfo {

	private final boolean allowGoogleId;
	private final boolean allowAppleId;

	public ParameterInfoEmailAddress(boolean allowGoogleId,
			boolean allowAppleId) {
		this.allowGoogleId = allowGoogleId;
		this.allowAppleId = allowAppleId;
	}

	public boolean isAllowGoogleId() {
		return allowGoogleId;
	}

	public boolean isAllowAppleId() {
		return allowAppleId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoEmailAddress that = (ParameterInfoEmailAddress) o;
		return allowGoogleId == that.allowGoogleId && allowAppleId == that.allowAppleId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(allowGoogleId, allowAppleId);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoEmailAddress.class.getSimpleName() + "[", "]")
				.add("allowGoogleId='" + allowGoogleId + "'")
				.add("allowAppleId=" + allowAppleId)
				.toString();
	}
}
