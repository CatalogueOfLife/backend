package life.catalogue.importer.coldp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.undercouch.citeproc.bibtex.BibTeXConverter;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.TreatmentFormatParser;
import org.apache.commons.io.FilenameUtils;
import org.gbif.dwc.terms.BibTexTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.jbibtex.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class ColdpInserter extends NeoCsvInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);
  static final Term CSLJSON_CLASS_TERM = new UnknownTerm(URI.create("http://citationstyles.org/CSL-JSON"), "CSL-JSON", true);

  private ColdpInterpreter inter;

  public ColdpInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, ColdpReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException {
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
        (t, d) -> t.media.add(d)
    );
    insertTaxonEntities(reader, ColdpTerm.VernacularName,
        inter::interpretVernacular,
        ColdpTerm.taxonID,
        (t, d) -> t.vernacularNames.add(d)
    );
    insertTaxonEntities(reader, ColdpTerm.SpeciesEstimate,
      inter::interpretEstimate,
      ColdpTerm.taxonID,
      (t, d) -> t.estimates.add(d)
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
              String name = NeoProperties.getScientificNameWithAuthorFromUsage(rel.getEndNode());
              if (name.equals(NeoProperties.NULL_NAME)) {
                String vkey = (String) rel.getProperty(NeoProperties.VERBATIM_KEY, null);
                LOG.warn("Missing related names for {} interaction, verbatimKey={}", rt.specInterType, vkey);
              } else {
                rel.setProperty(NeoProperties.SCINAME, name);
                counter++;
              }
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

  private void insertTreatments(){
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

  private void insertExtendedReferences() {
    ColdpReader coldp = (ColdpReader) reader;
    if (coldp.hasExtendedReferences()) {
      final int datasetKey = store.getDatasetKey();
      if (coldp.getBibtexFile() != null) {
        insertBibTex(datasetKey, coldp.getBibtexFile());
      }
      if (coldp.getCslJsonFile() != null) {
        insertCslJson(datasetKey, coldp.getCslJsonFile());
      }
    }
  }
  
  private Term bibTexTerm(String name) {
    return new BibTexTerm(name.trim());
  }
  
  private void insertBibTex(final int datasetKey, File f) {
    try {
      InputStream is = new FileInputStream(f);
      BibTeXConverter bc = new BibTeXConverter();
      BibTeXDatabase db = bc.loadDatabase(is);
      bc.toItemData(db).forEach((id, cslItem) -> {
        BibTeXEntry bib = db.getEntries().get(new Key(id));
        VerbatimRecord v = new VerbatimRecord();
        v.setType(BibTexTerm.CLASS_TERM);
        v.setDatasetKey(datasetKey);
        v.setFile(f.getName());
        for (Map.Entry<Key, Value> field : bib.getFields().entrySet()) {
          v.put(bibTexTerm(field.getKey().getValue()), field.getValue().toUserString());
        }
        store.put(v);
  
        try {
          CslData csl = CslDataConverter.toCslData(cslItem);
          csl.setId(id); // maybe superfluous but safe
          Reference ref = ReferenceFactory.fromCsl(datasetKey, csl);
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);

        } catch (RuntimeException e) {
          LOG.warn("Failed to convert CslDataConverter into Reference: {}", e.getMessage(), e);
          v.addIssue(Issue.UNPARSABLE_REFERENCE);
          store.put(v);
        }
      });
    } catch (IOException | ParseException e) {
      LOG.error("Unable to read BibTex file {}", f, e);
    }
  }
  
  private void insertCslJson(int datasetKey, File f) {
    try {
  
      JsonNode jsonNode = ApiModule.MAPPER.readTree(f);
      if (!jsonNode.isArray()) {
        LOG.error("Unable to read CSL-JSON file {}. Array required", f);
        return;
      }
      
      for (JsonNode jn : jsonNode) {
        VerbatimRecord v = new VerbatimRecord();
        v.setType(CSLJSON_CLASS_TERM);
        v.setDatasetKey(datasetKey);
        v.setFile(f.getName());
        store.put(v);
        
        try {
          CslData csl = ApiModule.MAPPER.treeToValue(jn, CslData.class);
          // make sure we have an ID!!!
          if (csl.getId() == null) {
            if (csl.getDOI() != null) {
              csl.setId(csl.getDOI());
            } else {
              throw new IllegalArgumentException("Missing required CSL id field");
            }
          }
          Reference ref = ReferenceFactory.fromCsl(datasetKey, csl);
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);
          
        } catch (JsonProcessingException | RuntimeException e) {
          LOG.warn("Failed to convert verbatim csl json {} into Reference: {}", v.getId(), e.getMessage(), e);
          v.addIssue(Issue.UNPARSABLE_REFERENCE);
          store.put(v);
        }
      }
    } catch (IOException e) {
      LOG.error("Unable to read CSL-JSON file {}", f, e);
    }
  }

  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new ColdpRelationInserter(store);
  }

  /**
   * Reads the dataset metadata.yaml and puts it into the store
   */
  @Override
  public Optional<DatasetWithSettings> readMetadata() {
    return MetadataParser.readMetadata(folder);
  }
  
}
