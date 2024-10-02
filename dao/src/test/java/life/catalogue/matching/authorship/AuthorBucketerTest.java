package life.catalogue.matching.authorship;

import life.catalogue.common.collection.ColumnExtractor;
import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.Authorship;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.LineIterator;

import org.gbif.nameparser.api.NomCode;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static org.junit.Assert.assertTrue;

/**
 * Utility that reads a stream of author names and splits them into sets of names that are classified as the same name by the author comparator.
 */
public class AuthorBucketerTest {

  public static Map<String, Set<String>> clusterNames(Iterator<String> authors) {
    Map<String, Set<String>> buckets = Maps.newHashMap();
    AuthorComparator comp = new AuthorComparator(AuthorshipNormalizer.INSTANCE);

    while (authors.hasNext()) {
      String author = authors.next();
      Authorship authorship = buildAuthorship(author);
      String match = null;
      for (String x : buckets.keySet()) {
        if (comp.compareStrict(authorship, buildAuthorship(x), NomCode.BOTANICAL)) {
          match = x;
          break;
        }
      }
      if (match == null) {
        // new bucket
        buckets.put(author, Sets.newHashSet(author));
      } else {
        buckets.get(match).add(author);
      }
    }
    return buckets;
  }

  private static Authorship buildAuthorship(String author) {
    Authorship a = new Authorship();
    a.getAuthors().add(author);
    return a;
  }

  @Test
  public void testAuthormap() throws Exception {
    LineIterator iter = new LineIterator(Resources.reader("authorship/authormap.txt"));
    int lines = 0;
    while (iter.hasNext()) {
      lines++;
      iter.next();
    }
    iter = new LineIterator(Resources.reader("authorship/authormap.txt"));
    Map<String, Set<String>> buckets = clusterNames(new ColumnExtractor(iter, '\t', 0));

    Joiner join = Joiner.on("; ").skipNulls();
    for (Map.Entry<String, Set<String>> entry : buckets.entrySet()) {
      if (entry.getValue().size() > 1) {
        System.out.println(entry.getKey());
        System.out.println("  " + join.join(entry.getValue()));
      }
    }
    System.out.println("Lines: " + lines + ", buckets: " + buckets.size());
    assertTrue(buckets.size() > 4300);
  }

}
