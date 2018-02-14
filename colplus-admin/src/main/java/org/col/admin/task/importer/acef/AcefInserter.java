package org.col.admin.task.importer.acef;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.VerbatimRecordFactory;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.AcefTerm;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 *
 */
public class AcefInserter implements NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private final NeoDb store;
  private final File folder;
  private InsertMetadata meta = new InsertMetadata();
  private Map<String, Integer> refKeys = Maps.newHashMap();
  private Map<String, Integer> taxKeys = Maps.newHashMap();
  private AcefReader reader;
  private AcefInterpreter inter;

  public AcefInserter(NeoDb store, File folder) throws IOException {
    this.store = store;
    this.folder = folder;
  }

  /**
   * Inserts ACEF data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  public InsertMetadata insertAll() throws NormalizationFailedException {
    try {
      reader = AcefReader.from(folder);
      inter = new AcefInterpreter(meta, store);
      store.startBatchMode();

      insertReferences();
      insertTaxaAndNames();
      insertSupplementary();

      LOG.info("Data insert completed, {} nodes created", meta.getRecords());
      store.endBatchMode();

      Optional<TermRecord> metadata = reader.readFirstRow(AcefTerm.SOURCE_DATABASE);
      if (metadata.isPresent()) {
        updateMetadata(metadata.get());
      } else {
        LOG.warn("No dataset metadata found");
      }

      LOG.info("ACEF insert completed with {} records", meta.getRecords());
      return meta;

    } catch (IOException e) {
      throw new NormalizationFailedException("Failed to read ACEF files");
    }
  }

  private void insertSupplementary() {

  }

  private void insertTaxaAndNames() {
    // species
    for (TermRecord rec : reader.read(AcefTerm.ACCEPTED_SPECIES)) {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      meta.incRecords(t.name.getRank());
    }
    // infraspecies
    for (TermRecord rec : reader.read(AcefTerm.ACCEPTED_INFRA_SPECIFIC_TAXA)) {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      if (!t.name.getRank().isInfraspecific()) {
        LOG.info("Expected infraspecific taxon but found {} for name {}: {}", t.name.getRank(), t.getTaxonID(), t.name.getScientificName());
        t.addIssue(Issue.INCONSISTENT_NAME);
      }
      store.put(t);
      meta.incRecords(t.name.getRank());
    }
    // synonyms
    for (TermRecord rec : reader.read(AcefTerm.SYNONYMS)) {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.ID), rec);
      NeoTaxon t = inter.interpretTaxon(v, true);
      store.put(t);
      meta.incRecords(t.name.getRank());
    }
  }

  private void insertReferences() {
    for (TermRecord rec : reader.read(AcefTerm.REFERENCE)) {
      Reference ref = new Reference();
      ref.setId(rec.get(AcefTerm.ReferenceID));
      ref.setTitle(rec.get(AcefTerm.Title));
      ref.setYear(rec.getIntDefault(AcefTerm.Year, null));
      store.put(ref);
      refKeys.put(ref.getId(), ref.getKey());
    }
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  private void updateMetadata(TermRecord dr) {
    Dataset d = new Dataset();
    d.setTitle(dr.get(AcefTerm.DatabaseFullName));
    d.setVersion(dr.get(AcefTerm.DatabaseVersion));
    d.setDescription(dr.get(AcefTerm.Abstract));
    d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
    d.setDescription(dr.get(AcefTerm.Abstract));
    d.setHomepage(dr.getURI(AcefTerm.HomeURL));
    d.setDataFormat(DataFormat.ACEF);
    try {
      LOG.info("No dataset metadata available");
    } catch (Throwable e) {
      LOG.error("Unable to read dataset metadata from dwc archive", e.getMessage());

    } finally {
      // the key will be preserved by the store
      store.put(d);
    }
  }

}
