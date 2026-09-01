package life.catalogue.matching.authorship;

import life.catalogue.common.collection.ColumnExtractor;
import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.LineIterator;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static org.junit.Assert.assertTrue;

/**
 * Utility that reads a stream of author names and splits them into sets of names that are classified as the same name by the author comparator.
 *
 * Clustering is O(authors x buckets) and the shipped author map holds 60k names, so the full run takes
 * minutes and is kept as a manual test only - it once hung a Jenkins fork long enough for the JVM to be
 * killed. The regression test clusters a sample instead.
 */
public class AuthorBucketerTest {
  /**
   * The sample is taken as {@link #SAMPLE_CHUNKS} evenly spread blocks of {@link #SAMPLE_CHUNK_SIZE}
   * consecutive names. Both halves matter: authormap.txt is sorted alphabetically, so the spelling
   * variants of one author sit next to each other and only a *contiguous* block keeps anything to
   * cluster at all, while spreading the blocks stops the sample from being just the As.
   */
  private static final int SAMPLE_CHUNKS = 12;
  private static final int SAMPLE_CHUNK_SIZE = 100;

  public static Map<String, Set<String>> clusterNames(Iterator<String> authors) {
    Map<String, Set<String>> buckets = Maps.newHashMap();
    AuthorComparator comp = new AuthorComparator(AuthorshipNormalizer.INSTANCE);

    while (authors.hasNext()) {
      String author = authors.next();
      Authorship authorship = buildAuthorship(author);
      String match = null;
      for (String x : buckets.keySet()) {
        if (comp.compareStrict(authorship, buildAuthorship(x), NomCode.BOTANICAL, 1)) {
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

  /**
   * All author names of the shipped author map, in file order.
   */
  private static Iterator<String> authormap() {
    return new ColumnExtractor(new LineIterator(Resources.reader("authorship/authormap.txt")), '\t', 0);
  }

  private static Iterator<String> sample(Iterator<String> authors, int chunks, int chunkSize) {
    List<String> all = new ArrayList<>();
    while (authors.hasNext()) {
      all.add(authors.next());
    }
    List<String> sample = new ArrayList<>(chunks * chunkSize);
    int stride = all.size() / chunks;
    for (int c = 0; c < chunks; c++) {
      int from = c * stride;
      sample.addAll(all.subList(from, Math.min(from + chunkSize, all.size())));
    }
    return sample.iterator();
  }

  private static void printMultiMemberBuckets(Map<String, Set<String>> buckets) {
    Joiner join = Joiner.on("; ").skipNulls();
    for (Map.Entry<String, Set<String>> entry : buckets.entrySet()) {
      if (entry.getValue().size() > 1) {
        System.out.println(entry.getKey());
        System.out.println("  " + join.join(entry.getValue()));
      }
    }
  }

  @Test
  public void testAuthormapSample() {
    Map<String, Set<String>> buckets = clusterNames(sample(authormap(), SAMPLE_CHUNKS, SAMPLE_CHUNK_SIZE));
    printMultiMemberBuckets(buckets);
    System.out.println("Buckets: " + buckets.size());
    assertTrue("too few buckets, the comparator merges unrelated authors: " + buckets.size(), buckets.size() > 900);
  }

  /**
   * Clusters the entire author map. Minutes of CPU - run by hand when changing {@link AuthorComparator}.
   */
  @Test
  @Ignore("manual only: O(authors x buckets) over all 60k names, takes minutes")
  public void testFullAuthormap() {
    Map<String, Set<String>> buckets = clusterNames(authormap());
    printMultiMemberBuckets(buckets);
    System.out.println("Buckets: " + buckets.size());
    assertTrue(buckets.size() > 4300);
  }
}
