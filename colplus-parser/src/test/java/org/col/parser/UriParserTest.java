package org.col.parser;

import java.net.URI;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

public class UriParserTest extends ParserTestBase<URI> {
  
  public UriParserTest() {
    super(UriParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse("http://www.gbif.org", "www.gbif.org");
    assertParse("http://www.gbif.org", "http://www.gbif.org");
    assertParse("https://www.gbif.org", "https://www.gbif.org");
    assertParse("https://www.gbif.org/", "https://www.gbif.org/");
    assertParse("ftp://www.gbif.org/me", "ftp://www.gbif.org/me");
    assertParse("http://gbif.org/alreadyEscaped?q=Me&q=you&q=%3F%3D%26", "gbif.org/alreadyEscaped?q=Me&q=you&q=%3F%3D%26");
    assertParse("http://gbif.org/nonAscii?q=Me&q=you&q=öäüérle", "gbif.org/nonAscii?q=Me&q=you&q=öäüérle");
    
    assertParse("http://gbif.org/missing%20space?escape=t%20r%20u%20e", "http://gbif.org/missing space?escape=t r u e");
    assertParse("http://gbif.org/missing%20space?escape=t%20r%20u%20e", "gbif.org/missing space?escape=t r u e");
  
  }
  
  private void assertParse(String expected, String input) throws UnparsableException {
    assertParse(URI.create(expected), input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("www", "deuter");
  }
}