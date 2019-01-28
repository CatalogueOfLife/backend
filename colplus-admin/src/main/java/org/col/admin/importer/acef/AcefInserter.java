package org.col.admin.importer.acef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Splitter;
import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.reference.ReferenceFactory;
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
 *
 */
public class AcefInserter extends NeoInserter {
  
  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
  
  private AcefInterpreter inter;
  
  public AcefInserter(NeoDb store, Path folder, ReferenceFactory refFactory) throws IOException {
    super(folder, AcefReader.from(folder), store, refFactory);
  }
  
  /**
   * Inserts ACEF data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      inter = new AcefInterpreter(store.getDataset(), reader.getMappingFlags(), refFactory, store);

      // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
      // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
      insertEntities(reader, AcefTerm.Reference,
          inter::interpretReference,
          store::create
      );
      
      // species
      insertEntities(reader, AcefTerm.AcceptedSpecies,
          inter::interpretSpecies,
          store::createNameAndUsage
      );
      
      // infraspecies
      // accepted infraspecific names in ACEF have no genus or species
      // but a link to their parent species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!
      insertEntities(reader, AcefTerm.AcceptedInfraSpecificTaxa,
          inter::interpretInfraspecies,
          store::createNameAndUsage
      );
      
      // synonyms
      insertEntities(reader, AcefTerm.Synonyms,
          inter::interpretSynonym,
          store::createNameAndUsage
      );
  
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
  
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }
  
  @Override
  public void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()){
      reader.stream(AcefTerm.NameReferencesLinks).forEach(this::addReferenceLink);
      tx.success();
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  }
  
  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new AcefRelationInserter(store, inter);
  }
  
  /**
   * Inserts the NameReferecesLinks table from ACEF by looking up both the taxonID and the ReferenceID
   * ComNameRef references are linked from the individual common name already, we only process name and taxon references here
   * <p>
   * A name should only have one reference - the publishedIn one.
   * A taxon can have multiple and are treated as the bibliography extension in dwc.
   * <p>
   * As all references and names must be indexed in the store to establish the relations
   * we run this in the relation inserter
   */
  private void addReferenceLink(VerbatimRecord rec) {
    String taxonID = emptyToNull(rec.get(AcefTerm.ID));
    String referenceID = emptyToNull(rec.get(AcefTerm.ReferenceID));
    String refTypeRaw = emptyToNull(rec.get(AcefTerm.ReferenceType)); // NomRef, TaxAccRef, ComNameRef
    ReferenceTypeParser.ReferenceType refType = SafeParser.parse(ReferenceTypeParser.PARSER, refTypeRaw).orNull();
    
    // lookup NeoTaxon and reference
    NeoUsage u = store.usages().objByID(taxonID);
    Reference ref = store.refById(referenceID);
    Set<Issue> issues = EnumSet.noneOf(Issue.class);
    if (u != null && ref != null && refType != null) {
      switch (refType) {
        case NomRef:
          NeoName nn = store.nameByUsage(u.node);
          if (nn.name.getPublishedInId() != null) {
            rec.addIssue(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
          }
          nn.name.setPublishedInId(ref.getId());
          // we extract the page from CSL and also store it in the name
          // No deduplication of refs happening
          nn.name.setPublishedInPage(ref.getCsl().getPage());
          store.names().update(nn);
          break;
        case TaxAccRef:
          u.bibliography.add(ref.getId());
          store.usages().update(u);
          break;
        case ComNameRef:
          // ignore here, we should see this again when parsing common names
          break;
      }
    } else {
      if (u == null) {
        issues.add(Issue.TAXON_ID_INVALID);
      }
      if (ref == null) {
        issues.add(Issue.REFERENCE_ID_INVALID);
      }
      if (refType == null) {
        issues.add(Issue.REFTYPE_INVALID);
      }
    }
    // persist new issue?
    if (!issues.isEmpty()) {
      rec.addIssues(issues);
      store.put(rec);
    }
  }
  
  
  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    Dataset d = null;
    Optional<VerbatimRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      VerbatimRecord dr = metadata.get();
      d = new Dataset();
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setWebsite(dr.getURI(AcefTerm.HomeURL));
      d.setDataFormat(DataFormat.ACEF);
    }
    return Optional.ofNullable(d);
  }
  
}
