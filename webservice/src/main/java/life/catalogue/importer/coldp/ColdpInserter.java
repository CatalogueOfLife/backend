package life.catalogue.importer.coldp;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.csv.ColdpReader;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.bibtex.BibTexInserter;
import life.catalogue.importer.csljson.CslJsonInserter;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.TreatmentFormatParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class ColdpInserter extends NeoCsvInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);

  private ColdpInterpreter inter;

  public ColdpInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, ColdpReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException, InterruptedException {
    inter = new ColdpInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
    // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
    insertEntities(reader, ColdpTerm.Reference,
        inter::interpretReference,
        store.references()::create
    );

    // insert CSL-JSON references
    // insert BibTex references
    insertExtendedReferences();

    // name_usage combination
    insertEntities(reader, ColdpTerm.NameUsage,
      inter::interpretNameUsage,
      u -> store.createNameAndUsage(u) != null
    );

    // name & relations
    insertEntities(reader, ColdpTerm.Name,
        inter::interpretName,
        n -> store.names().create(n) != null
    );
    insertRelations(reader, ColdpTerm.NameRelation,
        inter::interpretNameRelations,
        store.names(),
        ColdpTerm.nameID,
        ColdpTerm.relatedNameID,
        Issue.NAME_ID_INVALID,
      true
    );
    interpretTypeMaterial(reader, ColdpTerm.TypeMaterial,
        inter::interpretTypeMaterial
    );

    // taxa
    insertEntities(reader, ColdpTerm.Taxon,
        inter::interpretTaxon,
        t -> store.usages().create(t) != null
    );
    // taxon concept relations
    insertRelations(reader, ColdpTerm.TaxonConceptRelation,
      inter::interpretTaxonRelations,
      store.usages(),
      ColdpTerm.taxonID,
      ColdpTerm.relatedTaxonID,
      Issue.TAXON_ID_INVALID,
      true
    );
    // species interactions
    insertRelations(reader, ColdpTerm.SpeciesInteraction,
      inter::interpretSpeciesInteractions,
      store.usages(),
      ColdpTerm.taxonID,
      ColdpTerm.relatedTaxonID,
      Issue.TAXON_ID_INVALID,
      false
    );

    // synonyms
    insertEntities(reader, ColdpTerm.Synonym,
        inter::interpretSynonym,
        s -> store.usages().create(s) != null
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

  @Override
  protected void postBatchInsert() throws NormalizationFailedException {
    // lookup species interaction related names - this requires neo4j so cant be done during batch inserts
    for (RelType rt : RelType.values()) {
      if (rt.isSpeciesInteraction()) {
        int counter = 0;
        try (Transaction tx = store.getNeo().beginTx();
             var iter = store.iterRelations(rt)
        ) {
          while (iter.hasNext()) {
            var rel = iter.next();
            if (!rel.hasProperty(NeoProperties.SCINAME)) {
              Node relatedUsageNode = rel.getEndNode();
              if (store.getDevNullNode().getId() != relatedUsageNode.getId()) {
                // there is a real related usage node existing, use its name
                String name = NeoProperties.getScientificNameWithAuthorFromUsage(relatedUsageNode);
                if (!name.equals(NeoProperties.NULL_NAME)) {
                  rel.setProperty(NeoProperties.SCINAME, name);
                  counter++;
                }
              }
            }
            // still no name? flag issue
            if (!rel.hasProperty(NeoProperties.SCINAME) && rel.hasProperty(NeoProperties.VERBATIM_KEY)) {
              Integer vkey = (Integer) rel.getProperty(NeoProperties.VERBATIM_KEY, null);
              LOG.debug("Missing related names for {} interaction, verbatimKey={}", rt.specInterType, vkey);
              store.addIssues(vkey, Issue.RELATED_NAME_MISSING);
            }
          }
          tx.success();
        }
        if (counter > 0) {
          LOG.info("Added related names for {} {} interactions", counter, rt.specInterType);
        }
      }
    }
  }

  private void insertTreatments() throws InterruptedException {
    ColdpReader coldp = (ColdpReader) reader;
    if (coldp.hasTreatments()) {
      try {
        final int datasetKey = store.getDatasetKey();
        for (Path tp : coldp.getTreatments()) {
          interruptIfCancelled("NeoInserter interrupted, exit early");
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
        NeoUsage nu = store.usages().objByID(t.getId());
        if (nu != null) {
          nu.treatment = t;
          store.usages().update(nu);
        } else {
          v.addIssue(Issue.TAXON_ID_INVALID);
        }
      } else {
        v.addIssue(Issue.UNPARSABLE_TREATMENT);
      }
    } catch (IOException | RuntimeException e) {
      LOG.warn("Failed to read treatment {}", e.getMessage(), e);
      v.addIssue(Issue.UNPARSABLE_TREATMENT);
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
      if (coldp.getCslJsonFile() != null) {
        CslJsonInserter bibIns = new CslJsonInserter(store, coldp.getCslJsonFile(), refFactory);
        bibIns.insertAll();
      }
    }
  }

  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new ColdpRelationInserter(store);
  }

}
