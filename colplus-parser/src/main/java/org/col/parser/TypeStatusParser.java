package org.col.parser;

import org.col.api.vocab.TypeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class TypeStatusParser extends EnumParser<TypeStatus> {
  private static final Logger LOG = LoggerFactory.getLogger(TypeStatusParser.class);
  
  public static final TypeStatusParser PARSER = new TypeStatusParser();
  
  public TypeStatusParser() {
    super("typestatus.csv", TypeStatus.class);
  }
  
}
