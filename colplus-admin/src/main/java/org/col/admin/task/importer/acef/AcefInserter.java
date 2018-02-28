package org.col.admin.task.importer.acef;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.dwca.VerbatimRecordFactory;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 *
 */
public class AcefInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private Map<String, Integer> refKeys = Maps.newHashMap();
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
   * Inserts ACEF data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new AcefInterpreter(meta, store);

      insertReferences();
      insertTaxaAndNames();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  public void insert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()){
      reader.stream(AcefTerm.Distribution).forEach(this::addVerbatimRecord);
      reader.stream(AcefTerm.CommonNames).forEach(this::addVerbatimRecord);

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, meta, inter);
  }

  private void addVerbatimRecord(TermRecord rec) {
    String id = rec.get(AcefTerm.AcceptedTaxonID);
    NeoTaxon t = store.getByTaxonID(id);
    if (t == null) {
      LOG.warn("Non existing taxonID {} found in {} record line {}, {}", id, rec.getType().simpleName(), rec.getLine(), rec.getFile());

    } else if(t.verbatim == null){
      LOG.warn("No verbatim data found for taxonID {} in {} record {} line {}, {}", id, rec.getType().simpleName(), rec.getLine(), rec.getFile());

    } else {
      t.verbatim.addExtensionRecord(rec.getType(), rec);
      store.update(t);
    }
  }

  private void insertTaxaAndNames() {
    // species
    reader.stream(AcefTerm.AcceptedSpecies).forEach( rec -> {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
    // infraspecies
    reader.stream(AcefTerm.AcceptedInfraSpecificTaxa).forEach( rec -> {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      if (!t.name.getRank().isInfraspecific()) {
        LOG.info("Expected infraspecific taxon but found {} for name {}: {}", t.name.getRank(), t.getTaxonID(), t.name.getScientificName());
        t.addIssue(Issue.INCONSISTENT_NAME);
      }
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
    // synonyms
    reader.stream(AcefTerm.Synonyms).forEach( rec -> {
      VerbatimRecord v = VerbatimRecordFactory.build(rec.get(AcefTerm.ID), rec);
      NeoTaxon t = inter.interpretTaxon(v, true);
      store.put(t);
      meta.incRecords(t.name.getRank());
    });
  }

  private void insertReferences() {
    reader.stream(AcefTerm.Reference).forEach( rec -> {
      Reference ref = Reference.create();
      ref.setId(rec.get(AcefTerm.ReferenceID));
      ref.setTitle(rec.get(AcefTerm.Title));
      ref.setYear(rec.getIntDefault(AcefTerm.Year, null));
      store.put(ref);
      refKeys.put(ref.getId(), ref.getKey());
    });
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
