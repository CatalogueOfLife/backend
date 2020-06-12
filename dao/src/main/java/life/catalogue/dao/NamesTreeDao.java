package life.catalogue.dao;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.TextTreePrinter;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class NamesTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(NamesTreeDao.class);
  
  private final SqlSessionFactory factory;
  private final File repo;
  
  public NamesTreeDao(SqlSessionFactory factory, File repo) {
    this.factory = factory;
    this.repo = repo;
  }
  
  public static Set<String> readLines(File f) throws IOException{
    try (BufferedReader br = UTF8IoUtils.readerFromFile(f)) {
      return br.lines().collect(Collectors.toSet());
    }
  }
  
  public void updateDatasetNames(int datasetKey, int attempt) {
    try (SqlSession session = factory.openSession(true);
        NamesWriter nHandler = new NamesWriter(namesFile(Context.DATASET, datasetKey, attempt));
        NamesIdWriter idHandler = new NamesIdWriter(namesIdFile(Context.DATASET, datasetKey, attempt))
    ){
      NameMapper nm = session.getMapper(NameMapper.class);

      nm.processNameStrings(datasetKey, null).forEach(nHandler);
      LOG.info("Written {} name strings for dataset {}-{}", nHandler.counter, datasetKey, attempt);

      nm.processIndexIds(datasetKey, null).forEach(idHandler);
      LOG.info("Written {} names index ids for dataset {}-{}", idHandler.counter, datasetKey, attempt);
    }
  }
  
  public void updateSectorNames(int sectorKey, int attempt) {
    try (SqlSession session = factory.openSession(true);
         NamesWriter nHandler = new NamesWriter(namesFile(Context.SECTOR, sectorKey, attempt));
         NamesIdWriter idHandler = new NamesIdWriter(namesIdFile(Context.SECTOR, sectorKey, attempt))
    ){
      SectorMapper sm = session.getMapper(SectorMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);
      Sector s = sm.get(DSID.idOnly(sectorKey));

      nm.processNameStrings(s.getDatasetKey(), sectorKey).forEach(nHandler);
      LOG.info("Written {} name strings for sector {}-{}", nHandler.counter, sectorKey, attempt);

      nm.processIndexIds(s.getDatasetKey(), sectorKey).forEach(idHandler);
      LOG.info("Written {} names index ids for sector {}-{}", idHandler.counter, sectorKey, attempt);
    }
  }

  static class NamesWriter implements Consumer<String>, AutoCloseable {
    public int counter = 0;
    private final File f;
    private final BufferedWriter w;
    
    NamesWriter(File f) {
      this.f=f;
      try {
        w = UTF8IoUtils.writerFromGzipFile(f);
        
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  
    @Override
    public void close() {
      try {
        w.close();
      } catch (IOException e) {
        LOG.error("Failed to close {}", f.getAbsolutePath());
      }
    }
  
    @Override
    public void accept(String id) {
      try {
        if (id != null) {
          counter++;
          w.append(id);
          w.append('\n');
        }
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  }

  static class NamesIdWriter implements Consumer<String>, AutoCloseable {
    public int counter = 0;
    private final File f;
    private final BufferedWriter w;

    NamesIdWriter(File f) {
      this.f=f;
      try {
        w = UTF8IoUtils.writerFromGzipFile(f);

      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      try {
        w.close();
      } catch (IOException e) {
        LOG.error("Failed to close {}", f.getAbsolutePath());
      }
    }

    @Override
    public void accept(String id) {
      try {
        if (id != null) {
          counter++;
          w.append(id);
          w.append('\n');
        }
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Deletes all metrics stored for the given dataset or sector, incl tree and name index sets.
   */
  public void deleteAll(Context context, int key) throws IOException {
    File dir = subdir(context, key);
    FileUtils.deleteDirectory(dir);
    LOG.info("Deleted all file metrics for {} {}", context.name().toLowerCase(), key);
  }
  
  public int updateDatasetTree(int datasetKey, int attempt) throws IOException {
    try (Writer writer = UTF8IoUtils.writerFromGzipFile(treeFile(Context.DATASET, datasetKey, attempt))) {
      int count = TextTreePrinter.dataset(datasetKey, factory, writer).print();
      LOG.info("Written text tree with {} lines for dataset {}-{}", count, datasetKey, attempt);
      return count;
    }
  }
  
  public int updateSectorTree(int sectorKey, int attempt) throws IOException {
    Sector s;
    try (SqlSession session = factory.openSession(true)){
      s = session.getMapper(SectorMapper.class).get(DSID.idOnly(sectorKey));
    }
    try (Writer writer = UTF8IoUtils.writerFromGzipFile(treeFile(Context.SECTOR, sectorKey, attempt))){
      int count = TextTreePrinter.sector(s.getDatasetKey(), sectorKey, factory, writer).print();
      LOG.info("Written text tree with {} lines for sector {}-{}", count, sectorKey, attempt);
      return count;
    }
  }

  /**
   * @param context whether its a dataset or sector
   * @param key the dataset or sector key
   */
  public Stream<String> getNames(Context context, int key, int attempt) {
    return streamFile(namesFile(context, key, attempt));
  }

  public Stream<String> getNameIds(Context context, int key, int attempt) {
    return streamFile(namesIdFile(context, key, attempt));
  }

  public Stream<String> getTree(Context context, int key, int attempt) {
    return streamFile(treeFile(context, key, attempt));
  }

  private static Stream<String> streamFile(File f) {
    try {
      BufferedReader br = UTF8IoUtils.readerFromGzipFile(f);
      return br.lines();
    } catch (IOException e) {
      LOG.warn("Failed to stream file {}", f.getAbsolutePath());
      return Stream.empty();
    }
  }

  public enum Context {DATASET, SECTOR}

  public File treeFile(Context context, int key, int attempt) {
    return new File(subdir(context, key), "tree/"+attempt+".txt.gz");
  }
  
  public File namesFile(Context context, int key, int attempt) {
    return new File(subdir(context, key), "names/"+attempt+"-strings.txt.gz");
  }

  public File namesIdFile(Context context, int key, int attempt) {
    return new File(subdir(context, key), "names/"+attempt+".txt.gz");
  }

  private File subdir(Context context, int key) {
    return new File(repo, context.name().toLowerCase() + "/" + bucket(key) + "/" + key);
  }

  /**
   * Assigns a given evenly distributed integer to a bucket of max 1000 items so we do not overload the filesystem
   * @param x
   * @return 000-999
   */
  @VisibleForTesting
  protected static String bucket(int x) {
    return String.format("%03d", Math.abs(x) % 1000);
  }
}
