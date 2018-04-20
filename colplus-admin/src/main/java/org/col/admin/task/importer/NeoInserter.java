package org.col.admin.task.importer;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 *
 */
public abstract class NeoInserter {
  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);

  protected final NeoDb store;
  protected final Path folder;
  protected final InsertMetadata meta = new InsertMetadata();
  protected final ReferenceFactory refFactory;

  public NeoInserter(Path folder, NeoDb store, ReferenceFactory refFactory) {
    this.folder = folder;
    this.store = store;
    this.refFactory = refFactory;
  }

  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");

  protected static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(CharMatcher.javaIsoControl().trimAndCollapseFrom(x, ' ').trim());
  }

  protected UnescapedVerbatimRecord build (String id, TermRecord core) {
    UnescapedVerbatimRecord v = UnescapedVerbatimRecord.create();
    Preconditions.checkNotNull(id, "ID required");
    v.setId(id);

    // set core terms
    core.forEach((term, value) -> {
      String val = clean(value);
      if (val != null) {
        v.setTerm(term, val);
      }
    });

    return v;
  }

  protected void addVerbatimRecord(Term idTerm, TermRecord rec) {
    String id = rec.get(idTerm);
    NeoTaxon t = store.getByID(id);
    if (t == null) {
      LOG.warn("Non existing {} {} found in {} record line {}, {}", idTerm.simpleName(), id, rec.getType().simpleName(), rec.getLine(), rec.getFile());

    } else if(t.verbatim == null){
      LOG.warn("No verbatim data found for {} {} in {} record {} line {}, {}", idTerm.simpleName(), id, rec.getType().simpleName(), rec.getLine(), rec.getFile());

    } else {
      t.verbatim.addExtensionRecord(rec.getType(), rec);
      store.update(t);
    }
  }

  public InsertMetadata insertAll() throws NormalizationFailedException {
    // the key will be preserved by the store
    Optional<Dataset> d = readMetadata();
    d.ifPresent(store::put);

    store.startBatchMode();
    batchInsert();
    LOG.info("Batch insert completed, {} nodes created", meta.getRecords());

    store.endBatchMode();
    LOG.info("Neo batch inserter closed, data flushed to disk", meta.getRecords());

    final int batchRec = meta.getRecords();
    insert();
    LOG.info("Regular insert completed, {} nodes created, total={}", meta.getRecords()-batchRec, meta.getRecords());

    LOG.info("Start processing explicit relations ...");
    store.process(Labels.ALL,10000, relationProcessor());

    return meta;
  }

  public abstract void batchInsert() throws NormalizationFailedException;

  public abstract void insert() throws NormalizationFailedException;

  protected abstract NeoDb.NodeBatchProcessor relationProcessor();

  protected abstract Optional<Dataset> readMetadata();

}
