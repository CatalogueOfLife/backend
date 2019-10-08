package org.col.importer.coldp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.undercouch.citeproc.bibtex.BibTeXConverter;
import org.col.api.datapackage.ColdpTerm;
import org.col.api.jackson.ApiModule;
import org.col.api.model.CslData;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.common.csl.CslDataConverter;
import org.col.img.ImageService;
import org.col.importer.NeoInserter;
import org.col.importer.NormalizationFailedException;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NodeBatchProcessor;
import org.col.importer.reference.ReferenceFactory;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.jbibtex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);
  private static final String BIBTEX_NS = "http://bibtex.org/";
  static final Term BIBTEX_CLASS_TERM = new UnknownTerm(URI.create(BIBTEX_NS), "BibTeX", true);
  static final Term CSLJSON_CLASS_TERM = new UnknownTerm(URI.create("http://citationstyles.org"), "CSL-JSON", true);
  
  private ColdpInterpreter inter;

  public ColdpInserter(NeoDb store, Path folder, ReferenceFactory refFactory, ImageService imgService) throws IOException {
    super(folder, ColdpReader.from(folder), store, refFactory, imgService);
  }
  
  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException {
    try {
      inter = new ColdpInterpreter(store.getDataset(), reader.getMappingFlags(), refFactory, store);

      // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
      // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
      insertEntities(reader, ColdpTerm.Reference,
          inter::interpretReference,
          store::create
      );
  
      // insert CSL-JSON references
      // insert BibTex references
      insertExtendedReferences();
      
      // name & relations
      insertEntities(reader, ColdpTerm.Name,
          inter::interpretName,
          n -> store.names().create(n) != null
      );
      insertNameRelations(reader, ColdpTerm.NameRel,
          inter::interpretNameRelations,
          ColdpTerm.nameID,
          ColdpTerm.relatedNameID
      );

      // taxa
      insertEntities(reader, ColdpTerm.Taxon,
          inter::interpretTaxon,
          t -> store.usages().create(t) != null
      );
      
      // synonyms
      insertEntities(reader, ColdpTerm.Synonym,
          inter::interpretSynonym,
          s -> store.usages().create(s) != null
      );
  
      // supplementary
      insertTaxonEntities(reader, ColdpTerm.Description,
          inter::interpretDescription,
          ColdpTerm.taxonID,
          (t, d) -> t.descriptions.add(d)
      );
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
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ColDP files", e);
    }
  }
  
  private void insertExtendedReferences() {
    ColdpReader coldp = (ColdpReader) reader;
    if (coldp.hasExtendedReferences()) {
      final int datasetKey = store.getDataset().getKey();
      if (coldp.getBibtexFile() != null) {
        insertBibTex(datasetKey, coldp.getBibtexFile());
      }
      if (coldp.getCslJsonFile() != null) {
        insertCslJson(datasetKey, coldp.getCslJsonFile());
      }
    }
  }
  
  private Term bibTexTerm(String name) {
    return TermFactory.instance().findPropertyTerm(BIBTEX_NS + name);
  }
  
  private void insertBibTex(final int datasetKey, File f) {
    try {
      InputStream is = new FileInputStream(f);
      BibTeXConverter bc = new BibTeXConverter();
      BibTeXDatabase db = bc.loadDatabase(is);
      bc.toItemData(db).forEach((id, cslItem) -> {
        BibTeXEntry bib = db.getEntries().get(new Key(id));
        VerbatimRecord v = new VerbatimRecord();
        v.setType(BIBTEX_CLASS_TERM);
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
          store.create(ref);

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
          store.create(ref);
          
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
    return new ColdpRelationInserter(store, inter);
  }

  /**
   * Reads the dataset metadata.yaml and puts it into the store
   */
  @Override
  public Optional<Dataset> readMetadata() {
    return MetadataParser.readMetadata(super.folder);
  }
  
}
