package life.catalogue.db.tree;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.dao.NamesTreeDao;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseDiffService {
  private static final Logger LOG = LoggerFactory.getLogger(BaseDiffService.class);

  private final static Pattern ATTEMPTS = Pattern.compile("^(\\d+)\\.\\.(\\d+)$");
  protected final SqlSessionFactory factory;
  protected final NamesTreeDao dao;
  private final NamesTreeDao.Context context;

  public BaseDiffService(NamesTreeDao.Context context, NamesTreeDao dao, SqlSessionFactory factory) {
    this.context = context;
    this.factory = factory;
    this.dao = dao;
  }

  public Reader treeDiff(int datasetKey, String attempts) throws IOException {
    int[] atts = parseAttempts(datasetKey, attempts);
    return udiff(atts, a -> dao.treeFile(context, datasetKey, a));
  }

  public Reader namesDiff(int datasetKey, String attempts) throws IOException {
    int[] atts = parseAttempts(datasetKey, attempts);
    return udiff(atts, a -> dao.namesFile(context, datasetKey, a));
  }

  public NamesDiff nameIdsDiff(int datasetKey, String attempts) throws IOException {
    int[] atts = parseAttempts(datasetKey, attempts);
    return namesDiff(datasetKey, atts, a -> dao.namesIdFile(context, datasetKey, a));
  }

  abstract int[] parseAttempts(int key, String attempts);

  private File[] attemptToFiles(int[] attempts, Function<Integer, File> getFile){
    // verify the these exist!
    File[] files = new File[attempts.length];
    int idx = 0;
    for (int at : attempts) {
      File f = getFile.apply(at);
      if (!f.exists()) {
        throw NotFoundException.notFound("Import attempt", at);
      }
      files[idx++]=f;
    }
    return files;
  }

  @VisibleForTesting
  protected int[] parseAttempts(String attempts, Supplier<List<? extends ImportAttempt>> importSupplier) {
    int a1;
    int a2;
    try {
      if (StringUtils.isBlank(attempts)) {
        List<? extends ImportAttempt> imports = importSupplier.get();
        if (imports.size()<2) {
          throw new NotFoundException("At least 2 successful imports must exist to provide a diff");
        }
        a1=imports.get(1).getAttempt();
        a2=imports.get(0).getAttempt();

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

    } catch (IllegalArgumentException | NotFoundException e) {
      throw e;
    }
  }

  @VisibleForTesting
  protected NamesDiff namesDiff(int key, int[] atts, Function<Integer, File> getFile) throws IOException {
    File[] files = attemptToFiles(atts, getFile);
    final NamesDiff diff = new NamesDiff(key, atts[0], atts[1]);
    Set<String> n1 = NamesTreeDao.readLines(files[0]);
    Set<String> n2 = NamesTreeDao.readLines(files[1]);

    diff.setDeleted(new HashSet<>(n1));
    diff.getDeleted().removeAll(n2);

    diff.setInserted(new HashSet<>(n2));
    diff.getInserted().removeAll(n1);
    return diff;
  }

  public String diffBinaryVersion() throws IOException {
    Runtime rt = Runtime.getRuntime();
    Process ps = rt.exec("diff -v");
    return InputStreamUtils.readEntireStream(ps.getInputStream());
  }

  /**
   * Generates a unified diff from two gzipped files using a native unix process.
   * @param atts
   * @param getFile
   */
  @VisibleForTesting
  protected BufferedReader udiff(int[] atts, Function<Integer, File> getFile) throws IOException {
    File[] files = attemptToFiles(atts, getFile);
    try {
      String cmd = String.format("diff -B -d -U 2 <(gunzip -c %s) <(gunzip -c %s)", files[0].getAbsolutePath(), files[1].getAbsolutePath());
      LOG.debug("Execute: {}", cmd);
      ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd + "; exit 0");
      Process ps = pb.start();
      int status = ps.waitFor();
      if (status != 0) {
        String error = InputStreamUtils.readEntireStream(ps.getErrorStream());
        throw new RuntimeException("Unix diff failed with status " + status + ": " + error);
      }
      return new BufferedReader(new InputStreamReader(ps.getInputStream()));

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix diff was interrupted", e);
    }
  }

}
