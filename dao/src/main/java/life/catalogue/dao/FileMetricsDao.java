package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameMapper;

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
 * stored on the filesystem. We use GZIP compression to keep storage small.
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

  /**
   * The type of metrics stored by this DAO.
   * Used for logging and error messages.
   * @return
   */
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
    final File f = namesFile(storeKey, attempt);
    int written;
    try (SqlSession session = factory.openSession(true);
         NamesWriter nHandler = new NamesWriter(f, true)
    ){
      NameMapper nm = session.getMapper(NameMapper.class);

      DSID<Integer> skey = sectorKey(dataKey);
      PgUtils.consume(
        () -> nm.processNameStrings(skey.getDatasetKey(), skey.getId()),
        nHandler
      );
      written = nHandler.counter;
      LOG.info("Written {} name strings for {} {}-{}", written, type, dataKey, attempt);
    }
    // an empty names file carries nothing the metrics row does not already record - do not keep it
    if (written == 0) {
      deleteOrWarn(f);
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
   * Deletes both the names and tree file for a given import/release attempt
   */
  public void deleteAttempt(K key, int attempt) {
    deleteOrWarn(namesFile(key, attempt));
  }

  /**
   * Deletes the names file of that attempt if it exists.
   * Uses the delete call itself as the existence check - one filesystem round trip instead of a
   * stat followed by an unlink, which matters when the metrics repo is on network storage.
   * @return true if a file was actually removed
   */
  public boolean deleteAttemptIfExists(K key, int attempt) {
    return namesFile(key, attempt).delete();
  }

  protected void deleteOrWarn(File file) {
    if (file.exists()) {
      if (!FileUtils.deleteQuietly(file)) {
        LOG.warn("Failed to delete file {}", file);
      }
    }
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

  /**
   * A sync/import that produced no names writes no file, so a missing file is not an error as long as the
   * persisted metrics record a zero name count. Only a genuinely unknown attempt is a NotFound.
   */
  public Stream<String> getNames(K key, int attempt) {
    File f = namesFile(key, attempt);
    if (!f.exists()) {
      Integer nameCount = persistedNameCount(key, attempt);
      if (nameCount != null && nameCount == 0) {
        return Stream.empty();
      }
    }
    return streamFile(f, key, attempt);
  }

  /**
   * @return the persisted name count for that attempt, or null if no metrics record exists for it
   */
  protected abstract Integer persistedNameCount(K key, int attempt);

  protected Stream<String> streamFile(File f, K key, int attempt) {
    try {
      BufferedReader br = UTF8IoUtils.readerFromGzipFile(f);
      return br.lines().onClose(() -> {
        try {
          br.close();
        } catch (IOException e) {
          LOG.warn("Failed to close names file {}", f.getAbsolutePath(), e);
        }
      });

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

  public File namesFile(K key, int attempt) {
    return new File(subdir(key), attempt+"-names.txt.gz");
  }

  public abstract File subdir(K key);

  /**
   * Converts the given key into a DSID for a sector,
   * i.e. a dataset key and a sector key.
   *
   * In case of dataset metrics the sector key is null.
   */
  abstract DSID<Integer> sectorKey(K key);
}

