package org.col.admin.task.importer.acef;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.exception.InvalidNameException;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.parser.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class AcefInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(AcefInterpreter.class);
  private static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();

  private final InsertMetadata meta;
  private final ReferenceStore refStore;

  public AcefInterpreter(InsertMetadata metadata, ReferenceStore refStore) {
    this.meta = metadata;
    this.refStore = refStore;
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  public NeoTaxon interpretTaxon(VerbatimRecord v, boolean synonym) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    t.name.setOrigin(Origin.SOURCE);
    // taxon
    t.taxon = new Taxon();
    t.taxon.setId(v.getId());
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setStatus(SafeParser.parse(TaxonomicStatusParser.PARSER, v.getCoreTerm(AcefTerm.Sp2000NameStatus))
        .orElse(TaxonomicStatus.ACCEPTED)
    );
    t.taxon.setAccordingTo(v.getCoreTerm(AcefTerm.LTSSpecialist));
    t.taxon.setAccordingToDate(date(v, AcefTerm.LTSDate));
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setDatasetUrl(uri(v, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    t.taxon.setFossil(null);
    t.taxon.setRecent(null);
    //t.setLifezones();
    t.taxon.setSpeciesEstimate(null);
    t.taxon.setSpeciesEstimateReferenceKey(null);
    t.taxon.setRemarks(v.getCoreTerm(AcefTerm.AdditionalData));

    // synonym
    if (synonym) {
      t.synonym = new NeoTaxon.Synonym();
    }

    // acts
    t.acts = interpretActs(v);
    // flat classification
    t.classification = interpretClassification(v);

    return t;
  }

  private static LocalDate date(VerbatimRecord v, Term term) {
    return SafeParser.parse(DateParser.PARSER, v.getCoreTerm(term)).orNull();
  }

  private static URI uri(VerbatimRecord v, Term ... term) {
    return SafeParser.parse(UriParser.PARSER, v.getFirst(term)).orNull();
  }

  private Classification interpretClassification(VerbatimRecord v) {
    Classification cl = new Classification();
    cl.setKingdom(v.getCoreTerm(AcefTerm.Kingdom));
    cl.setPhylum(v.getCoreTerm(AcefTerm.Phylum));
    cl.setClass_(v.getCoreTerm(AcefTerm.Class));
    cl.setOrder(v.getCoreTerm(AcefTerm.Order));
    cl.setSuperfamily(v.getCoreTerm(AcefTerm.Superfamily));
    cl.setFamily(v.getCoreTerm(AcefTerm.Family));
    cl.setGenus(v.getCoreTerm(AcefTerm.Genus));
    cl.setSubgenus(v.getCoreTerm(AcefTerm.SubGenusName));
    return cl;
  }

  private List<NameAct> interpretActs(VerbatimRecord v) {
    List<NameAct> acts = Lists.newArrayList();

    // publication of name
    if (v.hasCoreTerm(DwcTerm.namePublishedInID) || v.hasCoreTerm(DwcTerm.namePublishedIn)) {
      NameAct act = new NameAct();
      act.setType(NomActType.DESCRIPTION);
      act.setReferenceKey(
          lookupReferenceTitleID(
            v.getCoreTerm(DwcTerm.namePublishedInID),
            v.getCoreTerm(DwcTerm.namePublishedIn)
          ).getKey()
      );
      acts.add(act);
    }
    return acts;
  }

  private Reference lookupReferenceTitleID(String id, String title) {
    // first try by id
    Reference r = refStore.refById(id);
    if (r == null) {
      // then try by title
      r = refStore.refByTitle(title);
      if (r == null) {
        // lastly create a new reference
        r = Reference.create();
        r.setId(id);
        r.setTitle(title);
        refStore.put(r);
      }
    }
    return r;
  }

  static void updateScientificName(String id, Name n) {
    try {
      n.setScientificName(n.canonicalNameWithoutAuthorship());
      if (!n.isConsistent()) {
        n.addIssue(Issue.INCONSISTENT_NAME);
        LOG.info("Inconsistent name {}: {}", id, n.toStringComplete());
      }
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
  }

  private Name interpretName(VerbatimRecord v) {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(Origin.SOURCE);
    n.setGenus(v.getCoreTerm(AcefTerm.Genus));
    n.setInfragenericEpithet(v.getCoreTerm(AcefTerm.SubGenusName));
    n.setSpecificEpithet(v.getCoreTerm(AcefTerm.SpeciesEpithet));

    String authorship;
    if (v.hasCoreTerm(AcefTerm.InfraSpeciesEpithet)) {
      n.setInfraspecificEpithet(v.getCoreTerm(AcefTerm.InfraSpeciesEpithet));
      n.setRank(SafeParser.parse(RankParser.PARSER, v.getCoreTerm(AcefTerm.InfraSpeciesMarker))
          .orElse(Rank.INFRASPECIFIC_NAME, Issue.RANK_INVALID, n.getIssues())
      );
      authorship = v.getCoreTerm(AcefTerm.InfraSpeciesAuthorString);
      // accepted infraspecific names in ACEF have no genus or species but a link to their parent species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!

    } else {
      n.setRank(Rank.SPECIES);
      authorship = v.getCoreTerm(AcefTerm.AuthorString);
      updateScientificName(v.getId(), n);
    }

    if (!Strings.isNullOrEmpty(authorship)) {

      Optional<ParsedName> optAuthorship = NameParser.PARSER.parseAuthorship(authorship);
      if (optAuthorship.isPresent()) {
        ParsedName nAuth = optAuthorship.get();
        n.setCombinationAuthorship(nAuth.getCombinationAuthorship());
        n.setSanctioningAuthor(nAuth.getSanctioningAuthor());
        n.setBasionymAuthorship(nAuth.getBasionymAuthorship());

      } else {
        LOG.warn("Unparsable authorship {}", authorship);
        n.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
      }
    }

    return n;
  }
}
