package org.col.api.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Authorship;

/**
 * Jackson module that defines all serde rules for all CoL API model classes.
 */
public class ApiModule extends SimpleModule {

  public static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    MAPPER.registerModule(new JavaTimeModule());
    MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    MAPPER.registerModule(new ApiModule());
  }

  public ApiModule() {
    super("ColApi");

    // first deserializers
    addDeserializer(Country.class, new CountrySerde.Deserializer());
    addDeserializer(Language.class, new LanguageSerde.Deserializer());
    addDeserializer(Term.class, new TermSerde.Deserializer());
    addDeserializer(TermRecord.class, new TermRecordSerde.Deserializer());
    addDeserializer(ExtendedTermRecord.class, new ExtendedTermRecordSerde.Deserializer());

    // then serializers:
    addSerializer(Country.class, new CountrySerde.Serializer());
    addSerializer(Language.class, new LanguageSerde.Serializer());
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
  }


  @Override
  public void setupModule(SetupContext ctxt) {
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.appendAnnotationIntrospector(new EnumAnnotationIntrospector());
    ctxt.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
  }

  abstract class AuthorshipMixIn {
    @JsonIgnore abstract boolean isEmpty();
  }

  /**
   * Intercepts the way standard EnumValues are build by forcing all values to lower case and
   * to use a space instead of underscores.
   */
  public static class EnumAnnotationIntrospector extends NopAnnotationIntrospector {

    @Override
    public String[] findEnumValues(Class<?> enumType, Enum<?>[] enumValues, String[] names) {
      int idx = 0;
      for (Enum<?> e : enumValues) {
        names[idx++] = enumValueName(e);
      }
      return names;
    }
  }

  public static String enumValueName(Enum<?> val) {
    return val.name().toLowerCase().replaceAll("_+", " ");
  }

}