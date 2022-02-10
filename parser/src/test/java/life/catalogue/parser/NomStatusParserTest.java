package life.catalogue.parser;

import life.catalogue.api.vocab.NomStatus;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

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

    assertParse(NomStatus.NOT_ESTABLISHED, "orthographia");
    assertParse(NomStatus.NOT_ESTABLISHED, "orth var");
    assertParse(NomStatus.NOT_ESTABLISHED, "orth. var.");
    assertParse(NomStatus.NOT_ESTABLISHED, "orth.var.");

    assertParse(NomStatus.CONSERVED, "orth.cons.");

    assertUnparsable("NOMEN_0012228");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
