package it.tdlight.client;

public interface SimpleAuthenticationSupplier<T extends AuthenticationData> extends AuthenticationSupplier<T>,
		AuthenticationData {}
