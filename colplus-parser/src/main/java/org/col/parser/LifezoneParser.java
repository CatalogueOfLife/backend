package org.col.parser;

import org.col.api.vocab.Lifezone;

/**
 *
 */
public class LifezoneParser extends EnumParser<Lifezone> {
  public static final LifezoneParser PARSER = new LifezoneParser();
  
  public LifezoneParser() {
    super("lifezone.csv", Lifezone.class);
  }
  
}
