package life.catalogue.parser;

import life.catalogue.api.vocab.NomRelType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class NomRelTypeParser extends EnumParser<NomRelType> {
  public static final NomRelTypeParser PARSER = new NomRelTypeParser();
  
  public NomRelTypeParser() {
    super("nomreltype.csv", NomRelType.class);

    // read NOMEN ontology, see https://github.com/CatalogueOfLife/backend/issues/716
    NomenOntology nomen = new NomenOntology();
    Pattern NomenInt = NomStatusParser.nomenIntPattern();
    for (NomenOntology.Nomen n : nomen.list()) {
      if (n.nomRelType != null) {
        add(n.name, n.nomRelType);
        add(NomenOntology.NAMESPACE.resolve(n.name), n.nomRelType);
        Matcher m = NomenInt.matcher(n.name);
        if (m.find()) {
          add("NOMEN_" + Integer.parseInt(m.group(1)), n.nomRelType);
        }
      }
    }
  }
  
}
