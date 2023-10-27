package life.catalogue.importer.csljson;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.neo.NeoDb;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.gbif.dwc.terms.UnknownTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 *
 */
public class CslJsonInserter {
  private static final Term CSLJSON_CLASS_TERM = new UnknownTerm(URI.create("http://citationstyles.org/CSL-JSON"), "csl", "CSL-JSON", true);
  private static final Logger LOG = LoggerFactory.getLogger(CslJsonInserter.class);
  private final int datasetKey;
  private final NeoDb store;
  private final File cslJsonFile;
  private final ReferenceFactory refFactory;
  private final AtomicInteger counter = new AtomicInteger();

  public CslJsonInserter(NeoDb store, File cslJsonFile, ReferenceFactory refFactory) {
    this.cslJsonFile = cslJsonFile;
    this.store = store;
    this.refFactory = refFactory;
    this.datasetKey = store.getDatasetKey();
  }

  public int getCounter() {
    return counter.get();
  }

  /**
   * Inserts Bibtex references into the store
   */
  public int insertAll() throws InterruptedException {
    try {
      JsonNode jsonNode = ApiModule.MAPPER.readTree(cslJsonFile);
      if (!jsonNode.isArray()) {
        LOG.error("Unable to read CSL-JSON file {}. Array required", cslJsonFile);
        return 0;
      }

      for (JsonNode jn : jsonNode) {
        VerbatimRecord v = new VerbatimRecord();
        v.setType(CSLJSON_CLASS_TERM);
        v.setDatasetKey(datasetKey);
        v.setFile(cslJsonFile.getName());
        store.put(v);
        counter.incrementAndGet();

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
          Reference ref = refFactory.fromCsl(datasetKey, csl, v);
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);

        } catch (JsonProcessingException | RuntimeException e) {
          LOG.warn("Failed to convert verbatim csl json {} into Reference: {}", v.getId(), e.getMessage(), e);
          v.addIssue(Issue.UNPARSABLE_REFERENCE);
          store.put(v);
        }
      }
    } catch (IOException e) {
      LOG.error("Unable to read CSL-JSON file {}", cslJsonFile, e);
    }
    return counter.get();
  }

}
