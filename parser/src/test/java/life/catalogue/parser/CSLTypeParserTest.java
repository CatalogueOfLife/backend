package life.catalogue.parser;

import com.google.common.collect.Lists;

import de.undercouch.citeproc.csl.CSLType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CSLTypeParserTest extends ParserTestBase<CSLType> {

  public CSLTypeParserTest() {
    super(CSLTypeParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(CSLType.ARTICLE, "Article");
    assertParse(CSLType.ARTICLE, "article");
    assertParse(CSLType.ARTICLE, "ARTICLE");
    assertParse(CSLType.ARTICLE_JOURNAL, "ARTICLE_JOURNAL");
    assertParse(CSLType.ARTICLE_JOURNAL, "article journal");
    assertParse(CSLType.ARTICLE_JOURNAL, "ARTICLE-journal");
    assertParse(CSLType.ARTICLE_JOURNAL, "Article  Journal");

    assertUnparsable("0");
    assertUnparsable("f");
    assertUnparsable("f");
    assertUnparsable("no");
    assertUnparsable("nein");
    assertUnparsable("-1");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ur e", "a", "2");
  }
}

