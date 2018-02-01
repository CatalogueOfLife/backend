package org.col.dw.parser;

import org.junit.Test;

/**
 *
 */
public class SynonymStatusParserTest extends ParserTestBase<Boolean> {

  public SynonymStatusParserTest() {
    super(SynonymStatusParser.PARSER);
  }


  @Test
  public void parse() throws Exception {
    assertParse(true, "synonym");
    assertParse(true, "juniorsynonym");
    assertParse(true, "unaccepted!");
    assertParse(true, " ambiguoussynonym");
    assertParse(true, "sinônimo");
    assertParse(true, "Pro-Parte");

    //assertParse(false,"correct");
    assertParse(false,"Valid");
    assertParse(false,"accepted");
    assertParse(false,"provisional");
  }
}