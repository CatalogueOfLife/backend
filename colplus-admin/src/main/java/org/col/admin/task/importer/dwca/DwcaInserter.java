package org.col.admin.task.importer.dwca;

import com.google.common.collect.Lists;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.api.model.Dataset;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.neo4j.graphdb.Transaction;
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
  private DwcaReader reader;
  private DwcInterpreter inter;

  public DwcaInserter(NeoDb store, Path folder) throws IOException {
    super(folder, store);
  }

  private void initReader() {
    if (reader == null) {
      try {
        reader = DwcaReader.from(folder);
        reader.validate(meta);
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
      inter = new DwcInterpreter(store.getDataset(), meta, store);

      insertReferences();
      insertTaxaAndNames();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert DwC-A data", e);
    }
  }

  @Override
  public void insert() throws NormalizationFailedException {
    for (Term rowType : Lists.newArrayList(GbifTerm.Reference, GbifTerm.Distribution, GbifTerm.VernacularName)) {
      try (Transaction tx = store.getNeo().beginTx()){
        reader.stream(rowType).forEach(this::addVerbatimRecord);
      } catch (RuntimeException e) {
        LOG.error("Failed to insert DwC-A {} data. Skip all {}s", rowType, rowType, e);
      }
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new DwcaRelationInserter(store, meta, inter);
  }

  private void addVerbatimRecord(TermRecord rec) {
    super.addVerbatimRecord(DwcaReader.DWCA_ID, rec);
  }

  private void insertTaxaAndNames() {
    // taxon
    reader.stream(DwcTerm.Taxon).forEach(rec -> {
      if (rec.hasTerm(DwcaReader.DWCA_ID)) {
        UnescapedVerbatimRecord v = build(rec.get(DwcaReader.DWCA_ID), rec);
        NeoTaxon t = inter.interpret(v);
        store.put(t);
        meta.incRecords(t.name.getRank());
      } else {
        LOG.warn("Taxon record without id: {}", rec);
      }
    });
  }

  private void insertReferences() {
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    EmlParser parser = new EmlParser();
    initReader();
    if (reader.getMetadataFile().isPresent()) {
      try {
        return parser.parse(reader.getMetadataFile().get());
      } catch (IOException e) {
        LOG.error("Unable to read dataset metadata from dwc archive: {}", e.getMessage(), e);
      }
    } else {
      LOG.info("No dataset metadata available");
    }
    return Optional.empty();
  }

}
