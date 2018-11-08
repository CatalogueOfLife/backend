package org.col.parser;


import org.col.api.vocab.Gazetteer;

/**
 * Parses area standards
 */
public class GazetteerParser extends EnumParser<Gazetteer> {
  public static final GazetteerParser PARSER = new GazetteerParser();
  
  public GazetteerParser() {
    super("gazetteer.csv", Gazetteer.class);
  }
  
}
