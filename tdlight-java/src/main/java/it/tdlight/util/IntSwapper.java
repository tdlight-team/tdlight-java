package it.tdlight.util;

public final class IntSwapper {

	private final int[] array;
	int tmp;

	public IntSwapper(int[] array) {
		this.array = array;
		tmp = 0;
	}

	public void swap(int a, int b) {
		tmp = array[a];
		array[a] = array[b];
		array[b] = tmp;
	}
}
