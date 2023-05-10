package it.tdlight.util;

import java.util.function.IntConsumer;

public final class SimpleIntQueue {
	public int size = 0;
	public int[] a = new int[16];

	public void add(int i) {
		if (size >= a.length) {
			int[] prev = a;
			a = new int[a.length << 1];
			System.arraycopy(prev, 0, a, 0, prev.length);
		}
		a[size++] = i;
	}

	public void drain(IntConsumer consumer) {
		for (int i = 0; i < size; i++) {
			consumer.accept(a[i]);
		}
		reset();
	}

	public void reset() {
		size = 0;
	}

	public boolean isContentful() {
		return size > 0;
	}
}
