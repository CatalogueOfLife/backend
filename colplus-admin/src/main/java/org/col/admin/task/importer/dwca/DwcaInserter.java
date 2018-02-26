package org.col.admin.task.importer.dwca;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.model.Dataset;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.record.StarRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 *
 */
public class DwcaInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);

  private Archive arch;
  private DwcInterpreter interpreter;

  public DwcaInserter(NeoDb store, Path dwca) throws IOException {
    super(dwca, store);
  }

  /**
   * Inserts DwC-A data from the source files into the normalizer store using
   * a neo4j batch inserter. Finally indices are build and a regular store instance returned.
   *
   * Before inserting it does a quick check to see if all required dwc terms are actually mapped.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    openArchive(folder);
    interpreter = new DwcInterpreter(meta, store);
    //TODO: insert reference file first
    for (StarRecord star : arch) {
      insertStarRecord(star);
    }
  }

  @Override
  public void insert() throws NormalizationFailedException {
    // nothing for dwca
  }

    /**
     * Reads the dataset metadata and puts it into the store
     */
  public Optional<Dataset> readMetadata() {
    Dataset d = new Dataset();
    try {
      d.setDataFormat(DataFormat.DWCA);
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
    }
    return Optional.ofNullable(d);
  }

  @VisibleForTesting
  protected void insertStarRecord(StarRecord star) throws NormalizationFailedException {

    VerbatimRecord v = VerbatimRecordFactory.build(star);

    NeoTaxon i = interpreter.interpret(v);

    // store interpreted record incl verbatim
    store.put(i);

    meta.incRecords(i.name.getRank());
  }

  private void openArchive(Path dwca) throws NormalizationFailedException {
    try {
      LOG.info("Reading dwc archive from {}", dwca);
      arch = ArchiveFactory.openArchive(dwca.toFile());
      DwcaMetaValidator.check(arch, meta);
    } catch (IOException e) {
      throw new NormalizationFailedException("IOException opening archive " + dwca, e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new DwcaRelationInserter(store, meta);
  }
}
