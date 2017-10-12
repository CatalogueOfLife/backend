package org.col.commands.importer.neo;

import org.col.api.Name;
import org.col.api.Taxon;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.parser.NameParserGBIF;
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
  private NameParserGBIF nameParser = new NameParserGBIF();


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
    // prefer the already atomized version over parsing the full string
    if (v.hasCoreTerm(DwcTerm.specificEpithet) || v.hasCoreTerm(GbifTerm.genericName)) {
      n = new Name();
      n.setRank(rank);
      n.setGenus(v.getFirst(GbifTerm.genericName, DwcTerm.genus));
      n.setInfragenericEpithet(v.getCoreTerm(DwcTerm.subgenus));
      n.setSpecificEpithet(v.getCoreTerm(DwcTerm.specificEpithet));
      n.setInfraspecificEpithet(v.getCoreTerm(DwcTerm.infraspecificEpithet));
      n.setAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));
      n.setScientificName(null);

    } else {
      n = nameParser.parse(null, rank);
    }
    n.setId(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID));
    // authorship has to be parsed in any case
    n.setAuthorship(v.getCoreTerm(DwcTerm.scientificNameAuthorship));

    return n;
  }
}
