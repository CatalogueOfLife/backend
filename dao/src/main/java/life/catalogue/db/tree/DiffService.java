package life.catalogue.db.tree;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.dao.NamesTreeDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffService {
  private final SqlSessionFactory factory;
  private final NamesTreeDao dao;
  
  private final static Pattern ATTEMPTS = Pattern.compile("^(\\d+)\\.\\.(\\d+)$");
  static enum DiffType {TREE, NAMES};
  
  public DiffService(SqlSessionFactory factory, NamesTreeDao dao) {
    this.factory = factory;
    this.dao = dao;
  }
  
  public Reader datasetTreeDiff(int datasetKey, String attempts) throws IOException {
    int[] atts = parseDatasetAttempts(datasetKey, attempts, DiffType.TREE);
    return udiff(atts, a -> dao.treeFile(datasetKey, a));
  }
  
  public Reader sectorTreeDiff(int sectorKey, String attempts) throws IOException {
    int[] atts = parseSectorAttempts(sectorKey, attempts, DiffType.TREE);
    return udiff(atts, a -> dao.sectorTreeFile(sectorKey, a));
  }
  
  public NamesDiff datasetNamesDiff(int datasetKey, String attempts) throws IOException {
    int[] atts = parseDatasetAttempts(datasetKey, attempts, DiffType.NAMES);
    return namesDiff(datasetKey, atts, a -> dao.namesFile(datasetKey, a));
  }
  
  public NamesDiff sectorNamesDiff(int sectorKey, String attempts) throws IOException {
    int[] atts = parseSectorAttempts(sectorKey, attempts, DiffType.NAMES);
    return namesDiff(sectorKey, atts, a -> dao.sectorNamesFile(sectorKey, a));
  }
  
  private int[] parseDatasetAttempts(int datasetKey, String attempts, DiffType type) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(DatasetImportMapper.class)
              .list(datasetKey, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
        }
      }
    });
  }
  
  private int[] parseSectorAttempts(int sectorKey, String attempts, DiffType type) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(SectorImportMapper.class)
              .list(sectorKey, null, null, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
        }
      }
    });
  }
  
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
    Set<String> n1 = NamesTreeDao.readNames(files[0]);
    Set<String> n2 = NamesTreeDao.readNames(files[1]);
    
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
  
  @VisibleForTesting
  protected BufferedReader udiff(int[] atts, Function<Integer, File> getFile) throws IOException {
    File[] files = attemptToFiles(atts, getFile);
    Runtime rt = Runtime.getRuntime();
    Process ps = rt.exec(String.format("diff -B -d -U 2 %s %s", files[0].getAbsolutePath(), files[1].getAbsolutePath()));
  
    return new BufferedReader(new InputStreamReader(ps.getInputStream()));
  }
}
