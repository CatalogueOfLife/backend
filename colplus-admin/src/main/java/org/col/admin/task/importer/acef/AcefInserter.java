package org.col.admin.task.importer.acef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.col.admin.task.importer.NeoInserter;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.TermRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Splitter;

import static com.google.common.base.Strings.emptyToNull;

/**
 *
 */
public class AcefInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private AcefReader reader;
  private AcefInterpreter inter;

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
      insertTaxaAndNames();

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      reader.stream(AcefTerm.Distribution).forEach(this::addVerbatimRecord);
      reader.stream(AcefTerm.CommonNames).forEach(this::addVerbatimRecord);

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
    // link references to taxa, names and vernaculars
    insertReferenceLinks();
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, inter);
  }

  private void addVerbatimRecord(TermRecord rec) {
    super.addVerbatimRecord(AcefTerm.AcceptedTaxonID, rec);
  }

  private void insertTaxaAndNames() {
    final AtomicInteger counter = new AtomicInteger(0);
    // species
    reader.stream(AcefTerm.AcceptedSpecies).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      counter.incrementAndGet();
      meta.incRecords(t.name.getRank());
    });
    LOG.info("Inserted {} species names", counter.get());

    // infraspecies
    counter.set(0);
    reader.stream(AcefTerm.AcceptedInfraSpecificTaxa).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.AcceptedTaxonID), rec);
      // accepted infraspecific names in ACEF have no genus or species but a link to their parent
      // species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!
      NeoTaxon t = inter.interpretTaxon(v, false);
      store.put(t);
      counter.incrementAndGet();
      meta.incRecords(t.name.getRank());
    });
    LOG.info("Inserted {} infraspecific names", counter.get());

    // synonyms
    counter.set(0);
    reader.stream(AcefTerm.Synonyms).forEach(rec -> {
      UnescapedVerbatimRecord v = build(rec.get(AcefTerm.ID), rec);
      NeoTaxon t = inter.interpretTaxon(v, true);
      store.put(t);
      counter.incrementAndGet();
      meta.incRecords(t.name.getRank());
    });
    LOG.info("Inserted {} synonym names", counter.get());
  }

  /**
   * This inserts the plain references from the Reference file with no links to names, taxa or distributions.
   * Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
   */
  private void insertReferences() {
    final AtomicInteger counter = new AtomicInteger(0);
    reader.stream(AcefTerm.Reference).forEach(rec -> {
      store.put(refFactory.fromACEF(
          emptyToNull(rec.get(AcefTerm.ReferenceID)),
          emptyToNull(rec.get(AcefTerm.Author)),
          emptyToNull(rec.get(AcefTerm.Title)),
          emptyToNull(rec.get(AcefTerm.Year)),
          emptyToNull(rec.get(AcefTerm.Source)),
          emptyToNull(rec.get(AcefTerm.ReferenceType)),
          emptyToNull(rec.get(AcefTerm.Details))
      ));
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
  private void insertReferenceLinks() {
    try (Transaction tx = store.getNeo().beginTx()) {
      reader.stream(AcefTerm.NameReferencesLinks).forEach(rec -> {
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
                t.bibliography.add(ref);

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
      });
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
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
