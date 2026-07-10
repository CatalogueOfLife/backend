package life.catalogue.printer;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.printer.diff.DiffInput;
import life.catalogue.printer.diff.DiffOptions;
import life.catalogue.printer.diff.NamesDiffEngine;
import life.catalogue.printer.diff.StreamingMergeDiffEngine;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public abstract class BaseDiffService<K> {
  private static final Logger LOG = LoggerFactory.getLogger(BaseDiffService.class);

  private static final Pattern ATTEMPTS = Pattern.compile("^(\\d+)\\.{2,3}(\\d+)$");
  protected final SqlSessionFactory factory;
  protected final FileMetricsDao<K> dao;
  protected final NamesDiffEngine engine = new StreamingMergeDiffEngine();
  protected final int maxItems;

  public BaseDiffService(FileMetricsDao<K> dao, SqlSessionFactory factory, int maxItems) {
    this.factory = factory;
    this.dao = dao;
    this.maxItems = maxItems;
  }

  /** Override to tune diff behaviour (thresholds, limits). */
  protected DiffOptions diffOptions() {
    return DiffOptions.defaults().setMaxItems(maxItems);
  }

  /**
   * Diff the stored names of two import attempts of the same key. The names files are already
   * byte-ordered (Postgres LC_COLLATE 'C'), so they stream straight into the merge engine.
   */
  public NamesDiff diff(K key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    DiffInput a = new DiffInput(label(key, atts[0]), () -> dao.getNames(key, atts[0]));
    DiffInput b = new DiffInput(label(key, atts[1]), () -> dao.getNames(key, atts[1]));
    LOG.info("Names diff for {} {} attempts {}..{}", dao.getType(), key, atts[0], atts[1]);
    return engine.diff(a, b, diffOptions());
  }

  abstract int[] parseAttempts(K key, String attempts);

  @VisibleForTesting
  protected int[] parseAttempts(String attempts, Supplier<List<? extends ImportAttempt>> importSupplier) {
    int a1;
    int a2;
    if (StringUtils.isBlank(attempts)) {
      List<? extends ImportAttempt> imports = importSupplier.get();
      if (imports.size() < 2) {
        throw new NotFoundException("At least 2 successful imports must exist to provide a diff");
      }
      a1 = imports.get(1).getAttempt();
      a2 = imports.get(0).getAttempt();
    } else {
      Matcher m = ATTEMPTS.matcher(attempts);
      if (m.find()) {
        a1 = Integer.parseInt(m.group(1));
        a2 = Integer.parseInt(m.group(2));
        if (a1 >= a2) {
          throw new IllegalArgumentException("first attempt must be lower than second");
        }
      } else {
        throw new IllegalArgumentException("attempts must be separated by a two dots ..");
      }
    }
    return new int[]{a1, a2};
  }

  protected String label(K key) {
    return label(key, null);
  }

  String label(K key, @Nullable Integer attempt) {
    return "dataset_" + key + (attempt == null ? "" : "#" + attempt);
  }
}
