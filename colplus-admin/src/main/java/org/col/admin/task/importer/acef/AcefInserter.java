package org.col.admin.task.importer.acef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.api.model.Dataset;
import org.col.api.model.TermRecord;
import org.col.api.vocab.DataFormat;
import org.gbif.dwc.terms.AcefTerm;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Splitter;

/**
 *
 */
public class AcefInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private AcefReader reader;
  private AcefInterpreter inter;

  public AcefInserter(NeoDb store, Path folder) throws IOException {
    super(folder, store);
  }

  private void initReader() {
    if (reader == null) {
      try {
        reader = AcefReader.from(folder);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Inserts ACEF data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new AcefInterpreter(store.getDataset(), meta, store);

      insertReferences();
      insertTaxaAndNames();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  public void insert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      reader.stream(AcefTerm.Distribution).forEach(this::addVerbatimRecord);
      reader.stream(AcefTerm.CommonNames).forEach(this::addVerbatimRecord);

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, inter);
  }

  private void addVerbatimRecord(TermRecord rec) {
    super.addVerbatimRecord(AcefTerm.AcceptedTaxonID, rec);
  }

  private void insertTaxaAndNames() {
    // species
    reader.stream(AcefTerm.AcceptedSpecies).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
    // infraspecies
    reader.stream(AcefTerm.AcceptedInfraSpecificTaxa).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      // accepted infraspecific names in ACEF have no genus or species but a link to their parent
      // species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
    // synonyms
    reader.stream(AcefTerm.Synonyms).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.ID), rec);
      NeoTaxon t = inter.interpretTaxon(v, true);
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
  }

  private void insertReferences() {
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    Dataset d = null;
    initReader();
    Optional<TermRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      TermRecord dr = metadata.get();
      d = new Dataset();
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setHomepage(dr.getURI(AcefTerm.HomeURL));
      d.setDataFormat(DataFormat.ACEF);
    }
    return Optional.ofNullable(d);
  }

}
