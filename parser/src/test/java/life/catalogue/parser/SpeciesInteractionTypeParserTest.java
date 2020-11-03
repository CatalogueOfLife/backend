package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.SpeciesInteractionType;
import org.junit.Test;

import java.util.List;

public class SpeciesInteractionTypeParserTest extends ParserTestBase<SpeciesInteractionType> {

  public SpeciesInteractionTypeParserTest() {
    super(SpeciesInteractionTypeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(SpeciesInteractionType.POLLINATES, "pollinate");
    assertParse(SpeciesInteractionType.EATS, "eat");
    assertParse(SpeciesInteractionType.EATS, "preys");
    assertParse(SpeciesInteractionType.EATS, "preys on");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "3d");
  }
}
