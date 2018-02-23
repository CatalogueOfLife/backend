package org.col.api.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;

/**
 * Jackson module that defines all serde rules for all CoL API model classes.
 */
public class ApiModule extends SimpleModule {

  public static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    MAPPER.registerModule(new ApiModule());
  }

  public ApiModule() {
    super("ColApi");

    // first deserializers
    addDeserializer(Country.class, new CountrySerde.Deserializer());
    addDeserializer(Language.class, new LanguageSerde.Deserializer());
    addDeserializer(Rank.class, new RankSerde.Deserializer());
    addDeserializer(Term.class, new TermSerde.Deserializer());
    addDeserializer(TermRecord.class, new TermRecordSerde.Deserializer());
    addDeserializer(ExtendedTermRecord.class, new ExtendedTermRecordSerde.Deserializer());

    // then serializers:
    addSerializer(Country.class, new CountrySerde.Serializer());
    addSerializer(Language.class, new LanguageSerde.Serializer());
    addSerializer(Rank.class, new RankSerde.Serializer());
    addSerializer(Term.class, new TermSerde.ValueSerializer());
    addSerializer(TermRecord.class, new TermRecordSerde.Serializer());
    addSerializer(ExtendedTermRecord.class, new ExtendedTermRecordSerde.Serializer());

    // then key deserializers
    addKeyDeserializer(Term.class, new TermSerde.TermKeyDeserializer());
    addKeyDeserializer(Language.class, new CountrySerde.KeyDeserializer());
    addKeyDeserializer(Language.class, new LanguageSerde.KeyDeserializer());

    // then key serializers
    addKeySerializer(Term.class, new TermSerde.FieldSerializer());
    addKeySerializer(Country.class, new CountrySerde.FieldSerializer());
    addKeySerializer(Language.class, new LanguageSerde.FieldSerializer());

    // by default jackson uses a LinkedHashMap to deserialize maps
    //addAbstractTypeMapping(Map.class, HashMap.class);
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    // required to properly register serdes
    super.setupModule(ctxt);

    ctxt.setMixInAnnotations(ParsedName.class, ParsedNameMixIn.class);
    ctxt.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
  }

  abstract class ParsedNameMixIn {
    @JsonIgnore abstract boolean isAutonym();
    @JsonIgnore abstract boolean isBinomial();
    @JsonIgnore abstract boolean isTrinomial();
    @JsonIgnore abstract boolean isIndetermined();
    @JsonIgnore abstract boolean isConsistent();
    @JsonIgnore abstract boolean getTerminalEpithet();
    @JsonIgnore abstract String canonicalName();
    @JsonIgnore abstract String canonicalNameWithoutAuthorship();
    @JsonIgnore abstract String canonicalNameMinimal();
    @JsonIgnore abstract String canonicalNameComplete();
    @JsonProperty("authorship") abstract String authorshipComplete();
    @JsonProperty("nomenclaturalCode") abstract NomCode getCode();
  }

  abstract class AuthorshipMixIn {
    @JsonIgnore abstract boolean isEmpty();
  }
}