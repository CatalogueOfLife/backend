package life.catalogue.parser;

import life.catalogue.api.vocab.SpeciesInteractionType;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

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
