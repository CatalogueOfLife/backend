package life.catalogue.parser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

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
    // DOIs are special - the doi.org resolver can contain unescaped DOIs which can have reserved URI characters
    assertParseDOI("10.3417/1055-3177(2006)", "https://doi.org/10.3417/1055-3177(2006)");
    assertParseDOI("10.3417/1055-3177(2006)16[244:ANSOCS]2.0.CO;2", "https://doi.org/10.3417/1055-3177(2006)16[244:ANSOCS]2.0.CO;2");
  }
  
  private void assertParse(String expected, String input) throws UnparsableException, URISyntaxException {
    assertParse(URI.create(expected), input);
  }
  private void assertParseDOI(String expectedDOI, String input) throws UnparsableException, URISyntaxException {
    URI expected = new URI("https", "doi.org", "/"+expectedDOI, null, null);
    assertParse(expected, input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("www", "deuter");
  }
}