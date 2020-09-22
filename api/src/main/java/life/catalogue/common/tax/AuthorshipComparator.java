package life.catalogue.common.tax;

import java.util.Comparator;

public class AuthorshipComparator implements Comparator<String>  {

  @Override
  public int compare(String o1, String o2) {
    o1 = AuthorshipNormalizer.normalize(o1);
    o2 = AuthorshipNormalizer.normalize(o2);

    if (o1 == o2) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    }
    if (o2 == null) {
      return 1;
    }
    return o1.compareTo(o2);
  }
}
