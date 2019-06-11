package org.col.importer.acef;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;
import org.col.api.model.Dataset;
import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.col.common.date.FuzzyDate;
import org.col.img.ImageService;
import org.col.importer.NeoInserter;
import org.col.importer.NormalizationFailedException;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NodeBatchProcessor;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.reference.ReferenceFactory;
import org.col.parser.*;
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
  
  private AcefInterpreter inter;
  
  public AcefInserter(NeoDb store, Path folder, ReferenceFactory refFactory, ImageService imgService) throws IOException {
    super(folder, AcefReader.from(folder), store, refFactory, imgService);
  }
  
  /**
   * Inserts ACEF data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  protected void batchInsert() throws NormalizationFailedException {
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
          u -> store.createNameAndUsage(u) != null
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
  protected void postBatchInsert() throws NormalizationFailedException {
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
    // we default to TaxAccRef, see https://github.com/Sp2000/colplus-repo/issues/33#issuecomment-500610124
    ReferenceTypeParser.ReferenceType refType = SafeParser.parse(ReferenceTypeParser.PARSER, refTypeRaw)
        .orElse(ReferenceTypeParser.ReferenceType.TaxAccRef, Issue.REFTYPE_INVALID, rec);
    
    // lookup NeoTaxon and reference
    NeoUsage u = store.usages().objByID(taxonID);
    Reference ref = store.refById(referenceID);
    Set<Issue> issues = EnumSet.noneOf(Issue.class);
    if (u != null && ref != null) {
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
  public Optional<Dataset> readMetadata() {
    Dataset d = null;
    Optional<VerbatimRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      VerbatimMeta dr = new VerbatimMeta(metadata.get());
      d = new Dataset();
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setAlias(dr.get(AcefTerm.DatabaseShortName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setGroup(dr.get(AcefTerm.GroupNameInEnglish));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setReleased(dr.getDate(AcefTerm.ReleaseDate));
      d.setCompleteness(dr.getInt(AcefTerm.Completeness));
      d.setConfidence(dr.getInt(AcefTerm.Confidence));
      // TODO: consume local logo file
      d.setLogo(dr.getURI(AcefTerm.LogoFileName));
      d.setWebsite(dr.getURI(AcefTerm.HomeURL));
      d.setType(dr.get(AcefTerm.Coverage, DatasetTypeParser.PARSER));
      d.setContact(dr.get(AcefTerm.ContactPerson));
      d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
      d.setOrganisations(dr.get(AcefTerm.Organisation, COMMA_SPLITTER));
      d.setDataFormat(DataFormat.ACEF);
    }
    return Optional.ofNullable(d);
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
  
    public LocalDate getDate(Term term) {
      Optional<FuzzyDate> opt = SafeParser.parse(DateParser.PARSER, v.get(term)).getOptional();
      return opt.map(FuzzyDate::toLocalDate).orElse(null);
    }
  }
  
}
