package org.col.parser;

import org.col.api.vocab.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class MediaTypeParser extends EnumParser<MediaType> {
  private static final Logger LOG = LoggerFactory.getLogger(MediaTypeParser.class);
  public static final MediaTypeParser PARSER = new MediaTypeParser();

  public MediaTypeParser() {
    super("mediatype.csv", MediaType.class);
  }

}
