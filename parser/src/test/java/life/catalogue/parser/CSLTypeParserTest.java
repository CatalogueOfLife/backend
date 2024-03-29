package life.catalogue.parser;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import de.undercouch.citeproc.csl.CSLType;

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
    assertParse(CSLType.ARTICLE_JOURNAL, "journal article");
    assertParse(CSLType.ARTICLE_JOURNAL, "journal-article");
    assertParse(CSLType.ARTICLE_JOURNAL, "journal_article");
    assertParse(CSLType.ARTICLE_JOURNAL, "JournalArticle");
    assertParse(CSLType.ARTICLE_JOURNAL, "journalArticle");
    assertParse(CSLType.PERSONAL_COMMUNICATION, "email");
    assertParse(CSLType.PERSONAL_COMMUNICATION, "eMail");

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

