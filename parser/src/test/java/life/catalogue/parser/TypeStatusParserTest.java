package life.catalogue.parser;

import life.catalogue.api.vocab.TypeStatus;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class TypeStatusParserTest extends ParserTestBase<TypeStatus> {

  public TypeStatusParserTest() {
    super(TypeStatusParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TypeStatus.HOLOTYPE, "h");
    assertParse(TypeStatus.HOLOTYPE, "holo");
    assertParse(TypeStatus.HOLOTYPE, "holotypo");
    assertParse(TypeStatus.EX_TYPE, "ex neotype");
    assertParse(TypeStatus.EX_TYPE, "Ex-Neotypus");

    assertParse(TypeStatus.ISOTYPE, "isotypes");
    assertParse(TypeStatus.ISOTYPE, "Iso Types");
  }

  @Test
  public void empty() throws Exception {
    assertEmpty("none");
    assertEmpty("Other material");
    assertEmpty("other_material");
    assertEmpty("no type");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "pp");
  }
}
