package org.col.admin.importer.dwca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.ColDwcTerm;
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
      inter = new DwcInterpreter(store.getDataset(), meta, refFactory);
      
      // taxon core only, extensions are interpreted later
      insertEntities(reader, DwcTerm.Taxon,
          inter::interpret,
          store::put
      );
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert DwC-A data", e);
    }
  }
  
  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      insertNameRelations(reader, ColDwcTerm.NameRelations,
          inter::interpretNameRelations,
          DwcaReader.DWCA_ID,
          ColDwcTerm.relatedNameUsageID
      );
      
      insertTaxonEntities(reader, GbifTerm.Distribution,
          inter::interpretDistribution,
          DwcaReader.DWCA_ID,
          (t, d) -> t.distributions.add(d)
      );
      
      insertTaxonEntities(reader, GbifTerm.VernacularName,
          inter::interpretVernacularName,
          DwcaReader.DWCA_ID,
          (t, vn) -> t.vernacularNames.add(vn)
      );
      
      insertTaxonEntities(reader, GbifTerm.Reference,
          inter::interpretReference,
          DwcaReader.DWCA_ID,
          (t, r) -> {
            store.put(r);
            t.bibliography.add(r.getId());
          }
      );
      tx.success();
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read DWCA files", e);
    }
  }
  
  @Override
  protected NodeBatchProcessor relationProcessor() {
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
      Path metadataPath = reader.getMetadataFile().get();
      if (Files.exists(metadataPath)) {
        try {
          return parser.parse(metadataPath);
          
        } catch (IOException | RuntimeException e) {
          LOG.error("Unable to read dataset metadata from dwc archive: {}", e.getMessage(), e);
        }
      } else {
        LOG.warn("Declared dataset metadata file {} does not exist.", metadataPath);
      }
    } else {
      LOG.info("No dataset metadata available");
    }
    return Optional.empty();
  }
  
}
