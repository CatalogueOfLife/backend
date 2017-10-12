package org.col.commands.importer;

import org.col.api.Name;
import org.col.api.Taxon;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.parser.NameParser;
import org.col.parser.NameParserGNA;
import org.col.parser.RankParser;
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

  private RankParser rankParser = new RankParser();
  private NameParser nameParser = new NameParserGNA();


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
    t.verbatim = v;
    t.taxon = new Taxon();
    t.taxon.setId(v.getCoreTerm(DwcTerm.taxonID));
    t.name = interpretName(v);
    return t;
  }

  private Name interpretName(VerbatimRecord v) {

    Name n;
    Rank rank = rankParser.parse(v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank)).orElse(Rank.UNRANKED);

    // we can get the scientific name in various ways.
    // we parse all names from the scientificName + optional authorship
    // or use the atomized parts which we also use to validate the parsing result.
    if (v.hasCoreTerm(DwcTerm.scientificName)) {
      n = nameParser.parse(v.getCoreTerm(DwcTerm.scientificName), rank).get();

    } else {
      n = new Name();
      n.setRank(rank);
      n.setGenus(v.getFirst(GbifTerm.genericName, DwcTerm.genus));
      n.setInfragenericEpithet(v.getCoreTerm(DwcTerm.subgenus));
      n.setSpecificEpithet(v.getCoreTerm(DwcTerm.specificEpithet));
      n.setInfraspecificEpithet(v.getCoreTerm(DwcTerm.infraspecificEpithet));
      n.setScientificName(n.buildScientificName());

    }

    // try to add an authorship if not yet there
    if (v.hasCoreTerm(DwcTerm.scientificNameAuthorship)) {
      Name authorship = parseAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));
      if (n.hasAuthorship()) {
        // TODO: compare authorships and raise warning if different
      } else {
        n.copyAuthorship(authorship);
      }
    }

    n.setId(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID));

    return n;
  }

  /**
   * @return a name instance with just the parsed authorship, i.e. combination & original year & author list
   */
  private Name parseAuthorship(String authorship) {
    Name auth = nameParser.parse("Abies alba "+authorship, Rank.SPECIES).get();
    auth.setGenus(null);
    auth.setSpecificEpithet(null);
    auth.setRank(null);
    return auth;
  }
}
