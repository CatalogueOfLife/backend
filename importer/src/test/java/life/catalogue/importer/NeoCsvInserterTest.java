package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.ColdpReader;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.ImportStoreFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.apache.commons.io.FileUtils;
import org.junit.Test;



import com.google.common.io.Files;

import static org.junit.Assert.*;

public class NeoCsvInserterTest {

  @Test
  public void interruptInsertAll() throws Exception{
    final int datasetKey = 3;
    NormalizerConfig cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    ImportStoreFactory factory = new ImportStoreFactory(cfg);
    ImportStore db = factory.create(datasetKey, 1);

    URL url = getClass().getResource("/coldp/20");
    Path src = Paths.get(url.toURI());

    //Path src = cfg.sourceDir(datasetKey).toPath();
    java.nio.file.Files.createDirectories(src);
    DataCsvInserter ins = new DataCsvInserter(src, ColdpReader.from(src), db, new DatasetSettings(), null) {
      @Override
      protected void insert() throws NormalizationFailedException, InterruptedException, InterruptedRuntimeException {
        throw new InterruptedRuntimeException("no inserts");
      }
    };

    // InterruptedException
    try {
      ins.insertAll();
      fail("Expected InterruptedException");
    } catch (InterruptedException e) {
      // good!
    } finally {
      db.close();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }

  /**
   * An exception for a single record flags it as not interpreted and does not fail the entire insert.
   * https://github.com/CatalogueOfLife/backend/issues/1627
   */
  @Test
  public void recordExceptionDoesNotFailInsert() throws Exception {
    NormalizerConfig cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    ImportStore db = new ImportStoreFactory(cfg).create(4, 1);
    Path src = Paths.get(getClass().getResource("/coldp/20").toURI());
    final AtomicInteger calls = new AtomicInteger();
    DataCsvInserter ins = new DataCsvInserter(src, ColdpReader.from(src), db, new DatasetSettings(), null) {
      @Override
      protected void insert() {
        insertEntities(reader, ColdpTerm.NameUsage, rec -> {
          if (calls.incrementAndGet() == 1) {
            throw new IllegalStateException("bad record");
          }
          return Optional.empty();
        }, x -> true);
      }
    };
    try {
      ins.insertAll();
      List<VerbatimRecord> recs = db.verbatimList(ColdpTerm.NameUsage).toList();
      assertTrue(calls.get() > 1);
      assertEquals(calls.get(), recs.size());
      assertTrue(recs.stream().allMatch(v -> v.contains(Issue.NOT_INTERPRETED)));
    } finally {
      db.close();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
}
