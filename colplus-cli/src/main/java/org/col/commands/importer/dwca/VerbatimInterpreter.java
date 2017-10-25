package org.col.commands.importer.dwca;

import org.col.api.*;
import org.col.api.exception.InvalidNameException;
import org.col.api.vocab.*;
import org.col.commands.importer.neo.InsertMetadata;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class VerbatimInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimInterpreter.class);

  private RankParser rankParser = RankParser.PARSER;
  private NameParser nameParser = NameParserGNA.PARSER;
  private UriParser uriParser = UriParser.PARSER;
  private SynonymStatusParser synonymStatusParser = SynonymStatusParser.PARSER;
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

  public NeoTaxon interpret(VerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    if (insertMetadata.isOriginalNameMapped()) {
      Name on = new Name();
      on.setScientificName(v.getCoreTerm(DwcTerm.originalNameUsage));
      on.setId(v.getCoreTerm(DwcTerm.originalNameUsageID));
      t.name.setOriginalName(on);
    }
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getCoreTerm(dwc));
    }
    // add taxon in any case - we can swap stawtus of a synonym during normalization
    // and it keeps the taxonID for resolution of relations
    t.taxon = new Taxon();
    t.taxon.setId(v.getCoreTerm(DwcTerm.taxonID));
    //TODO: parse status
    t.taxon.setStatus(TaxonomicStatus.ACCEPTED);
    if (insertMetadata.isParentNameMapped()) {
      Taxon parent = new Taxon();
      //parent.setScientificName(v.getCoreTerm(DwcTerm.parentNameUsage));
      parent.setId(v.getCoreTerm(DwcTerm.parentNameUsageID));
      t.taxon.setParent(parent);
    }
    // a synonym?
    t.synonym = parseSynonym(v);
    return t;
  }

  /**
   * @return A synonym instance if the record is considered to be a synonym, otherwise null
   */
  private NeoTaxon.Synonym parseSynonym(VerbatimRecord v) {
    NeoTaxon.Synonym syn = new NeoTaxon.Synonym();

    syn.statusSynonym = SafeParser.parse(synonymStatusParser, v.getCoreTerm(DwcTerm.taxonomicStatus))
        .orElse(false);
    if (insertMetadata.isAcceptedNameMapped()) {
      syn.acceptedNameUsage = getCoreTermIfDifferent(v, DwcTerm.acceptedNameUsage, DwcTerm.scientificName);
      syn.acceptedNameUsageID = getCoreTermIfDifferent(v, DwcTerm.acceptedNameUsageID, DwcTerm.taxonID);
    }
    if (syn.statusSynonym || syn.acceptedNameUsage != null || syn.acceptedNameUsageID != null) {
      syn.id = v.getCoreTerm(DwcTerm.taxonID);
      return syn;
    }
    return null;
  }

  /**
   * @return the value of term if the value is different from the value of otherTerm. Null otherwise
   */
  private String getCoreTermIfDifferent(VerbatimRecord v, Term term, Term otherTerm) {
    String val1 = v.getCoreTerm(term);
    String val2 = v.getCoreTerm(otherTerm);
    if (val1 != null && val2 != null && !val1.equals(val2)) {
      return val1;
    }
    return null;
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
      n.setScientificName(n.buildScientificName());
    } catch (InvalidNameException e) {
      LOG.warn("Invalid atomised name found: {}", n);
      n.addIssue(Issue.INCONSISTENT_NAME);
    }
    return n;
  }

  private Name interpretName(VerbatimRecord v) {
    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    Name n;
    if (v.hasCoreTerm(DwcTerm.scientificName)) {
      try {
        n = nameParser.parse(v.getCoreTerm(DwcTerm.scientificName)).get();
        // TODO: validate name against optional atomized terms!
      } catch (UnparsableException e) {
        n = buildNameFromVerbatimTerms(v);
        n.addIssue(Issue.UNPARSABLE_NAME);
      }

    } else {
      n = buildNameFromVerbatimTerms(v);
    }
    // parse rank
    final Rank rank = SafeParser.parse(rankParser, v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank))
        .orElse(Rank.UNRANKED, Issue.RANK_INVALID, n.getIssues());
    n.setRank(rank);

    // try to add an authorship if not yet there
    if (v.hasCoreTerm(DwcTerm.scientificNameAuthorship)) {
      try {
        Authorship authorship = parseAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        if (n.hasAuthorship()) {
          // TODO: compare authorships and raise warning if different
        } else {
          n.setAuthorship(authorship);
        }

      } catch (UnparsableException e) {
        LOG.warn("Unparsable authorship {}", v.getCoreTerm(DwcTerm.scientificNameAuthorship));
        n.addIssue(Issue.UNPARSABLE_AUTHORSHIP);
      }
    }

    if (!n.isConsistent()) {
      n.addIssue(Issue.INCONSISTENT_NAME);
      LOG.warn("Inconsistent name: {}", n);
    }

    n.setId(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID));
    n.setOrigin(Origin.SOURCE);
    n.setSourceUrl(uriParser.parse(v.getCoreTerm(DcTerm.references)).orElse(null));
    // TODO: use new scientificNameRemarks/nomenclatureRemarks term
    n.setRemarks(v.getCoreTerm(DwcTerm.taxonRemarks));

    // TODO: parse and set more properties
    n.setEtymology(null);
    n.setOriginalName(null);
    n.setFossil(null);
    n.setNomenclaturalCode(null);
    n.setStatus(null);

    return n;
  }

  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  private Authorship parseAuthorship(String authorship) throws UnparsableException {
      Name auth = nameParser.parse("Abies alba "+authorship).get();
      return auth.getAuthorship();
  }
}
