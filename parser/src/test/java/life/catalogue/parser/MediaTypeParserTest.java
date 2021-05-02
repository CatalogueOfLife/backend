package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.MediaType;
import org.junit.Test;

import java.util.List;

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