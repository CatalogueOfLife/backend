package life.catalogue.importer.coldp;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Treatment;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.csv.ColdpReader;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.DataCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.bibtex.BibTexInserter;
import life.catalogue.importer.csljson.CslJsonInserter;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.TreatmentFormatParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class ColdpInserter extends DataCsvInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);

  private ColdpInterpreter inter;

  public ColdpInserter(ImportStore store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, ColdpReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void insert() throws NormalizationFailedException, InterruptedException {
    inter = new ColdpInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
    // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
    insertEntities(reader, ColdpTerm.Reference,
        inter::interpretReference,
      r -> store.references().create(r)
    );

    // insert CSL-JSON references
    // insert BibTex references
    insertExtendedReferences();

    // name_usage combination
    insertEntities(reader, ColdpTerm.NameUsage,
      inter::interpretNameUsage,
      store::createNameAndUsage
    );

    // TODO: authors
    insertEntities(reader, ColdpTerm.Author,
      inter::interpretAuthor,
      a -> false
    );

    // name & relations
    insertEntities(reader, ColdpTerm.Name,
      inter::interpretName,
      store.names()::create
    );
    insertNameRelations(reader, ColdpTerm.NameRelation,
        inter::interpretNameRelations,
        Issue.NAME_ID_INVALID
    );
    interpretTypeMaterial(reader, ColdpTerm.TypeMaterial,
        inter::interpretTypeMaterial
    );

    // taxa
    insertEntities(reader, ColdpTerm.Taxon,
        inter::interpretTaxon,
        store.usages()::create
    );
    // taxon concept relations
    insertTaxonTCRelations(reader, ColdpTerm.TaxonConceptRelation,
      inter::interpretTaxonRelations,
      Issue.TAXON_ID_INVALID
    );
    // species interactions
    insertTaxonSpiRelations(reader, ColdpTerm.SpeciesInteraction,
      inter::interpretSpeciesInteractions,
      Issue.TAXON_ID_INVALID
    );

    // synonyms
     insertEntities(reader, ColdpTerm.Synonym,
        inter::interpretSynonym,
        store.usages()::create
    );

    // supplementary
    insertTaxonEntities(reader, ColdpTerm.Distribution,
        inter::interpretDistribution,
        ColdpTerm.taxonID,
        (t, d) -> t.distributions.add(d)
    );
    insertTaxonEntities(reader, ColdpTerm.Media,
        inter::interpretMedia,
        ColdpTerm.taxonID,
        (t, m) -> t.media.add(m)
    );
    insertTaxonEntities(reader, ColdpTerm.VernacularName,
        inter::interpretVernacular,
        ColdpTerm.taxonID,
        (t, vn) -> t.vernacularNames.add(vn)
    );
    insertTaxonEntities(reader, ColdpTerm.SpeciesEstimate,
      inter::interpretEstimate,
      ColdpTerm.taxonID,
      (t, se) -> t.estimates.add(se)
    );
    insertTaxonEntities(reader, ColdpTerm.TaxonProperty,
      inter::interpretProperties,
      ColdpTerm.taxonID,
      (t, p) -> t.properties.add(p)
    );
    insertTreatments();
  }

  private void insertTreatments() throws InterruptedException {
    ColdpReader coldp = (ColdpReader) reader;
    if (coldp.hasTreatments()) {
      try {
        final int datasetKey = store.getDatasetKey();
        for (Path tp : coldp.getTreatments()) {
          interruptIfCancelled("DAta inserter interrupted, exit early");
          insertTreatment(datasetKey, tp);
        }
      } catch (IOException e) {
        LOG.error("Failed to read treatments", e);
      }
    }
  }

  private void insertTreatment(int datasetKey, Path tp) {
    VerbatimRecord v = new VerbatimRecord();
    v.setType(ColdpTerm.Treatment);
    v.setDatasetKey(datasetKey);
    v.setFile(tp.toString());

    String filename = tp.getFileName().toString();
    String suffix = FilenameUtils.getExtension(filename);
    String taxonid = FilenameUtils.getBaseName(filename);

    v.put(ColdpTerm.taxonID, taxonid);
    v.put(ColdpTerm.format, suffix);
    store.put(v);

    try {
      Treatment t = new Treatment();
      t.setDatasetKey(datasetKey);
      t.setVerbatimKey(v.getId());
      t.setId(taxonid);
      t.setFormat(
        SafeParser.parse(TreatmentFormatParser.PARSER, suffix).orNull(Issue.UNPARSABLE_TREAMENT_FORMAT, v)
      );
      t.setDocument(InputStreamUtils.readEntireStream(Files.newInputStream(tp)));

      if (t.getFormat() != null && t.getDocument() != null && t.getId() != null) {
        UsageData nu = store.usages().objByID(t.getId());
        if (nu != null) {
          nu.treatment = t;
          store.usages().update(nu);
        } else {
          v.add(Issue.TAXON_ID_INVALID);
        }
      } else {
        v.add(Issue.UNPARSABLE_TREATMENT);
      }
    } catch (IOException | RuntimeException e) {
      LOG.warn("Failed to read treatment {}", e.getMessage(), e);
      v.add(Issue.UNPARSABLE_TREATMENT);
    }
    store.put(v);
  }

  private void insertExtendedReferences() throws InterruptedException {
    ColdpReader coldp = (ColdpReader) reader;
    if (coldp.hasExtendedReferences()) {
      if (coldp.getBibtexFile() != null) {
        BibTexInserter bibIns = new BibTexInserter(store, coldp.getBibtexFile(), refFactory);
        bibIns.insertAll();
      }
      if (coldp.getCslJsonLinesFile() != null) {
        CslJsonInserter bibIns = new CslJsonInserter(store, coldp.getCslJsonLinesFile(), true, refFactory);
        bibIns.insertAll();
      }
      if (coldp.getCslJsonFile() != null) {
        CslJsonInserter bibIns = new CslJsonInserter(store, coldp.getCslJsonFile(), false, refFactory);
        bibIns.insertAll();
      }
    }
  }
}
