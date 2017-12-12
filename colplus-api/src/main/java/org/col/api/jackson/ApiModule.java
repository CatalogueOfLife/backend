package org.col.api.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.ParsedName;

/**
 * Jackson module that defines all serde rules for all CoL API model classes.
 */
public class ApiModule extends SimpleModule {
  public ApiModule() {
    super("ColApi");
  }

  @Override
  public void setupModule(SetupContext context) {
    context.setMixInAnnotations(ParsedName.class, ParsedNameMixIn.class);
    context.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
  }

  abstract class ParsedNameMixIn {
    @JsonIgnore abstract boolean isAutonym();
    @JsonIgnore abstract boolean isBinomial();
    @JsonIgnore abstract boolean isTrinomial();
    @JsonIgnore abstract boolean isIndetermined();
    @JsonIgnore abstract boolean isConsistent();
    @JsonIgnore abstract boolean getTerminalEpithet();
    @JsonProperty("authorship") abstract String authorshipComplete();
  }

  abstract class AuthorshipMixIn {
    @JsonIgnore abstract boolean isEmpty();
  }
}