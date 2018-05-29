package org.col.admin.importer.acef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.base.Splitter;
import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.TermRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.col.parser.ReferenceTypeParser;
import org.col.parser.SafeParser;
import org.gbif.dwc.terms.AcefTerm;
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

      // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
      // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
      insertEntities(reader, AcefTerm.Reference,
          inter::interpretReference,
          store::put
      );

      // species
      insertEntities(reader, AcefTerm.AcceptedSpecies,
          inter::interpretAccepted,
          t -> {
            meta.incRecords(t.name.getRank());
            store.put(t);
          }
      );

      // infraspecies
      // accepted infraspecific names in ACEF have no genus or species
      // but a link to their parent species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!
      insertEntities(reader, AcefTerm.AcceptedInfraSpecificTaxa,
          inter::interpretAccepted,
          t -> {
            meta.incRecords(t.name.getRank());
            store.put(t);
          }
      );

      // synonyms
      insertEntities(reader, AcefTerm.Synonyms,
          inter::interpretSynonym,
          t -> {
            meta.incRecords(t.name.getRank());
            store.put(t);
          }
      );

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()) {
      insertTaxonEntities(reader, AcefTerm.Distribution,
          inter::interpretDistribution,
          AcefTerm.AcceptedTaxonID,
          (t, d) -> t.distributions.add(d)
      );
      insertTaxonEntities(reader, AcefTerm.CommonNames,
          inter::interpretVernacular,
          AcefTerm.AcceptedTaxonID,
          (t, vn) -> t.vernacularNames.add(vn)
      );
      reader.stream(AcefTerm.NameReferencesLinks).forEach(this::addReferenceLink);

    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }

  @Override
  protected NeoDb.NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, inter);
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
    String refTypeRaw = emptyToNull(rec.get(AcefTerm.ReferenceType)); // NomRef, TaxAccRef, ComNameRef
    ReferenceTypeParser.ReferenceType refType = SafeParser.parse(ReferenceTypeParser.PARSER, refTypeRaw).orNull();

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

      } else if (refType == null) {
        LOG.debug("Unknown reference type {} used in NameReferencesLinks line {}", refTypeRaw, rec.getLine());
        ref.addIssue(Issue.REFTYPE_INVALID);
        store.put(ref);
        t.taxon.addIssue(Issue.REFTYPE_INVALID);
      } else {
        switch (refType) {
          case NomRef:
            t.name.setPublishedInKey(ref.getKey());
            // we extract the page from CSL and also store it in the name
            // No deduplication of refs happening
            t.name.setPublishedInPage(ref.getCsl().getPage());
            break;
          case TaxAccRef:
            t.bibliography.add(ref.getKey());
            break;
          case ComNameRef:
            // ignore here, we should see this again when parsing common names
            break;
        }
      }
      store.update(t);
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
