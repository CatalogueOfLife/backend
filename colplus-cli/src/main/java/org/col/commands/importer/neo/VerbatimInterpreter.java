package org.col.commands.importer.neo;

import org.col.api.Taxon;
import org.col.api.VerbatimRecord;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.gbif.common.parsers.RankParser;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class VerbatimInterpreter {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimInterpreter.class);

  private RankParser rankParser = RankParser.getInstance();

  private static String first(VerbatimRecord v, Term... terms) {
    for (Term t : terms) {
      // verbatim data is cleaned already and all empty strings are removed from the terms map
      if (v.hasCoreTerm(t)) {
        return v.getCoreTerm(t);
      }
    }
    return null;
  }

  public static NeoTaxon interpret(VerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    t.verbatim = v;
    t.taxon = new Taxon();
    t.taxon.setId(v.getCoreTerm(DwcTerm.taxonID));
    t.taxon.getName().setScientificName(v.getCoreTerm(DwcTerm.scientificName));
    return t;
  }
}
