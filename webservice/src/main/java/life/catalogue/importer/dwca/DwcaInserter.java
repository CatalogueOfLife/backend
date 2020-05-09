package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.ColDwcTerm;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.reference.ReferenceFactory;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.DwcaTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 *
 */
public class DwcaInserter extends NeoCsvInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);
  private DwcInterpreter inter;
  
  public DwcaInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, DwcaReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts DWCA data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException {
    inter = new DwcInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // taxon core only, extensions are interpreted later
    insertEntities(reader, DwcTerm.Taxon,
        inter::interpret,
        u -> store.createNameAndUsage(u) != null
    );

    // TODO: read type specimen extension and update name usage!
    // http://rs.gbif.org/extension/gbif/1.0/typesandspecimen.xml
    //updateEntities(reader, DwcTerm.Taxon,
    //    inter::interpret,
    //    u -> store.createNameAndUsage(u) != null
    //);

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
          if (store.references().create(r)) {
            t.usage.getReferenceIds().add(r.getId());
          } else {

          }
        }
    );
  }
  
  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new DwcaRelationInserter(store, reader.getMappingFlags());
  }
  
  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  public Optional<DatasetWithSettings> readMetadata() {
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
