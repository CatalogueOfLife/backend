package org.col.common.text;

import java.util.Comparator;

public class StringLengthComparator implements Comparator<String> {

  public int compare(String o1, String o2) {
    if (o2.length() == o1.length()) {
      return o1.compareToIgnoreCase(o2);
    }
    return o2.length() - o1.length();
  }

}
