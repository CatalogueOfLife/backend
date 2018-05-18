package org.col.admin.task.importer.dwca;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class DwcaInserter extends NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);
  private DwcaReader reader;
  private DwcInterpreter inter;

  public DwcaInserter(NeoDb store, Path folder, ReferenceFactory refFactory) throws IOException {
    super(folder, store, refFactory);
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
   * Inserts DWCA data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new DwcInterpreter(store.getDataset(), meta, store, refFactory);

      // taxon core only, extensions are interpreted later
      insertEntities(reader, DwcTerm.Taxon,
          inter::interpret,
          t -> {
            meta.incRecords(t.name.getRank());
            store.put(t);
          }
      );

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert DwC-A data", e);
    }
  }

  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      insertTaxonEntities(reader, GbifTerm.Distribution,
          inter::interpretDistribution,
          DwcaReader.DWCA_ID,
          (t, d) -> t.distributions.add(d)
      );

      insertTaxonEntities(reader, GbifTerm.VernacularName,
          inter::interpretVernacularName,
          DwcaReader.DWCA_ID,
          (t, d) -> t.vernacularNames.add(d)
      );

      insertTaxonEntities(reader, GbifTerm.Reference,
          inter::interpretReference,
          DwcaReader.DWCA_ID,
          (t, r) -> {
            store.put(r);
            t.bibliography.add(r.getKey());
          }
      );

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read DWCA files", e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new DwcaRelationInserter(store, meta);
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
