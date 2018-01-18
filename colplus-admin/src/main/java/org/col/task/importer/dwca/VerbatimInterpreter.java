package org.col.task.importer.dwca;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import org.col.api.*;
import org.col.api.exception.InvalidNameException;
import org.col.api.vocab.*;
import org.col.task.importer.neo.model.NeoTaxon;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class VerbatimInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimInterpreter.class);
  private static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();

  private InsertMetadata insertMetadata;

  public VerbatimInterpreter(InsertMetadata insertMetadata) {
    this.insertMetadata = insertMetadata;
  }

  private static String first(VerbatimRecord v, Term... terms) {
    for (Term t : terms) {
      // verbatim data is cleaned already and all empty strings are removed from the terms map
      if (v.hasCoreTerm(t)) {
        return v.getCoreTerm(t);
      }
    }
    return null;
  }

  public NeoTaxon interpret(VerbatimRecord v, boolean useCoreIdForTaxonID) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getCoreTerm(dwc));
    }
    // add taxon in any case - we can swap stawtus of a synonym during normalization
    t.taxon = interpretTaxon(v, useCoreIdForTaxonID);
    // a synonym by status?
    // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during main normalization
    if(SafeParser.parse(SynonymStatusParser.PARSER, v.getCoreTerm(DwcTerm.taxonomicStatus)).orElse(false)) {
      t.synonym = new NeoTaxon.Synonym();
    }
    // supplementary infos
    interpretVernacularNames(t);
    interpretDistributions(t);

    return t;
  }

  private void interpretDistributions(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Distribution)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Distribution)) {
        // try to figure out an area
        if (rec.hasTerm(DwcTerm.locationID)) {
          for (String loc : MULTIVAL.split(rec.get(DwcTerm.locationID))) {
            AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull();
            if (area != null) {
              addDistribution(t, area.area, area.standard);
            } else {
              t.addIssue(Issue.DISTRIBUTION_UNPARSABLE_AREA);
            }
          }

        } else if(rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
          for (String craw : MULTIVAL.split(rec.getFirst(DwcTerm.countryCode, DwcTerm.country))) {
            Country country = SafeParser.parse(CountryParser.PARSER, craw).orNull();
            if (country != null) {
              addDistribution(t, country.getIso2LetterCode(), Gazetteer.ISO);
            } else {
              t.addIssue(Issue.DISTRIBUTION_UNPARSABLE_COUNTRY);
            }
          }

        } else if(rec.hasTerm(DwcTerm.locality)) {
          addDistribution(t, rec.get(DwcTerm.locality), Gazetteer.TEXT);

        } else {
          t.addIssue(Issue.DISTRIBUTION_INVALID);
        }
      }
    }
  }

  private void addDistribution(NeoTaxon t, String area, Gazetteer standard) {
    //TODO: parse references!!!
    Distribution d = new Distribution();
    d.setArea(area);
    d.setAreaStandard(standard);
    //TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    t.distributions.add(d);
  }

  private void interpretVernacularNames(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.VernacularName)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.VernacularName)) {
        if (rec.hasTerm(DwcTerm.vernacularName)) {
          //TODO: parse references!!!
          VernacularName vn = new VernacularName();
          vn.setName(rec.get(DwcTerm.vernacularName));
          vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(DcTerm.language)).orNull());
          vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(DwcTerm.countryCode, DwcTerm.country)).orNull());
          t.vernacularNames.add(vn);
        } else {
          // vernacular names required
          t.addIssue(Issue.VERNACULAR_NAME_INVALID);
        }
      }
    }
  }

  private Taxon interpretTaxon(VerbatimRecord v, boolean useCoreIdForTaxonID) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setId(useCoreIdForTaxonID ? v.getId() : v.getCoreTerm(DwcTerm.taxonID));
    t.setStatus(SafeParser.parse(TaxonomicStatusParser.PARSER, v.getCoreTerm(DwcTerm.taxonomicStatus))
        .orElse(TaxonomicStatus.DOUBTFUL)
    );
    //TODO: interpret all of Taxon via new dwca extension
    t.setAccordingTo(null);
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.getCoreTerm(DcTerm.references)).orNull());
    t.setFossil(null);
    t.setRecent(null);
    //t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceKey(null);
    t.setRemarks(v.getCoreTerm(DwcTerm.taxonRemarks));
    return t;
  }

  private Name interpretName(VerbatimRecord v) {
    Set<Issue> issues = EnumSet.noneOf(Issue.class);

    // parse rank
    Rank rank = SafeParser.parse(RankParser.PARSER, v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank))
        .orElse(Rank.UNRANKED, Issue.RANK_INVALID, issues);
    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    Name n;
    if (v.hasCoreTerm(DwcTerm.scientificName)) {
      n = NameParser.PARSER.parse(v.getCoreTerm(DwcTerm.scientificName), rank).get();
      // TODO: validate name against optional atomized terms!
      //Name n2 = buildNameFromVerbatimTerms(v);

    } else {
      n = buildNameFromVerbatimTerms(v);
    }
    n.getIssues().addAll(issues);

    // assign best rank
    if (rank.notOtherOrUnranked() || n.getRank() == null) {
      n.setRank(rank);
    }

    // try to add an authorship if not yet there
    if (v.hasCoreTerm(DwcTerm.scientificNameAuthorship)) {
      try {
        Name authorship = parseAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        if (n.hasAuthorship()) {
          if (!n.authorshipComplete().equalsIgnoreCase(authorship.authorshipComplete())){
            n.addIssue(Issue.INCONSISTENT_AUTHORSHIP);
            LOG.warn("Different authorship found in dwc:scientificName=[{}] and dwc:scientificNameAuthorship=[{}]",
                n.authorshipComplete(),
                authorship.authorshipComplete());
          }
        } else {
          n.setCombinationAuthorship(authorship.getCombinationAuthorship());
          n.setSanctioningAuthor(authorship.getSanctioningAuthor());
          n.setBasionymAuthorship(authorship.getBasionymAuthorship());
        }

      } catch (UnparsableException e) {
        LOG.warn("Unparsable authorship {}", v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        n.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
      }
    }

    n.setId(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID));
    n.setOrigin(Origin.SOURCE);
    n.setSourceUrl(SafeParser.parse(UriParser.PARSER, v.getCoreTerm(DcTerm.references)).orNull());
    n.setStatus(SafeParser.parse(NomStatusParser.PARSER, v.getCoreTerm(DwcTerm.nomenclaturalStatus))
        .orElse(null, Issue.NOMENCLATURAL_STATUS_INVALID, n.getIssues())
    );
    n.setCode(SafeParser.parse(NomCodeParser.PARSER, v.getCoreTerm(DwcTerm.nomenclaturalCode))
        .orElse(null, Issue.NOMENCLATURAL_CODE_INVALID, n.getIssues())
    );
    //TODO: should we also get these through an extension, e.g. species profile or a nomenclature extension?
    n.setRemarks(v.getCoreTerm(CoLTerm.nomenclaturalRemarks));
    n.setFossil(null);

    // basionym is kept purely in neo4j

    if (!n.isConsistent()) {
      n.addIssue(Issue.INCONSISTENT_NAME);
      LOG.info("Inconsistent name: {}", n);
    }

    return n;
  }

  private Name buildNameFromVerbatimTerms(VerbatimRecord v) {
    Name n = new Name();
    n.setGenus(v.getFirst(GbifTerm.genericName, DwcTerm.genus));
    n.setInfragenericEpithet(v.getCoreTerm(DwcTerm.subgenus));
    n.setSpecificEpithet(v.getCoreTerm(DwcTerm.specificEpithet));
    n.setInfraspecificEpithet(v.getCoreTerm(DwcTerm.infraspecificEpithet));
    n.setType(NameType.SCIENTIFIC);
    //TODO: detect named hybrids in epithets manually
    n.setNotho(null);
    try {
      n.setScientificName(n.canonicalName());
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
    return n;
  }

  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  private Name parseAuthorship(String authorship) throws UnparsableException {
      return NameParser.PARSER.parse("Abies alba "+authorship).get();
  }
}
