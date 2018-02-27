package org.col.admin.task.importer.acef;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.ibm.icu.text.Transliterator;
import org.apache.commons.lang3.StringUtils;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.exception.InvalidNameException;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.col.parser.SafeParser.parse;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class AcefInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(AcefInterpreter.class);
  private static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();
  private static final Transliterator transLatin = Transliterator.getInstance("Any-Latin");
  private static final Transliterator transAscii = Transliterator.getInstance("Latin-ASCII");

  private final ReferenceStore refStore;

  public AcefInterpreter(InsertMetadata metadata, ReferenceStore refStore) {
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
    t.taxon.setStatus(parse(TaxonomicStatusParser.PARSER, v.getTerm(AcefTerm.Sp2000NameStatus))
        .orElse(TaxonomicStatus.ACCEPTED)
    );
    t.taxon.setAccordingTo(v.getTerm(AcefTerm.LTSSpecialist));
    t.taxon.setAccordingToDate(date(t, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setDatasetUrl(uri(t, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    t.taxon.setFossil(bool(t, Issue.IS_FOSSIL_INVALID, AcefTerm.IsFossil, AcefTerm.HasPreHolocene));
    t.taxon.setRecent(bool(t, Issue.IS_RECENT_INVALID, AcefTerm.IsRecent, AcefTerm.HasModern));
    t.taxon.setRemarks(v.getTerm(AcefTerm.AdditionalData));

    //lifezones
    String raw = t.verbatim.getTerm(AcefTerm.LifeZone);
    if (raw != null) {
      for (String lzv : MULTIVAL.split(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, t.issues);
        if (lz != null) {
          t.taxon.getLifezones().add(lz);
        }
      }
    }

    t.taxon.setSpeciesEstimate(null);
    t.taxon.setSpeciesEstimateReferenceKey(null);

    // synonym
    if (synonym) {
      t.synonym = new NeoTaxon.Synonym();
    }

    // acts
    t.acts = interpretActs(v);
    // flat classification
    t.classification = interpretClassification(v, synonym);

    return t;
  }

  void interpretVernaculars(NeoTaxon t) {
    for (TermRecord rec : t.verbatim.getExtensionRecords(AcefTerm.CommonNames)) {
      if (rec.hasTerm(AcefTerm.CommonName)) {
        VernacularName vn = new VernacularName();
        vn.setName(rec.get(AcefTerm.CommonName));
        vn.setLatin(latinName(t, vn.getName(), rec));
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(AcefTerm.Language)).orNull());
        vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.get(AcefTerm.Country)).orNull());
        addReferences(vn, rec, t.issues);
        t.vernacularNames.add(vn);
      } else {
        // vernacular names required
        t.addIssue(Issue.VERNACULAR_NAME_INVALID);
      }
    }
  }

  static String latinName(String name) {
    return transLatin.transform(name);
  }

  static String asciiName(String name) {
    return transAscii.transform(latinName(name));
  }

  private String latinName(NeoTaxon t, String name, TermRecord rec) {
    String latin = rec.get(AcefTerm.TransliteratedName);
    if (StringUtils.isBlank(latin) && !StringUtils.isBlank(name)) {
      latin = latinName(name);
      t.addIssue(Issue.VERNACULAR_NAME_TRANSLITERATED);
    }
    return latin;
  }

  void interpretDistributions(NeoTaxon t) {
    for (TermRecord rec : t.verbatim.getExtensionRecords(AcefTerm.Distribution)) {
      // require location
      if (rec.hasTerm(AcefTerm.DistributionElement)) {
        Distribution d = new Distribution();

        // which standard?
        d.setAreaStandard(parse(GazetteerParser.PARSER, rec.get(AcefTerm.StandardInUse))
            .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, t.issues)
        );

        // TODO: try to split location into several distributions...
        String loc = rec.get(AcefTerm.DistributionElement);
        if (d.getAreaStandard() == Gazetteer.TEXT) {
          d.setArea(loc);
        } else {
          // only parse area if other than text
          AreaParser.Area textArea = new AreaParser.Area(loc, Gazetteer.TEXT);
          if (loc.indexOf(':')<0) {
            loc = d.getAreaStandard().locationID(loc);
          }
          AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc)
              .orElse(textArea, Issue.DISTRIBUTION_AREA_INVALID, t.issues);
          d.setArea(area.area);
          // check if we have contradicting extracted a gazetteer
          if (area.standard != Gazetteer.TEXT && area.standard != d.getAreaStandard()) {
            LOG.info("Area standard {} found in area {} different from explicitly given standard {} for taxon {}",
                area.standard, area.area, d.getAreaStandard(), t.getTaxonID());
          }
        }

        // status
        d.setStatus(parse(DistributionStatusParser.PARSER, rec.get(AcefTerm.DistributionStatus))
                .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, t.issues)
        );
        addReferences(d, rec, t.issues);
        t.distributions.add(d);

      } else {
        t.addIssue(Issue.DISTRIBUTION_INVALID);
      }
    }
  }

  private void addReferences(Referenced obj, TermRecord v, Set<Issue> issueCollector) {
    if (v.hasTerm(AcefTerm.ReferenceID)) {
      Reference r = refStore.refById(v.get(AcefTerm.ReferenceID));
      if (r != null) {
        obj.addReferenceKey(r.getKey());
      } else {
        LOG.info("ReferenceID {} not existing but referred from {} for taxon {}",
            v.get(AcefTerm.ReferenceID), obj.getClass().getSimpleName(), v.get(AcefTerm.AcceptedTaxonID));
        issueCollector.add(Issue.REFERENCE_ID_INVALID);
      }
    }
  }

  private static LocalDate date(NeoTaxon t, Issue invalidIssue, Term term) {
    return parse(DateParser.PARSER, t.verbatim.getTerm(term)).orNull(invalidIssue, t.issues);
  }

  private static URI uri(NeoTaxon t, Issue invalidIssue, Term ... term) {
    return parse(UriParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.issues);
  }

  private static Boolean bool(NeoTaxon t, Issue invalidIssue, Term ... term) {
    return parse(BooleanParser.PARSER, t.verbatim.getFirst(term)).orNull(invalidIssue, t.issues);
  }

  private Classification interpretClassification(VerbatimRecord v, boolean isSynonym) {
    Classification cl = new Classification();
    cl.setKingdom(v.getTerm(AcefTerm.Kingdom));
    cl.setPhylum(v.getTerm(AcefTerm.Phylum));
    cl.setClass_(v.getTerm(AcefTerm.Class));
    cl.setOrder(v.getTerm(AcefTerm.Order));
    cl.setSuperfamily(v.getTerm(AcefTerm.Superfamily));
    cl.setFamily(v.getTerm(AcefTerm.Family));
    if (!isSynonym) {
      cl.setGenus(v.getTerm(AcefTerm.Genus));
      cl.setSubgenus(v.getTerm(AcefTerm.SubGenusName));
    }
    return cl;
  }

  private List<NameAct> interpretActs(VerbatimRecord v) {
    List<NameAct> acts = Lists.newArrayList();

    // publication of name
    if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
      NameAct act = new NameAct();
      act.setType(NomActType.DESCRIPTION);
      act.setReferenceKey(
          lookupReferenceTitleID(
            v.getTerm(DwcTerm.namePublishedInID),
            v.getTerm(DwcTerm.namePublishedIn)
          ).getKey()
      );
      acts.add(act);
    }
    return acts;
  }

  private Reference lookupReferenceTitleID(String id, String title) {
    // first try by id
    Reference r = refStore.refById(id);
    if (r == null && title != null) {
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
      n.updateScientificName();
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
    n.setId(v.getId());
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(Origin.SOURCE);
    n.setGenus(v.getTerm(AcefTerm.Genus));
    n.setInfragenericEpithet(v.getTerm(AcefTerm.SubGenusName));
    n.setSpecificEpithet(v.getTerm(AcefTerm.SpeciesEpithet));

    String authorship;
    if (v.hasTerm(AcefTerm.InfraSpeciesEpithet)) {
      n.setInfraspecificEpithet(v.getTerm(AcefTerm.InfraSpeciesEpithet));
      n.setRank(parse(RankParser.PARSER, v.getTerm(AcefTerm.InfraSpeciesMarker))
          .orElse(Rank.INFRASPECIFIC_NAME, Issue.RANK_INVALID, n.getIssues())
      );
      authorship = v.getTerm(AcefTerm.InfraSpeciesAuthorString);
      // accepted infraspecific names in ACEF have no genus or species but a link to their parent species ID.
      // so we cannot update the scientific name yet - we do this in the relation inserter instead!

    } else {
      n.setRank(Rank.SPECIES);
      authorship = v.getTerm(AcefTerm.AuthorString);
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
        Authorship unparsed = new Authorship();
        unparsed.getAuthors().add(authorship);
        n.setCombinationAuthorship(unparsed);
      }
    }

    return n;
  }
}
