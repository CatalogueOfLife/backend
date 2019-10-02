package org.col.db.tree;

import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.exception.NotFoundException;
import org.col.api.model.ImportAttempt;
import org.col.api.model.Page;
import org.col.api.model.SectorImport;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.ImportState;
import org.col.common.io.InputStreamUtils;
import org.col.dao.NamesTreeDao;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.SectorImportMapper;

public class DiffService {
  private final SqlSessionFactory factory;
  private final NamesTreeDao dao;
  
  private final static Splitter ATTEMPTS = Splitter.on("..").trimResults();
  private final static Splitter LINE_SPLITTER = Splitter.on('\n');
  
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
              .list(sectorKey, Datasets.DRAFT_COL, null, Lists.newArrayList(SectorImport.State.FINISHED), new Page(0, 2));
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
        throw new NotFoundException("Import attempt " +at+ " not existing");
      }
      files[idx++]=f;
    }
    return files;
  }
  
  private int[] parseAttempts(String attempts, Supplier<List<? extends ImportAttempt>> importSupplier) {
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
        Iterator<String> attIter = ATTEMPTS.split(attempts).iterator();
        a1 = Integer.parseInt(attIter.next());
        a2 = Integer.parseInt(attIter.next());
      }
      return new int[]{a1, a2};
      
    } catch (IllegalArgumentException | NotFoundException e) {
      throw e;
    }
  }
  

  private NamesDiff namesDiff(int key, int[] atts, Function<Integer, File> getFile) {
    File[] files = attemptToFiles(atts, getFile);
    final NamesDiff diff = new NamesDiff(key, atts[0], atts[1]);
    //TODO: read names...
    Set<String> n1 = null;
    Set<String> n2 = null;
    
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
