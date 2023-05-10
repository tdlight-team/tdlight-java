package it.tdlight;

import it.tdlight.util.IntSwapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class ArrayUtil {

	public static int[] copyFromCollection(Collection<Integer> list) {
		int[] result = new int[list.size()];
		int i = 0;
		for (Integer item : list) {
			result[i++] = item;
		}
		return result;
	}

	public static Set<Integer> toSet(int[] list) {
		Set<Integer> set = new HashSet<>(list.length);
		for (int item : list) {
			set.add(item);
		}
		return set;
	}

	public interface IntComparator {
		int compare(int k1, int k2);
	}

	public static void mergeSort(int from, int to, IntComparator c, IntSwapper swapper) {
		int length = to - from;
		int i;
		if (length >= 16) {
			i = from + to >>> 1;
			mergeSort(from, i, c, swapper);
			mergeSort(i, to, c, swapper);
			if (c.compare(i - 1, i) > 0) {
				inPlaceMerge(from, i, to, c, swapper);
			}
		} else {
			for(i = from; i < to; ++i) {
				for(int j = i; j > from && c.compare(j - 1, j) > 0; --j) {
					swapper.swap(j, j - 1);
				}
			}

		}
	}

	private static void inPlaceMerge(int from, int mid, int to, IntComparator comp, IntSwapper swapper) {
		if (from < mid && mid < to) {
			if (to - from == 2) {
				if (comp.compare(mid, from) < 0) {
					swapper.swap(from, mid);
				}

			} else {
				int firstCut;
				int secondCut;
				if (mid - from > to - mid) {
					firstCut = from + (mid - from) / 2;
					secondCut = lowerBound(mid, to, firstCut, comp);
				} else {
					secondCut = mid + (to - mid) / 2;
					firstCut = upperBound(from, mid, secondCut, comp);
				}

				if (mid != firstCut && mid != secondCut) {
					int first1 = firstCut;
					int last1 = mid;

					label43:
					while(true) {
						--last1;
						if (first1 >= last1) {
							first1 = mid;
							last1 = secondCut;

							while(true) {
								--last1;
								if (first1 >= last1) {
									first1 = firstCut;
									last1 = secondCut;

									while(true) {
										--last1;
										if (first1 >= last1) {
											break label43;
										}

										swapper.swap(first1++, last1);
									}
								}

								swapper.swap(first1++, last1);
							}
						}

						swapper.swap(first1++, last1);
					}
				}

				mid = firstCut + (secondCut - mid);
				inPlaceMerge(from, firstCut, mid, comp, swapper);
				inPlaceMerge(mid, secondCut, to, comp, swapper);
			}
		}
	}

	private static int lowerBound(int from, int to, int pos, IntComparator comp) {
		int len = to - from;

		while(len > 0) {
			int half = len / 2;
			int middle = from + half;
			if (comp.compare(middle, pos) < 0) {
				from = middle + 1;
				len -= half + 1;
			} else {
				len = half;
			}
		}

		return from;
	}

	private static int upperBound(int from, int mid, int pos, IntComparator comp) {
		int len = mid - from;

		while(len > 0) {
			int half = len / 2;
			int middle = from + half;
			if (comp.compare(pos, middle) < 0) {
				len = half;
			} else {
				from = middle + 1;
				len -= half + 1;
			}
		}

		return from;
	}
}
