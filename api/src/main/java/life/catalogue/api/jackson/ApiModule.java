package life.catalogue.api.jackson;

import com.google.common.collect.Lists;
import com.google.common.reflect.ClassPath;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.terms.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.coldp.DwcUnofficialTerm;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Authorship;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import de.undercouch.citeproc.csl.CSLType;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson module that defines all serde rules for all CoL API model classes.
 */
public class ApiModule extends SimpleModule {
  private static final Logger LOG = LoggerFactory.getLogger(ApiModule.class);

  public static final ObjectMapper MAPPER = configureMapper(new ObjectMapper());

  static {
    // register new term enums
    registerTermPackages(ColdpTerm.class, TxtTreeTerm.class);
  }

  public static void registerTermPackages(Class<?>... anchors) {
    for (Class<?> anchor : Lists.newArrayList(anchors)) {
      Package p = anchor.getPackage();
      LOG.debug("Scan package {} for term enums", p);
      try {
        for (ClassPath.ClassInfo info : ClassPath.from(ApiModule.class.getClassLoader()).getTopLevelClasses(p.getName())) {
          Class<?> cl = info.load();
          if (cl.isEnum() && Term.class.isAssignableFrom(cl) && AlternativeNames.class.isAssignableFrom(cl)) {
            TermFactory.instance().registerTermEnum(upcast(cl));
          }
        }
      } catch (IOException e) {
        LOG.error("Cannot read term class infos in package {}", p, e);
      }
    }
  }
  @SuppressWarnings("unchecked")
  private static <T extends Enum & Term & AlternativeNames> Class<T> upcast(final Class<?> in) {
    return (Class<T>) in;
  }

  public static ObjectMapper configureMapper(ObjectMapper mapper) {
    // keep all capital fields as such, dont lowercase them!!
    mapper.enable(MapperFeature.USE_STD_BEAN_NAMING);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    mapper.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);

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
    this("ColApi");
  }
  public ApiModule(String name) {
    super(name, new Version(1, 0, 0, null, "org.catalogueoflife", "api"));

    // first deserializers
    addDeserializer(Country.class, new CountrySerde.Deserializer());
    addDeserializer(Term.class, new TermSerde.Deserializer());
    addDeserializer(CSLType.class, new CSLTypeSerde.Deserializer());
    addDeserializer(URI.class, new URIDeserializer());
    addDeserializer(UUID.class, new UUIDSerde.Deserializer());
    // override the JavaTimeModule to use a permissive localdate deserializer catching parsing exceptions
    addDeserializer(LocalDateTime.class, new PermissiveJavaDateSerde.LocalDateTimeDeserializer());
    addDeserializer(LocalDate.class, new PermissiveJavaDateSerde.LocalDateDeserializer());

    // then serializers:
    addSerializer(Country.class, new CountrySerde.Serializer());
    addSerializer(Term.class, new TermSerde.Serializer());
    addSerializer(CSLType.class, new CSLTypeSerde.Serializer());
    addSerializer(UUID.class, new UUIDSerde.Serializer());

    // then key deserializers
    addKeyDeserializer(Term.class, new TermSerde.KeyDeserializer());
    addKeyDeserializer(Country.class, new CountrySerde.KeyDeserializer());
    addKeyDeserializer(UUID.class, new UUIDSerde.KeyDeserializer());

    // then key serializers
    addKeySerializer(Term.class, new TermSerde.KeySerializer());
    addKeySerializer(Country.class, new CountrySerde.FieldSerializer());
    addKeySerializer(UUID.class, new UUIDSerde.FieldSerializer());

    // fastutils primitive collection
    FastutilsSerde.register(this);
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    // default enum serde
    ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
    ctxt.addSerializers(new PermissiveEnumSerde.PermissiveEnumSerializers());
    ctxt.addKeySerializers(new PermissiveEnumSerde.PermissiveEnumKeySerializers());
    // lower camel case, permissive enum serde - currently only used for NameUsageSearchParameter
    ctxt.addDeserializers(new LowerCamelCaseEnumSerde.LowerCamelCaseEnumDeserializers());
    ctxt.addKeyDeserializers(new LowerCamelCaseEnumSerde.LowerCamelCaseEnumKeyDeserializers());
    ctxt.addSerializers(new LowerCamelCaseEnumSerde.LowerCamelCaseEnumSerializers());
    ctxt.addKeySerializers(new LowerCamelCaseEnumSerde.LowerCamelCaseEnumKeySerializers());
    // enum implementing EnumValue, e.g. DataCite models
    ctxt.addDeserializers(new EnumValueSerde.EnumValueDeserializers());
    ctxt.addKeyDeserializers(new EnumValueSerde.EnumValueKeyDeserializers());
    ctxt.addSerializers(new EnumValueSerde.EnumValueSerializers());
    ctxt.addKeySerializers(new EnumValueSerde.EnumValueKeySerializers());
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(Authorship.class, AuthorshipMixIn.class);
    ctxt.setMixInAnnotations(Term.class, TermMixIn.class);
  }
  
  abstract static class AuthorshipMixIn {
    @JsonIgnore
    abstract boolean isEmpty();
  }

  @JsonSerialize(using = TermSerde.Serializer.class, keyUsing = TermSerde.KeySerializer.class)
  @JsonDeserialize(using = TermSerde.Deserializer.class, keyUsing = TermSerde.KeyDeserializer.class)
  abstract static class TermMixIn {

  }

  static class URIDeserializer extends FromStringDeserializer<URI> {
  
    protected URIDeserializer() {
      super(URI.class);
    }
    
    @Override
    protected URI _deserialize(String value, DeserializationContext ctxt) throws IOException {
      return URI.create(value);
    }
  }
}
