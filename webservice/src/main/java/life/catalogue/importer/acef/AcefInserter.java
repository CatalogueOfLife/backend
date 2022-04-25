package life.catalogue.importer.acef;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.csv.AcefReader;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.NeoCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NodeBatchProcessor;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import static com.google.common.base.Strings.emptyToNull;

/**
 *
 */
public class AcefInserter extends NeoCsvInserter {
  private static final Map<String, List<String>> proParteAccIds = new HashMap<>();
  private static final Logger LOG = LoggerFactory.getLogger(AcefInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
  
  private AcefInterpreter inter;
  
  public AcefInserter(NeoDb store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, AcefReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts ACEF data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException {
    inter = new AcefInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
    // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
    insertEntities(reader, AcefTerm.Reference,
        inter::interpretReference,
        store.references()::create
    );

    // species
    insertEntities(reader, AcefTerm.AcceptedSpecies,
        inter::interpretSpecies,
        u -> store.createNameAndUsage(u) != null
    );

    // infraspecies
    // accepted infraspecific names in ACEF have no genus or species
    // but a link to their parent species ID.
    // so we cannot update the scientific name yet - we do this in the relation inserter instead!
    insertEntities(reader, AcefTerm.AcceptedInfraSpecificTaxa,
        inter::interpretInfraspecies,
        u -> store.createNameAndUsage(u) != null
    );

    // synonyms
    insertEntities(reader, AcefTerm.Synonyms,
        inter::interpretSynonym,
        this::createSynonymNameUsage
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
  }
  
  public boolean createSynonymNameUsage(NeoUsage u) {
    // check if we have seen the usage id before - this is legitimate for ambiguous and misapplied names
    // https://github.com/Sp2000/colplus-backend/issues/449
    NeoUsage pre = store.usages().objByID(u.getId());
    if (pre != null && pre.usage.getStatus().isSynonym()) {
      //TODO: create second relation or fail if accepted is the same!
      // we have not yet established neo relations so we need to check the verbatim data here

      VerbatimRecord v = store.getVerbatim(u.usage.getVerbatimKey());
      final String aID = v.getRaw(AcefTerm.AcceptedTaxonID);
      if (StringUtils.isNotEmpty(aID)) {
        List<String> accIds = proParteAccIds.computeIfAbsent(u.getId(), k -> new ArrayList<>());
        if (accIds.isEmpty()) {
          // never been here before, so we need to also load the first, previous acceptedID from verbatim
          v = store.getVerbatim(pre.usage.getVerbatimKey());
          String acceptedTaxonID = v.getRaw(AcefTerm.AcceptedTaxonID);
          if (StringUtils.isNotEmpty(acceptedTaxonID)) {
            accIds.add(acceptedTaxonID);
          }
        }
        if (accIds.contains(aID)) {
          LOG.debug("Duplicate synonym with the same acceptedID found. Ignore");
          v.addIssue(Issue.DUPLICATE_NAME);
          v.addIssue(Issue.TAXON_ID_INVALID);
          
        } else {
          NeoUsage acc = store.usages().objByID(aID);
          if (acc == null) {
            v.addIssue(Issue.ACCEPTED_ID_INVALID);
            
          } else {
            // create synonym relations in post process
            accIds.add(aID);
            return true;
          }
        }
        return false;
      }
      
    }
    return store.createNameAndUsage(u) != null;
  }
  
  @Override
  protected void postBatchInsert() throws NormalizationFailedException {
    try (Transaction tx = store.getNeo().beginTx()){
      reader.stream(AcefTerm.NameReferencesLinks).forEach(this::addReferenceLink);
      tx.success();
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ACEF files", e);
    }
  
    LOG.info("Create additional pro parte synonyms for {} usages", proParteAccIds.size());
    try (Transaction tx = store.getNeo().beginTx()){
      for (Map.Entry<String, List<String>> syn : proParteAccIds.entrySet()) {
        if (!syn.getValue().isEmpty()) {
          NeoUsage synU = store.usages().objByID(syn.getKey());
          // remove the first which we generate normally
          syn.getValue().remove(0);
          // now create additional pro parte synonym relations for the rest
          for (String accID : syn.getValue()) {
            NeoUsage accU = store.usages().objByID(accID);
            if (accU == null) {
              //TODO: log issue
            } else {
              synU.node.createRelationshipTo(accU.node, RelType.SYNONYM_OF);
            }
          }
        }
      }
      tx.success();
    
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to insert ACEF files", e);
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
    // we default to TaxAccRef, see https://github.com/Sp2000/colplus-repo/issues/33#issuecomment-500610124
    ReferenceTypeParser.ReferenceType refType = SafeParser.parse(ReferenceTypeParser.PARSER, refTypeRaw)
                                                          .orElse(ReferenceTypeParser.ReferenceType.TaxAccRef, Issue.REFTYPE_INVALID, rec);
    
    // lookup NeoTaxon and reference
    NeoUsage u = store.usages().objByID(taxonID);
    Reference ref = store.references().get(referenceID);
    Set<Issue> issues = EnumSet.noneOf(Issue.class);
    if (u != null && ref != null) {
      switch (refType) {
        case NomRef:
          NeoName nn = store.nameByUsage(u.node);
          if (nn.getName().getPublishedInId() != null) {
            rec.addIssue(Issue.MULTIPLE_PUBLISHED_IN_REFERENCES);
          }
          nn.getName().setPublishedInId(ref.getId());
          // we extract the page from CSL and also store it in the name
          // No deduplication of refs happening
          nn.getName().setPublishedInPage(ref.getCsl().getPage());
          store.names().update(nn);
          break;
        case TaxAccRef:
          // we dont ever have bare ACEF names - inserter only creates synonyms & taxa
          ((NameUsageBase)u.usage).getReferenceIds().add(ref.getId());
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
  public Optional<DatasetWithSettings> readMetadata() {
    Optional<VerbatimRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      VerbatimMeta dr = new VerbatimMeta(metadata.get());
      DatasetWithSettings d = new DatasetWithSettings();
      d.setDataFormat(DataFormat.ACEF);
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setAlias(dr.get(AcefTerm.DatabaseShortName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setTaxonomicScope(dr.get(AcefTerm.GroupNameInEnglish));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setIssued(dr.getDate(AcefTerm.ReleaseDate));
      d.setCompleteness(dr.getInt(AcefTerm.Completeness));
      d.setConfidence(dr.getInt(AcefTerm.Confidence));
      // TODO: consume local logo file
      d.setLogo(dr.getURI(AcefTerm.LogoFileName));
      d.setUrl(dr.getURI(AcefTerm.HomeURL));
      d.setType(dr.get(AcefTerm.Coverage, DatasetTypeParser.PARSER));
      d.setContact(Agent.parse(dr.get(AcefTerm.ContactPerson)));
      d.setCreator(Agent.parse(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER)));
      d.setContributor(Agent.parse(dr.get(AcefTerm.Organisation, COMMA_SPLITTER)));
      return Optional.ofNullable(d);
    }
    // try others
    return super.readMetadata();
  }
  
  static class VerbatimMeta {
    final VerbatimRecord v;
    
    VerbatimMeta(VerbatimRecord record) {
      this.v = record;
    }
  
    public String get(Term term) {
      return v.get(term);
    }
  
    @Nullable
    public List<String> get(Term term, Splitter splitter) {
      return v.get(term, splitter);
    }
  
    public <T> T get(Term term, Parser<T> parser) {
      return SafeParser.parse(parser, v.get(term)).orNull();
    }
  
    public <T> T get(Term term, Parser<T> parser, T defaultValue) {
      return SafeParser.parse(parser, v.get(term)).orElse(defaultValue);
    }

    public URI getURI(Term term) {
      return SafeParser.parse(UriParser.PARSER, v.get(term)).orNull();
    }
  
    public Integer getInt(Term term) {
      return SafeParser.parse(IntegerParser.PARSER, v.get(term)).orNull();
    }
  
    public FuzzyDate getDate(Term term) {
      return SafeParser.parse(DateParser.PARSER, v.get(term)).orNull();
    }
  }
  
}
