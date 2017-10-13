package org.col.commands.importer.neo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.col.api.Dataset;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.commands.importer.NormalizationFailedException;
import org.col.commands.importer.VerbatimInterpreter;
import org.col.commands.importer.VerbatimRecordFactory;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.io.MetadataException;
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
  VerbatimInterpreter interpreter = new VerbatimInterpreter();

  public NormalizerInserter(NormalizerStore store) throws IOException {
    this.store = store;
  }

  /**
   * Inserts DwC-A data from the source files into the normalizer store using
   * a neo4j batch inserter. Finally indices are build and a regular store instance returned.
   */
  public InsertMetadata insert(File dwca) throws NormalizationFailedException {
    openArchive(dwca);
    updateMetadata();
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
      //TODO: replace with a leaner CoL dataset parser for EML that can handle specific CoL extensions
      org.gbif.api.model.registry.Dataset gbif = arch.getMetadata();

      d.setTitle(gbif.getTitle());
      d.setDescription(gbif.getDescription());
      d.setHomepage(gbif.getHomepage());

    } catch (MetadataException e) {
      LOG.error("Unable to read dataset metadata from dwc archive", e);

    } finally {
      // the key will be preserved by the store
      store.put(d);
    }
  }

  @VisibleForTesting
  protected void insertStarRecord(StarRecord star) throws NormalizationFailedException {

    VerbatimRecord v = VerbatimRecordFactory.build(star);

    NeoTaxon i = interpreter.interpret(v);

    // store interpreted record incl verbatim
    store.put(i);

    meta.incRecords();
    meta.incRank(i.taxon.getRank());
    if (meta.getRecords() % (10000) == 0) {
      LOG.info("Inserts done into neo4j: {}", meta.getRecords());
      if (Thread.interrupted()) {
        LOG.warn("NormalizerInserter interrupted, exit early with incomplete parsing");
        throw new NormalizationFailedException("NormalizerInserter interrupted");
      }
    }
  }

  private void openArchive(File dwca) throws NormalizationFailedException {
    meta = new InsertMetadata();
    try {
      LOG.info("Reading dwc archive from {}", dwca);
      arch = ArchiveFactory.openArchive(dwca);
      if (!arch.getCore().hasTerm(DwcTerm.taxonID)) {
        LOG.warn("Using core ID for taxonID");
        meta.setCoreIdUsed(true);
      }
      // multi values in use for acceptedID?
      for (Term t : arch.getCore().getTerms()) {
        String delim = arch.getCore().getField(t).getDelimitedBy();
        if (!Strings.isNullOrEmpty(delim)) {
          meta.getMultiValueDelimiters().put(t, Splitter.on(delim).omitEmptyStrings());
        }
      }
      for (Term t : DwcTerm.HIGHER_RANKS) {
        if (arch.getCore().hasTerm(t)) {
          meta.setDenormedClassificationMapped(true);
          break;
        }
      }
      if (arch.getCore().hasTerm(DwcTerm.parentNameUsageID) || arch.getCore().hasTerm(DwcTerm.parentNameUsage)) {
        meta.setParentNameMapped(true);
      }
      if (arch.getCore().hasTerm(DwcTerm.acceptedNameUsageID) || arch.getCore().hasTerm(DwcTerm.acceptedNameUsage)) {
        meta.setAcceptedNameMapped(true);
      }
      if (arch.getCore().hasTerm(DwcTerm.originalNameUsageID) || arch.getCore().hasTerm(DwcTerm.originalNameUsage)) {
        meta.setOriginalNameMapped(true);
      }
    } catch (IOException e) {
      throw new NormalizationFailedException("IOException opening archive " + dwca.getAbsolutePath(), e);
    }
  }

}
