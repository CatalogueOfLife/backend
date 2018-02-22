package org.col.admin.task.importer.dwca;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.api.exception.InvalidNameException;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final Splitter MULTIVAL = Splitter.on(CharMatcher.anyOf(";|,")).trimResults();

  private final InsertMetadata insertMetadata;
  private final ReferenceStore refStore;

  public DwcInterpreter(InsertMetadata insertMetadata, ReferenceStore refStore) {
    this.insertMetadata = insertMetadata;
    this.refStore = refStore;
  }

  private static String first(VerbatimRecord v, Term... terms) {
    for (Term t : terms) {
      // verbatim data is cleaned already and all empty strings are removed from the terms map
      if (v.hasTerm(t)) {
        return v.getTerm(t);
      }
    }
    return null;
  }

  public NeoTaxon interpret(VerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    // acts
    t.acts = interpretActs(v);
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getTerm(dwc));
    }
    // add taxon in any case - we can swap status of a synonym during normalization
    t.taxon = interpretTaxon(v, insertMetadata.isCoreIdUsed());
    // a synonym by status?
    // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during relation insertion
    if(SafeParser.parse(SynonymStatusParser.PARSER, v.getTerm(DwcTerm.taxonomicStatus)).orElse(false)) {
      t.synonym = new NeoTaxon.Synonym();
    }
    // supplementary infos
    interpretBibliography(t);
    interpretVernacularNames(t);
    interpretDistributions(t);

    return t;
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

  private void interpretBibliography(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Reference)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Reference)) {
        //TODO: create / lookup references
        LOG.debug("Reference extension not implemented, but record found: {}", rec.getFirst(DcTerm.identifier, DcTerm.title, DcTerm.bibliographicCitation));
      }
    }
  }

  private void interpretDistributions(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Distribution)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Distribution)) {
        // try to figure out an area
        if (rec.hasTerm(DwcTerm.locationID)) {
          for (String loc : MULTIVAL.split(rec.get(DwcTerm.locationID))) {
            AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull();
            if (area != null) {
              addDistribution(t, area.area, area.standard, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_AREA_INVALID);
            }
          }

        } else if(rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
          for (String craw : MULTIVAL.split(rec.getFirst(DwcTerm.countryCode, DwcTerm.country))) {
            Country country = SafeParser.parse(CountryParser.PARSER, craw).orNull();
            if (country != null) {
              addDistribution(t, country.getIso2LetterCode(), Gazetteer.ISO, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_COUNTRY_INVALID);
            }
          }

        } else if(rec.hasTerm(DwcTerm.locality)) {
          addDistribution(t, rec.get(DwcTerm.locality), Gazetteer.TEXT, rec);

        } else {
          t.addIssue(Issue.DISTRIBUTION_INVALID);
        }
      }
    }
  }

  private void addDistribution(NeoTaxon t, String area, Gazetteer standard, TermRecord rec) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setAreaStandard(standard);
    addReferences(d, rec);
    //TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    t.distributions.add(d);
  }

  private void addReferences(Referenced obj, TermRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      //TODO: test for multiple
      obj.addReferenceKey(lookupReferenceTitleID(null, v.get(DcTerm.source)).getKey());
    }
  }

  private void interpretVernacularNames(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.VernacularName)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.VernacularName)) {
        if (rec.hasTerm(DwcTerm.vernacularName)) {
          VernacularName vn = new VernacularName();
          vn.setName(rec.get(DwcTerm.vernacularName));
          vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(DcTerm.language)).orNull());
          vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(DwcTerm.countryCode, DwcTerm.country)).orNull());
          addReferences(vn, rec);
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
    t.setId(useCoreIdForTaxonID ? v.getId() : v.getTerm(DwcTerm.taxonID));
    t.setStatus(SafeParser.parse(TaxonomicStatusParser.PARSER, v.getTerm(DwcTerm.taxonomicStatus))
        .orElse(TaxonomicStatus.DOUBTFUL)
    );
    //TODO: interpret all of Taxon via new dwca extension
    t.setAccordingTo(v.getTerm(DwcTerm.nameAccordingTo));
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.getTerm(DcTerm.references)).orNull(Issue.URL_INVALID, t.getIssues()));
    t.setFossil(null);
    t.setRecent(null);
    //t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceKey(null);
    t.setRemarks(v.getTerm(DwcTerm.taxonRemarks));
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
    String vSciname = v.getTerm(DwcTerm.scientificName);
    if (v.hasTerm(DwcTerm.scientificName)) {
      n = NameParser.PARSER.parse(vSciname, rank).get();
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
    if (v.hasTerm(DwcTerm.scientificNameAuthorship)) {
      Optional<ParsedName> optAuthorship = NameParser.PARSER.parseAuthorship(v.getTerm(DwcTerm.scientificNameAuthorship));
      if (optAuthorship.isPresent()) {
        ParsedName authorship = optAuthorship.get();
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

      } else {
        LOG.warn("Unparsable authorship {}", v.getTerm(DwcTerm.scientificNameAuthorship));
        n.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
      }
    }

    n.setId(ObjectUtils.firstNonNull(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID), v.getId()));
    n.setOrigin(Origin.SOURCE);
    n.setSourceUrl(SafeParser.parse(UriParser.PARSER, v.getTerm(DcTerm.references)).orNull());
    n.setStatus(SafeParser.parse(NomStatusParser.PARSER, v.getTerm(DwcTerm.nomenclaturalStatus))
        .orElse(null, Issue.NOMENCLATURAL_STATUS_INVALID, n.getIssues())
    );
    n.setCode(SafeParser.parse(NomCodeParser.PARSER, v.getTerm(DwcTerm.nomenclaturalCode))
        .orElse(null, Issue.NOMENCLATURAL_CODE_INVALID, n.getIssues())
    );
    //TODO: should we also get these through an extension, e.g. species profile or a nomenclature extension?
    n.setRemarks(v.getTerm(CoLTerm.nomenclaturalRemarks));
    n.setFossil(null);

    if (!n.isConsistent()) {
      n.addIssue(Issue.INCONSISTENT_NAME);
      LOG.info("Inconsistent name {}: {}", v.getId(), n);
    }

    return n;
  }

  private Name buildNameFromVerbatimTerms(VerbatimRecord v) {
    Name n = new Name();
    // named hybrids in epithets are detected by setters
    n.setGenus(v.getFirst(GbifTerm.genericName, DwcTerm.genus));
    n.setInfragenericEpithet(v.getTerm(DwcTerm.subgenus));
    n.setSpecificEpithet(v.getTerm(DwcTerm.specificEpithet));
    n.setInfraspecificEpithet(v.getTerm(DwcTerm.infraspecificEpithet));
    n.setType(NameType.SCIENTIFIC);
    try {
      n.updateScientificName();
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
    return n;
  }
}
