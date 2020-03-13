package life.catalogue.parser;

import java.util.List;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.parser.MediaTypeParser;
import org.junit.Test;

public class MediaTypeParserTest extends ParserTestBase<MediaType> {
  
  public MediaTypeParserTest() {
    super(MediaTypeParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(MediaType.VIDEO, "movie");
    assertParse(MediaType.VIDEO, "Film");
  }
  
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}