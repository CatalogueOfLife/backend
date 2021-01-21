package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.ColDwcTerm;
import life.catalogue.api.vocab.Issue;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.coldp.MetadataParser;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.reference.ReferenceFactory;
import org.gbif.dwc.terms.*;
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
  private static final Term EOL_DOCUMENT = TermFactory.instance().findClassTerm("http://eol.org/schema/media/Document");

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
        inter::interpretUsage,
        u -> store.createNameAndUsage(u) != null
    );

    // TODO: read type specimen extension and update name usage!
    // http://rs.gbif.org/extension/gbif/1.0/typesandspecimen.xml
    //updateEntities(reader, DwcTerm.Taxon,
    //    inter::interpret,
    //    u -> store.createNameAndUsage(u) != null
    //);

    insertRelations(reader, ColDwcTerm.NameRelations,
        inter::interpretNameRelations,
        store.names(),
        DwcaTerm.ID,
        ColDwcTerm.relatedNameUsageID,
        Issue.NAME_ID_INVALID,
      true
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

    //TODO: insert treatments via EOL Document extension !!!
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
    Optional<Path> mf = ((DwcaReader)reader).getMetadataFile();
    if (mf.isPresent()) {
      Path metadataPath = mf.get();
      if (Files.exists(metadataPath)) {
        try {
          return EmlParser.parse(metadataPath);
          
        } catch (IOException | RuntimeException e) {
          LOG.error("Unable to read dataset metadata from dwc archive: {}", e.getMessage(), e);
        }
      } else {
        LOG.warn("Declared dataset metadata file {} does not exist.", metadataPath);
      }
    } else {
      LOG.info("No dataset metadata available");
    }

    // try other metadata formats
    return MetadataParser.readMetadata(folder);
  }
  
}
