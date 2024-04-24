package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.printer.TextTreePrinter;

import java.io.*;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public abstract class FileMetricsDao<K> {
  private static final Logger LOG = LoggerFactory.getLogger(FileMetricsDao.class);

  protected final SqlSessionFactory factory;
  protected final File repo;
  protected final String type;

  public static Set<String> readLines(File f) throws IOException{
    try (BufferedReader br = UTF8IoUtils.readerFromFile(f)) {
      return br.lines().collect(Collectors.toSet());
    }
  }

  public FileMetricsDao(String type, SqlSessionFactory factory, File repo) {
    this.type = type;
    this.factory = factory;
    this.repo = repo;
  }

  public String getType() {
    return type;
  }

  /**
   * Updates the file metrics on the filesystem as taking 2 explicit keys for
   * the source of data and where to store the metrics under.
   * This is needed for releases to use the key from the release as the source,
   * but the mother projects key for storing the metrics under.
   *
   * @param dataKey key from where the data is taken
   * @param storeKey key where the metrics are stored
   * @param attempt attempt where the metrics are stored
   */
  public void updateNames(K dataKey, K storeKey, int attempt) {
    try (SqlSession session = factory.openSession(true);
         NamesWriter nHandler = new NamesWriter(namesFile(storeKey, attempt), true)
    ){
      NameMapper nm = session.getMapper(NameMapper.class);

      DSID<Integer> skey = sectorKey(dataKey);
      PgUtils.consume(
        () -> nm.processNameStrings(skey.getDatasetKey(), skey.getId()),
        nHandler
      );
      LOG.info("Written {} name strings for {} {}-{}", nHandler.counter, type, dataKey, attempt);
    }
  }

  /**
   * Deletes all metrics stored for the given key, incl tree and name index sets.
   */
  public void deleteAll(K key) throws IOException {
    File dir = subdir(key);
    FileUtils.deleteDirectory(dir);
    LOG.info("Deleted all file metrics for {} {}", type, key);
  }

  /**
   * Writer that creates a UTF8 encoded file with consumed strings written as unmodified lines as they come in.
   * Optionally compressed with gzip.
   */
  public static class NamesWriter implements Consumer<String>, AutoCloseable {
    public int counter = 0;
    protected final File f;
    private final BufferedWriter w;
    
    public NamesWriter(File f, boolean zip) {
      this.f=f;
      try {
        w = zip ? UTF8IoUtils.writerFromGzipFile(f) : UTF8IoUtils.writerFromFile(f);
        
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
    public void accept(String name) {
      try {
        if (name != null) {
          counter++;
          w.append(name);
          w.append('\n');
        }
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  }

  public Stream<String> getNames(K key, int attempt) {
    return streamFile(namesFile(key, attempt), key, attempt);
  }

  public Stream<String> getTree(K key, int attempt) {
    return streamFile(treeFile(key, attempt), key, attempt);
  }

  private Stream<String> streamFile(File f, K key, int attempt) {
    try {
      BufferedReader br = UTF8IoUtils.readerFromGzipFile(f);
      return br.lines();

    } catch (FileNotFoundException e) {
      throw new AttemptMissingException(type, key, attempt, e);

    } catch (IOException e) {
      throw new RuntimeException("Failed to stream file " + f.getAbsolutePath(), e);
    }
  }

  public static class AttemptMissingException extends NotFoundException {
    public final int attempt;

    public AttemptMissingException(String type, Object key, int attempt) {
      super(key, buildMessage(type, key, attempt));
      this.attempt = attempt;
    }

    public AttemptMissingException(String type, Object key, int attempt, IOException cause) {
      super(key, buildMessage(type, key, attempt), cause);
      this.attempt = attempt;
    }

    private static String buildMessage(String type, Object key, int attempt) {
      return String.format("Import attempt %s for %s %s missing", attempt, type, key);
    }
  }

  public File treeFile(K key, int attempt) {
    return new File(subdir(key), attempt+"-tree.txt.gz");
  }

  public File namesFile(K key, int attempt) {
    return new File(subdir(key), attempt+"-names.txt.gz");
  }

  public abstract File subdir(K key);

  abstract DSID<Integer> sectorKey(K key);
}

