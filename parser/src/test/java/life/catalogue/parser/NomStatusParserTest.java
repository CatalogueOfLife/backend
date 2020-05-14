package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.NomStatus;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class NomStatusParserTest extends ParserTestBase<NomStatus> {

  public NomStatusParserTest() {
    super(NomStatusParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(NomStatus.MANUSCRIPT, "ms");
    assertParse(NomStatus.MANUSCRIPT, "manuscript");
    assertParse(NomStatus.MANUSCRIPT, "ined.");
    assertParse(NomStatus.MANUSCRIPT, "ineditus");
  
    assertParse(NomStatus.ESTABLISHED, "available");
    assertParse(NomStatus.ESTABLISHED, "validly published");
    
    assertParse(NomStatus.ACCEPTABLE, "valid");

    assertParse(NomStatus.DOUBTFUL, "inquirenda");
    assertParse(NomStatus.DOUBTFUL, "nom inquirenda");
    assertParse(NomStatus.DOUBTFUL, "nomen inquirendum");
    assertParse(NomStatus.DOUBTFUL, "nomina inquirenda");

    assertParse(NomStatus.ESTABLISHED, "NOMEN_0000228");
    assertParse(NomStatus.ESTABLISHED, "http://purl.obolibrary.org/obo/NOMEN_0000228");
    assertParse(NomStatus.ESTABLISHED, "NOMEN_228");

    assertUnparsable("NOMEN_0012228");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
