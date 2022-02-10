package life.catalogue.parser;

import life.catalogue.api.vocab.Language;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import static life.catalogue.api.vocab.Language.byCode;
/**
 *
 */
public class LanguageParserTest extends ParserTestBase<Language> {

  public LanguageParserTest() {
    super(LanguageParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse("deu", "de");
    assertParse("deu", "deu");
    assertParse("deu", "german");
    assertParse("deu", "Deutsch");
    assertParse("deu", "GER");
    assertParse("eng", "en");
    assertParse("ceb", "visayan");
    assertParse("ceb", "Ormocanon");
    assertParse("ceb", "Cebuano");
    assertParse("kwz", "Квади");
    assertParse("ale", "aléoute");

    for (String x : new String[]{"Limburgan", "Limburger", "Limburgish", "Lim", "li", "林堡语", "LIMBOURGEOIS"}) {
      assertParse("lim", x);
    }
    
    assertUnparsable("unknown");
    assertUnparsable("zz");
  }
  
  private void assertParse(String expected, String input) throws UnparsableException {
    assertParse(byCode(expected), input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}