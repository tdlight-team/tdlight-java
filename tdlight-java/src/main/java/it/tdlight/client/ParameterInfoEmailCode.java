package it.tdlight.client;

import it.tdlight.jni.TdApi.EmailAddressAuthenticationCodeInfo;
import it.tdlight.jni.TdApi.EmailAddressResetStateAvailable;
import it.tdlight.jni.TdApi.EmailAddressResetStatePending;
import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoEmailCode implements ParameterInfo {

	private final boolean allowGoogleId;
	private final boolean allowAppleId;
	private final String emailAddressPattern;
	private final int emailLength;
	private final EmailAddressResetState emailAddressResetState;

	public ParameterInfoEmailCode(boolean allowGoogleId,
			boolean allowAppleId,
			String emailAddressPattern,
			int emailLength,
			EmailAddressResetState emailAddressResetState) {
		this.allowGoogleId = allowGoogleId;
		this.allowAppleId = allowAppleId;
		this.emailAddressPattern = emailAddressPattern;
		this.emailLength = emailLength;
		this.emailAddressResetState = emailAddressResetState;
	}

	public boolean isAllowGoogleId() {
		return allowGoogleId;
	}

	public boolean isAllowAppleId() {
		return allowAppleId;
	}

	public String getEmailAddressPattern() {
		return emailAddressPattern;
	}

	public int getEmailLength() {
		return emailLength;
	}

	public EmailAddressResetState getEmailAddressResetState() {
		return emailAddressResetState;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoEmailCode that = (ParameterInfoEmailCode) o;
		return allowGoogleId == that.allowGoogleId && allowAppleId == that.allowAppleId && emailLength == that.emailLength
				&& Objects.equals(emailAddressPattern, that.emailAddressPattern)
				&& emailAddressResetState == that.emailAddressResetState;
	}

	@Override
	public int hashCode() {
		return Objects.hash(allowGoogleId, allowAppleId, emailAddressPattern, emailLength, emailAddressResetState);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoEmailCode.class.getSimpleName() + "[", "]")
				.add("allowGoogleId=" + allowGoogleId)
				.add("allowAppleId=" + allowAppleId)
				.add("emailAddressPattern='" + emailAddressPattern + "'")
				.add("emailLength=" + emailLength)
				.add("emailAddressResetState=" + emailAddressResetState)
				.toString();
	}
}
