package it.tdlight.client;

import it.tdlight.jni.TdApi.TermsOfService;
import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoTermsOfService implements ParameterInfo {
	private final TermsOfService termsOfService;

	public ParameterInfoTermsOfService(TermsOfService termsOfService) {
		this.termsOfService = termsOfService;
	}

	public TermsOfService getTermsOfService() {
		return termsOfService;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoTermsOfService that = (ParameterInfoTermsOfService) o;
		return Objects.equals(termsOfService, that.termsOfService);
	}

	@Override
	public int hashCode() {
		return Objects.hash(termsOfService);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoTermsOfService.class.getSimpleName() + "[", "]")
				.add("termsOfService=" + termsOfService)
				.toString();
	}
}
