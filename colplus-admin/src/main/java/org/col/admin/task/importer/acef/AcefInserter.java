package org.col.admin.task.importer.acef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Splitter;
import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.emptyToNull;

/**
 *
 */
public class AcefInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private AcefReader reader;
  private AcefInterpreter inter;
  final AtomicInteger counter = new AtomicInteger(0);

  public AcefInserter(NeoDb store, Path folder, ReferenceFactory refFactory) {
    super(folder, store, refFactory);
  }

  private void initReader() {
    if (reader == null) {
      try {
        reader = AcefReader.from(folder);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Inserts ACEF data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new AcefInterpreter(store.getDataset(), meta, store, refFactory);

      insertReferences();

      // species
      reader.stream(AcefTerm.AcceptedSpecies).forEach(this::insertTaxon);
      LOG.info("Inserted {} species names", counter.get());

      // infraspecies
      // accepted infraspecific names in ACEF have no genus or species
      // but a link to their parent species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!
      counter.set(0);
      reader.stream(AcefTerm.AcceptedInfraSpecificTaxa).forEach(this::insertTaxon);
      LOG.info("Inserted {} infraspecific names", counter.get());

      // synonyms
      counter.set(0);
      reader.stream(AcefTerm.Synonyms).forEach(this::insertSynonym);
      LOG.info("Inserted {} synonym names", counter.get());

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      reader.stream(AcefTerm.Distribution).forEach(this::addDistribution);
      reader.stream(AcefTerm.CommonNames).forEach(this::addVernacular);
      reader.stream(AcefTerm.NameReferencesLinks).forEach(this::addReferenceLink);

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, inter);
  }

  private void addDistribution(TermRecord rec) {
    lookupTaxon(AcefTerm.AcceptedTaxonID, rec).ifPresent(t -> {
      inter.interpretDistribution(t, rec);
      store.update(t);
    });
  }

  private void addVernacular(TermRecord rec) {
    lookupTaxon(AcefTerm.AcceptedTaxonID, rec).ifPresent(t -> {
      inter.interpretVernacular(t, rec);
      store.update(t);
    });
  }

  private void insertTaxon(TermRecord rec) {
    insertTaxonAndName(AcefTerm.AcceptedTaxonID, false, rec);
  }

  private void insertSynonym(TermRecord rec) {
    insertTaxonAndName(AcefTerm.ID, true, rec);
  }

  private void insertTaxonAndName(Term idTerm, boolean synonym, TermRecord rec) {
    store.put(rec);
    NeoTaxon t = inter.interpretTaxon(idTerm, rec, synonym);
    store.put(t);
    counter.incrementAndGet();
    meta.incRecords(t.name.getRank());
  }

  /**
   * This inserts the plain references from the Reference file with no links to names, taxa or distributions.
   * Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
   */
  private void insertReferences() {
    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(AcefTerm.Reference).forEach(rec -> {
      store.put(rec);
      Reference r = refFactory.fromACEF(
          rec.get(AcefTerm.ReferenceID),
          rec.get(AcefTerm.ReferenceType),
          rec.get(AcefTerm.Author),
          rec.get(AcefTerm.Year),
          rec.get(AcefTerm.Title),
          rec.get(AcefTerm.Details)
      );
      r.setVerbatimKey(rec.getKey());
      store.put(r);
      counter.incrementAndGet();
    });
    LOG.info("Inserted {} references", counter.get());
  }

  /**
   * Inserts the NameReferecesLinks table from ACEF by looking up both the taxonID and the ReferenceID
   * ComNameRef references are linked from the individual common name already, we only process name and taxon references here
   *
   * A name should only have one reference - the publishedIn one.
   * A taxon can have multiple and are treated as the bibliography extension in dwc.
   *
   * As all references and names must be indexed in the store to establish the relations
   * we run this in the relation inserter
   */
  private void addReferenceLink(TermRecord rec) {
    String taxonID = emptyToNull(rec.get(AcefTerm.ID));
    String referenceID = emptyToNull(rec.get(AcefTerm.ReferenceID));
    String refType = emptyToNull(rec.get(AcefTerm.ReferenceType)); // NomRef, TaxAccRef, ComNameRef

    if (refType != null) {
      // lookup NeoTaxon and reference
      NeoTaxon t = store.getByID(taxonID);
      Reference ref = store.refById(referenceID);
      if (t == null) {
        if (ref != null) {
          LOG.debug("taxonID {} from NameReferencesLinks line {} not existing", taxonID, rec.getLine());
          ref.addIssue(Issue.TAXON_ID_INVALID);
          store.put(ref);
        } else {
          LOG.info("referenceID {} and taxonID {} from NameReferencesLinks line {} both not existing", referenceID, taxonID, rec.getLine());
        }

      } else {
        if (ref == null) {
          LOG.debug("referenceID {} from NameReferencesLinks line {} not existing", referenceID, rec.getLine());
          t.addIssue(Issue.REFERENCE_ID_INVALID);

        } else {
          //TODO: better parsing needed? Use enum???
          if (refType.equalsIgnoreCase("NomRef")) {
            t.name.setPublishedInKey(ref.getKey());
            // we extract the page from CSL and also store it in the name
            // No deduplication of refs happening
            t.name.setPublishedInPage(ref.getCsl().getPage());

          } else if (refType.equalsIgnoreCase("TaxAccRef")) {
            t.bibliography.add(ref.getKey());

          } else if (refType.equalsIgnoreCase("ComNameRef")) {
            // ignore here, we should see this again when parsing common names

          } else {
            // unkown type
            LOG.debug("Unknown reference type {} used in NameReferencesLinks line {}", refType, rec.getLine());
          }
        }
        store.update(t);
      }
    }
  }


  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    Dataset d = null;
    initReader();
    Optional<TermRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      TermRecord dr = metadata.get();
      d = new Dataset();
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setHomepage(dr.getURI(AcefTerm.HomeURL));
      d.setDataFormat(DataFormat.ACEF);
    }
    return Optional.ofNullable(d);
  }

}
