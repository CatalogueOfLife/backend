package life.catalogue.matching.similarity;

// Copyright (c) 2011, Commonwealth of Australia
// Copied and adapted from ALA:
// https://ala-nsl.googlecode.com/svn/taxamatch/trunk/src/au/org/biodiversity/services/taxamatch/impl/ModifiedDamerauLevenshtein.java

/**
 * A Damerau-Levenshtein edit distance that also counts the transposition of whole blocks of up to {@code limit}
 * characters as cheap edits, as used by taxamatch.
 * <p>
 * Strings of any length are supported: only the rows of the matrix the recurrence still reads are kept,
 * so the memory used grows with the length of the second string and the block limit, never with the product of both
 * lengths. Instances are immutable and thread safe.
 */
public class ModifiedDamerauLevenshtein implements StringSimilarity {
  private final int pBlockLimit;

  /**
   * A default MDL with a block limit of just 2 edits.
   */
  public ModifiedDamerauLevenshtein() {
    this.pBlockLimit = 2;
  }

  /**
   * @param limit the maximum allowed distance
   */
  public ModifiedDamerauLevenshtein(int limit) {
    this.pBlockLimit = limit;
  }

  @Override
  public double getSimilarity(String x1, String x2) {
    return DistanceUtils.convertEditDistanceToSimilarity(getEditDistance(x1, x2), x1, x2);
  }

  public final int getEditDistance(final String s1, final String s2) {
    if (s1.equals(s2)) {
      return 0;
    } else if (s1.isEmpty() || s2.isEmpty()) {
      return Math.max(s1.length(), s2.length());
    } else if (s1.length() == 1 && s2.length() == 1) {
      return 1;
    }

    // strip the common prefix and suffix, they cost nothing
    int start = 0;
    int end1 = s1.length();
    int end2 = s2.length();
    while (start < end1 && start < end2 && s1.charAt(start) == s2.charAt(start)) {
      start++;
    }
    while (end1 > start && end2 > start && s1.charAt(end1 - 1) == s2.charAt(end2 - 1)) {
      end1--;
      end2--;
    }
    final int len1 = end1 - start;
    final int len2 = end2 - start;
    if (len1 == 0 || len2 == 0) {
      return Math.max(len1, len2);
    } else if (len1 == 1 && len2 == 1) {
      return 1;
    }
    final char[] t1 = s1.substring(start, end1).toCharArray();
    final char[] t2 = s2.substring(start, end2).toCharArray();

    // the longest block that can be transposed, it does not change within a call
    final int maxBlock = Math.max(Math.min(len1 / 2, Math.min(len2 / 2, Math.max(pBlockLimit, 1))), 1);
    // a block transposition reads the row 2 * maxBlock above the current one, so that many rows plus the current one
    // are all we keep: row i lives at (i % rows) * width
    final int rows = 2 * maxBlock + 1;
    final int width = len2 + 1;
    final int[] matrix = new int[rows * width];

    for (int j = 0; j <= len2; j++) {
      matrix[j] = j;
    }

    for (int i = 1; i <= len1; i++) {
      final int row = (i % rows) * width;
      final int prev = ((i - 1) % rows) * width;
      matrix[row] = i;
      for (int j = 1; j <= len2; j++) {
        final int cost = t1[i - 1] == t2[j - 1] ? 0 : 1;

        int blockLength = maxBlock;
        while (blockLength >= 1) {
          final int sub1 = i - ((blockLength * 2) - 1);
          final int sub2 = j - (blockLength - 1);
          final int sub3 = i - (blockLength - 1);
          final int sub4 = j - ((blockLength * 2) - 1);

          if (i >= (blockLength * 2) && j >= (blockLength * 2)
              && substreq(t1, sub1, t2, sub2, blockLength)
              && substreq(t1, sub3, t2, sub4, blockLength)) {

            final int ins = matrix[row + j - 1] + 1;
            final int del = matrix[prev + j] + 1;
            final int tran = matrix[((i - blockLength * 2) % rows) * width + j - blockLength * 2]
                + cost + (blockLength - 1);
            matrix[row + j] = minimum(ins, del, tran);

            blockLength = 0;

          } else if (blockLength == 1) {

            final int del = matrix[prev + j] + 1;
            final int ins = matrix[row + j - 1] + 1;
            final int sub = matrix[prev + j - 1] + cost;

            matrix[row + j] = minimum(ins, del, sub);
          }
          blockLength--;
        }
      }
    }

    return matrix[(len1 % rows) * width + len2];
  }

	// The compiler inlines these, I think.

	private final static int minimum(int d, int i, int s) {
		return d < i ? (d < s ? d : s) : (i < s ? i : s);
	}

	private final static boolean substreq(char[] src1, int start1, char[] src2, int start2, int length) {
		int at1 = start1 >= 0 ? start1 - 1 : src1.length + start1;
		int at2 = start2 >= 0 ? start2 - 1 : src2.length + start2;
		while (length-- > 0) {
			if (src1[at1++] != src2[at2++]) return false;
		}
		return true;
	}
}
