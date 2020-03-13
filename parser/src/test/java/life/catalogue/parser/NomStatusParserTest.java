package life.catalogue.parser;

import java.util.List;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.parser.NomStatusParser;
import org.junit.Test;

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
    
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
