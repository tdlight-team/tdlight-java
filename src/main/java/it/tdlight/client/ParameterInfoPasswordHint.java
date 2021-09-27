package it.tdlight.client;

import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoPasswordHint implements ParameterInfo {

	private final String hint;
	private final boolean hasRecoveryEmailAddress;
	private final String recoveryEmailAddressPattern;

	public ParameterInfoPasswordHint(String hint, boolean hasRecoveryEmailAddress, String recoveryEmailAddressPattern) {
		this.hint = hint;
		this.hasRecoveryEmailAddress = hasRecoveryEmailAddress;
		this.recoveryEmailAddressPattern = recoveryEmailAddressPattern;
	}

	public String getHint() {
		return hint;
	}

	public String getRecoveryEmailAddressPattern() {
		return recoveryEmailAddressPattern;
	}

	public boolean hasRecoveryEmailAddress() {
		return hasRecoveryEmailAddress;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoPasswordHint that = (ParameterInfoPasswordHint) o;
		return hasRecoveryEmailAddress == that.hasRecoveryEmailAddress && Objects.equals(hint, that.hint) && Objects.equals(
				recoveryEmailAddressPattern,
				that.recoveryEmailAddressPattern
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(hint, hasRecoveryEmailAddress, recoveryEmailAddressPattern);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoPasswordHint.class.getSimpleName() + "[", "]")
				.add("hint='" + hint + "'")
				.add("hasRecoveryEmailAddress=" + hasRecoveryEmailAddress)
				.add("recoveryEmailAddressPattern='" + recoveryEmailAddressPattern + "'")
				.toString();
	}
}
