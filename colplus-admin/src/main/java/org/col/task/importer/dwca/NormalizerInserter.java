package org.col.task.importer.dwca;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.col.api.Dataset;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.task.importer.neo.NormalizerStore;
import org.col.task.importer.neo.model.NeoTaxon;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.record.StarRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class NormalizerInserter {

  private static final Logger LOG = LoggerFactory.getLogger(NormalizerInserter.class);

  private Archive arch;
  private InsertMetadata meta = new InsertMetadata();
  private final NormalizerStore store;
  private VerbatimInterpreter interpreter;

  public NormalizerInserter(NormalizerStore store) throws IOException {
    this.store = store;
  }

  /**
   * Inserts DwC-A data from the source files into the normalizer store using
   * a neo4j batch inserter. Finally indices are build and a regular store instance returned.
   *
   * Before inserting it does a quick check to see if all required dwc terms are actually mapped.
   */
  public InsertMetadata insert(File dwca) throws NormalizationFailedException {
    openArchive(dwca);
    updateMetadata();
    interpreter = new VerbatimInterpreter(meta);
    store.startBatchMode();
    for (StarRecord star : arch) {
      insertStarRecord(star);
    }
    LOG.info("Data insert completed, {} nodes created", meta.getRecords());
    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk. Opening regular normalizer db again ...", meta.getRecords());
    return meta;
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  private void updateMetadata() {
    Dataset d = new Dataset();
    d.setDataFormat(DataFormat.DWCA);
    try {
      if (Strings.isNullOrEmpty(arch.getMetadataLocation())) {
        LOG.info("No dataset metadata available");

      } else {
        //TODO: replace with a leaner CoL dataset parser for EML that can handle specific CoL extensions
        org.gbif.api.model.registry.Dataset gbif = arch.getMetadata();
        d.setTitle(gbif.getTitle());
        d.setDescription(gbif.getDescription());
        d.setHomepage(gbif.getHomepage());
      }

    } catch (Throwable e) {
      LOG.error("Unable to read dataset metadata from dwc archive", e.getMessage());

    } finally {
      // the key will be preserved by the store
      store.put(d);
    }
  }

  @VisibleForTesting
  protected void insertStarRecord(StarRecord star) throws NormalizationFailedException {

    VerbatimRecord v = VerbatimRecordFactory.build(star);

    NeoTaxon i = interpreter.interpret(v, meta.isCoreIdUsed());

    // store interpreted record incl verbatim
    store.put(i);

    meta.incRecords();
    meta.incRank(i.name.getRank());
    if (meta.getRecords() % (10000) == 0) {
      LOG.info("Inserts done into neo4j: {}", meta.getRecords());
      if (Thread.interrupted()) {
        LOG.warn("NormalizerInserter interrupted, exit early with incomplete parsing");
        throw new NormalizationFailedException("NormalizerInserter interrupted");
      }
    }
  }

  private void openArchive(File dwca) throws NormalizationFailedException {
    try {
      LOG.info("Reading dwc archive from {}", dwca);
      arch = ArchiveFactory.openArchive(dwca);
      meta = DwcaMetaValidator.check(arch);
    } catch (IOException e) {
      throw new NormalizationFailedException("IOException opening archive " + dwca.getAbsolutePath(), e);
    }
  }

}
