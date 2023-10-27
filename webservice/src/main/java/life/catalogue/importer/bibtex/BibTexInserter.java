package life.catalogue.importer.bibtex;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NeoDb;

import org.gbif.dwc.terms.BibTexTerm;
import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbibtex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.undercouch.citeproc.bibtex.BibTeXConverter;

/**
 *
 */
public class BibTexInserter {

  private static final Logger LOG = LoggerFactory.getLogger(BibTexInserter.class);
  private final int datasetKey;
  private final NeoDb store;
  private final File bibtexFile;
  private final ReferenceFactory refFactory;
  private final AtomicInteger counter = new AtomicInteger();

  public BibTexInserter(NeoDb store, File bibtexFile, ReferenceFactory refFactory) {
    this.bibtexFile = bibtexFile;
    this.store = store;
    this.refFactory = refFactory;
    this.datasetKey = store.getDatasetKey();
  }

  public int getCounter() {
    return counter.get();
  }

  /**
   * Inserts Bibtex references into the store.
   * @return number of references processed
   */
  public int insertAll() throws InterruptedException {
    try (InputStream is = new FileInputStream(bibtexFile)){
      BibTeXConverter bc = new BibTeXConverter();
      BibTeXDatabase db = bc.loadDatabase(is);
      bc.toItemData(db).forEach((id, cslItem) -> {
        BibTeXEntry bib = db.getEntries().get(new Key(id));
        VerbatimRecord v = new VerbatimRecord();
        v.setType(BibTexTerm.CLASS_TERM);
        v.setDatasetKey(datasetKey);
        v.setFile(bibtexFile.getName());
        for (Map.Entry<Key, Value> field : bib.getFields().entrySet()) {
          v.put(bibTexTerm(field.getKey().getValue()), field.getValue().toUserString());
        }
        store.put(v);
        counter.incrementAndGet();

        try {
          CslData csl = CslDataConverter.toCslData(cslItem);
          csl.setId(id); // maybe superfluous but safe
          Reference ref = refFactory.fromCsl(datasetKey, csl, v);
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);

        } catch (RuntimeException e) {
          LOG.warn("Failed to convert BibTex into Reference: {}", e.getMessage(), e);
          v.addIssue(Issue.UNPARSABLE_REFERENCE);
          store.put(v);
        }
      });
      LOG.info("Inserted {} BibTex references", counter);

    } catch (IOException | ParseException e) {
      LOG.error("Unable to read BibTex file {}", bibtexFile, e);
    }
    return counter.get();
  }

  private Term bibTexTerm(String name) {
    return new BibTexTerm(name.trim());
  }
}
