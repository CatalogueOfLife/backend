package org.col.admin.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.base.Splitter;
import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.datapackage.ColTerm;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
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
 * Main inserter for the ColDP entities in correct order to resolve some relations
 *
 */
public class ColDPInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColDPInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private ColDPReader reader;
  private ColDPInterpreter inter;

  public ColDPInserter(NeoDb store, Path folder, ReferenceFactory refFactory) {
    super(folder, store, refFactory);
  }

  private void initReader() {
    if (reader == null) {
      try {
        reader = ColDPReader.from(folder);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Inserts CoL data from a source folder into the normalizer store.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new ColDPInterpreter(store.getDataset(), meta, refFactory);

      insertEntities(reader, ColTerm.Reference,
          inter::interpretReference,
          store::put
      );

      insertEntities(reader, ColTerm.Name,
          inter::interpretName,
          store::put
      );
      
      //insertEntities(reader, ColTerm.NameRel,
      //    inter::interpretAccepted,
      //    store::put
      //);
      
      insertEntities(reader, ColTerm.Taxon,
          inter::interpretTaxon,
          store::put
      );

      insertEntities(reader, ColTerm.Synonym,
          inter::interpretSynonym,
          store::put
      );
  
      //TODO:
      insertEntities(reader, ColTerm.Description,
          inter::interpretSynonym,
          store::put
      );
      insertEntities(reader, ColTerm.Distribution,
          inter::interpretSynonym,
          store::put
      );
      insertEntities(reader, ColTerm.VernacularName,
          inter::interpretSynonym,
          store::put
      );
      insertEntities(reader, ColTerm.Media,
          inter::interpretSynonym,
          store::put
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
  protected NodeBatchProcessor relationProcessor() {
    return new ColDPRelationInserter(store, inter);
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
  private void addReferenceLink(VerbatimRecord rec) {
    String taxonID = emptyToNull(rec.get(AcefTerm.ID));
    String referenceID = emptyToNull(rec.get(AcefTerm.ReferenceID));
    String refTypeRaw = emptyToNull(rec.get(AcefTerm.ReferenceType)); // NomRef, TaxAccRef, ComNameRef
    ReferenceTypeParser.ReferenceType refType = SafeParser.parse(ReferenceTypeParser.PARSER, refTypeRaw).orNull();

    // lookup NeoTaxon and reference
    NeoTaxon t = store.getByID(taxonID);
    Reference ref = store.refById(referenceID);
    Issue issue = null;
    if (t == null) {
      if (ref != null) {
        LOG.debug("taxonID {} from NameReferencesLinks line {} not existing", taxonID, rec.getLine());
        issue = Issue.TAXON_ID_INVALID;
        store.put(ref);
      } else {
        LOG.info("referenceID {} and taxonID {} from NameReferencesLinks line {} both not existing", referenceID, taxonID, rec.getLine());
      }

    } else {
      if (ref == null) {
        LOG.debug("referenceID {} from NameReferencesLinks line {} not existing", referenceID, rec.getLine());
        issue = Issue.REFERENCE_ID_INVALID;

      } else if (refType == null) {
        LOG.debug("Unknown reference type {} used in NameReferencesLinks line {}", refTypeRaw, rec.getLine());
        issue = Issue.REFTYPE_INVALID;
        store.put(ref);
      } else {
        switch (refType) {
          case NomRef:
            t.name.setPublishedInId(ref.getId());
            // we extract the page from CSL and also store it in the name
            // No deduplication of refs happening
            t.name.setPublishedInPage(ref.getCsl().getPage());
            break;
          case TaxAccRef:
            t.bibliography.add(ref.getId());
            break;
          case ComNameRef:
            // ignore here, we should see this again when parsing common names
            break;
        }
      }
      store.update(t);
    }
    // persist new issue?
    if (issue != null) {
      rec.addIssue(issue);
      store.put(rec);
    }
  }


  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    Dataset d = null;
    initReader();
    Optional<VerbatimRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      VerbatimRecord dr = metadata.get();
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
