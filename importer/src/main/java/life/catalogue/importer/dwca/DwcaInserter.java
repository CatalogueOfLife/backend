package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.TaxonProperty;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.terms.EolDocumentTerm;
import life.catalogue.api.vocab.terms.EolReferenceTerm;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.csv.DwcaReader;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.eml.EmlParser;

import org.gbif.dwc.terms.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.apache.commons.io.FilenameUtils;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.collection.CollectionUtils.isEmpty;
import static life.catalogue.common.lang.Exceptions.runtimeInterruptIfCancelled;

/**
 *
 */
public class DwcaInserter extends NeoCsvInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);
  private static final UnknownTerm DNA_EXTENSION = UnknownTerm.build("http://rs.gbif.org/terms/1.0/DnaDerivedData", "DnaDerivedData", true);

  private DwcInterpreter inter;

  public DwcaInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, DwcaReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts DWCA data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException, InterruptedException {
    inter = new DwcInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // taxon core only, extensions are interpreted later
    insertEntities(reader, DwcTerm.Taxon,
        inter::interpretUsage,
        store::createNameAndUsaged
    );

    insertRelations(reader, ColdpTerm.NameRelation,
        inter::interpretNameRelations,
        store.names(),
        inter::taxonID,
        ColdpTerm.relatedNameID,
        Issue.NAME_ID_INVALID,
      true
    );

    interpretTypeMaterial(reader, DwcTerm.Occurrence,
      inter::interpretTypeMaterial
    );

    // https://github.com/CatalogueOfLife/backend/issues/1071
    // TODO: read type specimen extension and create type material or name relation for type names
    // http://rs.gbif.org/extension/gbif/1.0/typesandspecimen.xml

    insertTaxonEntities(reader, GbifTerm.Distribution,
        inter::interpretDistribution,
        inter::taxonID,
        (t, d) -> t.distributions.add(d)
    );

    insertTaxonEntities(reader, GbifTerm.VernacularName,
        inter::interpretVernacularName,
        inter::taxonID,
        (t, vn) -> t.vernacularNames.add(vn)
    );

    insertTaxonEntities(reader, GbifTerm.Multimedia,
        inter::interpretGbifMedia,
        inter::taxonID,
        (t, d) -> t.media.add(d)
    );
    insertTaxonEntities(reader, AcTerm.Multimedia,
      inter::interpretAcMedia,
      inter::taxonID,
      (t, d) -> t.media.add(d)
    );

    insertTaxonEntities(reader, DwcTerm.MeasurementOrFact,
      inter::interpretMeasurements,
      inter::taxonID,
      (t, p) -> t.properties.add(p)
    );

    insertTaxonEntities(reader, GbifTerm.Description,
      inter::interpretDescriptions,
      inter::taxonID,
      (t, p) -> t.properties.add(p)
    );
    // extract etymology from descriptions
    AtomicInteger cnt = new AtomicInteger();
    try (var tx = store.beginTx()) {
      reader.stream(GbifTerm.Description).forEach(rec -> {
        runtimeInterruptIfCancelled(NeoCsvInserter.INTERRUPT_MESSAGE);
        if (rec.getOrDefault(DcTerm.type, "").equalsIgnoreCase("etymology")) {
          String id = inter.taxonID(rec);
          if (id != null) {
            String description = rec.get(DcTerm.description);
            var nn = store.names().objByID(id, tx);
            if (nn != null && nn.getName().getEtymology() == null && description != null) {
              nn.getName().setEtymology(description);
              store.names().update(nn, tx);
              cnt.incrementAndGet();
            }
          }
        }
      });
    }
    LOG.info("Update {} names with etymology from descriptions", cnt.get());

    insertTaxonEntities(reader, GbifTerm.Reference,
      inter::interpretReference,
      inter::taxonID,
      (t, r) -> {
        if (r.getId() == null || !store.references().contains(r.getId())) {
          store.references().create(r);
        }
        if (t.isNameUsageBase() && r.getId() != null) {
          t.asNameUsageBase().getReferenceIds().add(r.getId());
        }
      }
    );

    insertTaxonEntities(reader, EolReferenceTerm.Reference,
      inter::interpretEolReference,
      inter::taxonID,
      (t, r) -> {
        if (store.references().create(r) && t.isNameUsageBase()) {
          t.asNameUsageBase().getReferenceIds().add(r.getId());
        }
      }
    );

    interpretTreatment(reader, EolDocumentTerm.Document,
      inter::interpretTreatment
    );

    insertTaxonEntities(reader, GbifTerm.SpeciesProfile,
      inter::interpretSpeciesProfile,
      inter::taxonID,
      (u, sp) -> {
        if (u.usage.isTaxon()) {
          Taxon t = u.asTaxon();
          // we can get multiple species profile records - aggregate them!
          if (t.isExtinct() == null) {
            t.setExtinct(sp.isExtinct());
          }

          if (sp.getEnvironments() != null) {
            if (t.getEnvironments() != null) {
              t.getEnvironments().addAll(sp.getEnvironments());
            } else {
              t.setEnvironments(sp.getEnvironments());
            }
          }

          if (!sp.getReferenceIds().isEmpty()) {
            t.getReferenceIds().addAll(sp.getReferenceIds());
          }

          if (!sp.properties.isEmpty()) {
            for (var kv : sp.properties.entrySet()) {
              var tp = new TaxonProperty();
              tp.setVerbatimKey(sp.getVerbatimKey());
              tp.setProperty(kv.getKey().prefixedName());
              tp.setValue(kv.getValue());
              if (!sp.getReferenceIds().isEmpty()) {
                tp.setReferenceId(sp.getReferenceIds().get(0));
              }
              u.properties.add(tp);
            }
          }
        }
      }
    );


    insertTaxonEntities(reader, GbifTerm.Identifier,
      inter::interpretAltIdentifiers,
      inter::taxonID,
      (nu, alt) -> {
        if (!isEmpty(alt.getIdentifier())) {
          var u = nu.usage.asUsageBase();
          if (isEmpty(u.getIdentifier())) {
            u.setIdentifier(alt.getIdentifier());
          } else {
            u.getIdentifier().addAll(alt.getIdentifier());
          }
        }
      }
    );

    // just add verbatim data for these well know extensions without interpreting any data!
    insertVerbatimEntities(reader, GbifTerm.Image, GbifTerm.TypesAndSpecimen, DwcTerm.ResourceRelationship, DNA_EXTENSION);
  }

  @Override
  protected BiConsumer<Node, Transaction> relationProcessor() {
    return new DwcaRelationInserter(store, reader.getMappingFlags());
  }
  
  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  public Optional<DatasetWithSettings> readMetadata() {
    // first try COL overrides, e.g. metadata.yaml
    Optional<DatasetWithSettings> ds = super.readMetadata();
    if (!ds.isPresent()) {
      // now look into the meta.xml for some other filename
      Optional<Path> mf = ((DwcaReader)reader).getMetadataFile();
      if (mf.isPresent()) {
        Path metadataPath = mf.get();
        if (Files.exists(metadataPath)) {
          try {
            String ext = FilenameUtils.getExtension(metadataPath.getFileName().toString());
            if (ext.equalsIgnoreCase("yaml") || ext.equalsIgnoreCase("yml")) {
              LOG.info("Read dataset metadata from YAML file {}", metadataPath);
              ds = ColdpMetadataParser.readYAML(Files.newInputStream(metadataPath));

            } else if (ext.equalsIgnoreCase("json")) {
              LOG.info("Read dataset metadata from JSON file {}", metadataPath);
              ds = ColdpMetadataParser.readJSON(Files.newInputStream(metadataPath));

            } else {
              ds = EmlParser.parse(metadataPath);
            }

          } catch (IOException | RuntimeException e) {
            LOG.error("Unable to read dataset metadata from dwc archive: {}", e.getMessage(), e);
          }
        } else {
          LOG.warn("Declared dataset metadata file {} does not exist.", metadataPath);
        }
      } else {
        LOG.info("No dataset metadata available");
      }
    }
    return ds;
  }
  
}
