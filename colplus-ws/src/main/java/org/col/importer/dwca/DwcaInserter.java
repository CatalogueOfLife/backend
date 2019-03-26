package org.col.importer.dwca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.col.importer.NeoInserter;
import org.col.importer.NormalizationFailedException;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NodeBatchProcessor;
import org.col.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.ColDwcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.DwcaTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class DwcaInserter extends NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);
  private DwcInterpreter inter;
  
  public DwcaInserter(NeoDb store, Path folder, ReferenceFactory refFactory) throws IOException {
    super(folder, DwcaReader.from(folder), store, refFactory);
  }
  
  /**
   * Inserts DWCA data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      inter = new DwcInterpreter(store.getDataset(), reader.getMappingFlags(), refFactory, store);

      // taxon core only, extensions are interpreted later
      insertEntities(reader, DwcTerm.Taxon,
          inter::interpret,
          store::createNameAndUsage
      );
  
      insertNameRelations(reader, ColDwcTerm.NameRelations,
          inter::interpretNameRelations,
          DwcaTerm.ID,
          ColDwcTerm.relatedNameUsageID
      );
  
      insertTaxonEntities(reader, GbifTerm.Distribution,
          inter::interpretDistribution,
          DwcaTerm.ID,
          (t, d) -> t.distributions.add(d)
      );
  
      insertTaxonEntities(reader, GbifTerm.VernacularName,
          inter::interpretVernacularName,
          DwcaTerm.ID,
          (t, vn) -> t.vernacularNames.add(vn)
      );
  
      insertTaxonEntities(reader, GbifTerm.Description,
          inter::interpretDescription,
          DwcaTerm.ID,
          (t, d) -> t.descriptions.add(d)
      );
  
      insertTaxonEntities(reader, GbifTerm.Multimedia,
          inter::interpretMedia,
          DwcaTerm.ID,
          (t, d) -> t.media.add(d)
      );

      insertTaxonEntities(reader, GbifTerm.Reference,
          inter::interpretReference,
          DwcaTerm.ID,
          (t, r) -> {
            if (store.create(r)) {
              t.bibliography.add(r.getId());
            } else {
          
            }
          }
      );
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to batch insert DwC-A data", e);
    }
  }
  
  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new DwcaRelationInserter(store, reader.getMappingFlags());
  }
  
  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    EmlParser parser = new EmlParser();
    Optional<Path> mf = ((DwcaReader)reader).getMetadataFile();
    if (mf.isPresent()) {
      Path metadataPath = mf.get();
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
