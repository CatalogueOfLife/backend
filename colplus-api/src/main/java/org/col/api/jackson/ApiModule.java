package org.col.api.jackson;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.col.api.datapackage.ColdpTerm;
import org.col.api.vocab.CSLRefType;
import org.col.api.vocab.ColDwcTerm;
import org.col.api.vocab.Country;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Authorship;

/**
 * Jackson module that defines all serde rules for all CoL API model classes.
 */
public class ApiModule extends SimpleModule {
  
  public static final ObjectMapper  MAPPER = configureMapper(new ObjectMapper());
  static final Set<Class> ENUM_CLASSES = ImmutableSet.of(Country.class, CSLRefType.class, Term.class);
  static {
    // register new term enums
    TermFactory.instance().registerTermEnum(ColDwcTerm.class);
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
  }
  
  public static ObjectMapper configureMapper(ObjectMapper mapper) {
    // keep all capital fields as such, dont lowercase them!!
    mapper.enable(MapperFeature.USE_STD_BEAN_NAMING);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    mapper.registerModule(new ApiModule());
    
    // flaky with java 11
    // if broken consider the experimental replacement blackbird: https://github.com/stevenschlansker/jackson-blackbird
    mapper.registerModule(new AfterburnerModule());
  
    mapper.addHandler(new CslArrayMismatchHandler());
    
    return mapper;
  }
  
  public ApiModule() {
    super("ColApi");
    
    // first deserializers
    addDeserializer(Country.class, new CountrySerde.Deserializer());
    addDeserializer(Term.class, new TermSerde.Deserializer());
    addDeserializer(CSLRefType.class, new CSLRefTypeSerde.Deserializer());
    addDeserializer(URI.class, new URIDeserializer());
    addDeserializer(Int2IntMap.class, new FastutilsSerde.Deserializer());
    
    // then serializers:
    addSerializer(Country.class, new CountrySerde.Serializer());
    addSerializer(Term.class, new TermSerde.ValueSerializer());
    addSerializer(CSLRefType.class, new CSLRefTypeSerde.Serializer());
    addSerializer(Int2IntMap.class, new FastutilsSerde.Serializer());
  
    // then key deserializers
    addKeyDeserializer(Term.class, new TermSerde.TermKeyDeserializer());
    addKeyDeserializer(Country.class, new CountrySerde.KeyDeserializer());
    
    // then key serializers
    addKeySerializer(Term.class, new TermSerde.FieldSerializer());
    addKeySerializer(Country.class, new CountrySerde.FieldSerializer());
  }
  
  @Override
  public void setupModule(SetupContext ctxt) {
    // default enum serde
    ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
    ctxt.addSerializers(new PermissiveEnumSerde.PermissiveEnumSerializers());
    ctxt.addKeySerializers(new PermissiveEnumSerde.PermissiveEnumKeySerializers());
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
  }
  
  abstract class AuthorshipMixIn {
    @JsonIgnore
    abstract boolean isEmpty();
  }
  
  static class URIDeserializer extends FromStringDeserializer<URI> {
  
    protected URIDeserializer() {
      super(URI.class);
    }
  
    @Override
    protected URI _deserializeFromEmptyString() throws IOException {
      return null;
    }
  
    @Override
    protected URI _deserialize(String value, DeserializationContext ctxt) throws IOException {
      return URI.create(value);
    }
  }
}
