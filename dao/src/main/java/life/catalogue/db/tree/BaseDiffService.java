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

  public Reader treeDiff(int key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    return udiff(key, atts, a -> dao.treeFile(context, key, a));
  }

  public Reader namesDiff(int key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    return udiff(key, atts, a -> dao.namesFile(context, key, a));
  }

  public NamesDiff nameIdsDiff(int key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    return namesDiff(key, atts, a -> dao.namesIdFile(context, key, a));
  }

  abstract int[] parseAttempts(int key, String attempts);

  private File[] attemptToFiles(int key, int[] attempts, Function<Integer, File> getFile) throws NamesTreeDao.AttemptMissingException{
    // verify that these exist!
    File[] files = new File[attempts.length];
    int idx = 0;
    for (int at : attempts) {
      File f = getFile.apply(at);
      assertExists(f, context, key, at);
      files[idx++]=f;
    }
    return files;
  }


  private static File assertExists(File f, NamesTreeDao.Context context, int key, int attempt) throws NamesTreeDao.AttemptMissingException {
    if (!f.exists() || f.isDirectory()) {
      throw new NamesTreeDao.AttemptMissingException(context, key, attempt);
    }
    return f;
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
  protected NamesDiff namesDiff(int key, int[] atts, Function<Integer, File> getFile) {
    File[] files = attemptToFiles(key, atts, getFile);
    try {
      final NamesDiff diff = new NamesDiff(key, atts[0], atts[1]);
      Set<String> n1 = NamesTreeDao.readLines(files[0]);
      Set<String> n2 = NamesTreeDao.readLines(files[1]);

      diff.setDeleted(new HashSet<>(n1));
      diff.getDeleted().removeAll(n2);

      diff.setInserted(new HashSet<>(n2));
      diff.getInserted().removeAll(n1);
      return diff;

    } catch (IOException e) {
      throw new RuntimeException(String.format("Failed to read files for %s %s attempts %s-%s", context, key, atts[0], atts[1]));
    }
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
  protected BufferedReader udiff(int key, int[] atts, Function<Integer, File> getFile) {
    File[] files = attemptToFiles(key, atts, getFile);
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

    } catch (IOException e) {
      throw new RuntimeException("Unix diff failed", e);

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix diff was interrupted", e);
    }
  }

}
