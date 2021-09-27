package it.tdlight.client;

import java.util.Objects;
import java.util.StringJoiner;

public final class ParameterInfoNotifyLink implements ParameterInfo {

	private final String link;

	public ParameterInfoNotifyLink(String link) {
		this.link = link;
	}

	public String getLink() {
		return link;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParameterInfoNotifyLink that = (ParameterInfoNotifyLink) o;
		return Objects.equals(link, that.link);
	}

	@Override
	public int hashCode() {
		return Objects.hash(link);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ParameterInfoNotifyLink.class.getSimpleName() + "[", "]")
				.add("link='" + link + "'")
				.toString();
	}
}
