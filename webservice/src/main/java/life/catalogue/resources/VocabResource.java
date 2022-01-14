package life.catalogue.resources;

import life.catalogue.coldp.DwcUnofficialTerm;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.TreeNode;
import life.catalogue.api.model.User;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.img.ImgConfig;
import life.catalogue.parser.AreaParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;

import de.undercouch.citeproc.csl.CSLType;

@Path("/vocab")
@Produces(MediaType.APPLICATION_JSON)
public class VocabResource {
  
  private static final Logger LOG = LoggerFactory.getLogger(VocabResource.class);
  private final Map<String, Class<Enum>> vocabs;
  private final List<String> vocabNames;
  
  public VocabResource() {
    LOG.info("Scan for available vocabularies");
    Map<String, Class<Enum>> enums = Maps.newHashMap();
    try {
      for (Package p : Lists.newArrayList(DatasetOrigin.class.getPackage(), Rank.class.getPackage())) {
        LOG.debug("Scan package {} for enums", p);
        for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(p.getName())) {
          add(enums, info.load());
        }
      }

      // manually add scattered enums
      for (Class<?> clazz : List.of(
          User.Role.class,
          ImgConfig.Scale.class,
          EditorialDecision.Mode.class,
          Sector.Mode.class,
          NameUsageSearchParameter.class,
          CSLType.class,
          TreeNode.Type.class)) {
        add(enums, clazz);
      }
      
    } catch (IOException e) {
      LOG.error("Failed to init enum class map", e);
    }
    vocabs = Map.copyOf(enums);
    List<String> names = new ArrayList<>(enums.keySet());
    names.add("language");
    names.add("geotime");
    names.add("terms");
    names.remove(binaryName(DwcUnofficialTerm.class));
    Collections.sort(names);
    vocabNames = List.copyOf(names);
  }
  
  private static void add(Map<String, Class<Enum>> enums, Class<?> clazz) {
    if (clazz.isEnum()) {
      LOG.debug("Adding enum {} to vocabularies", clazz.getSimpleName());
      if (enums.put(binaryName(clazz), (Class<Enum>) clazz) != null) {
        LOG.warn("Enum {} has the same key {} as an already existing vocabulary", clazz.getName(), binaryName(clazz));
      }
    }
  }
  
  @GET
  public List<String> list() {
    return vocabNames;
  }
  
  @GET
  @Path("term")
  public <TE extends Enum & Term> List<Term> terms(@QueryParam("prefix") String prefix) {
    Set<Class<? extends Enum>> classes = new HashSet<>( TermFactory.instance().listRegisteredTermEnums() );
    if (prefix != null) {
      prefix = prefix.toLowerCase().trim();
      Iterator<Class<? extends Enum>> iter = classes.iterator();
      while (iter.hasNext()) {
        Class<TE> tec = (Class<TE>) iter.next();
        if (!prefix.equals( tec.getEnumConstants()[0].prefix())) {
          iter.remove();
        }
      }
    }
    
    List<Term> terms = new ArrayList<>();
    for (Class<? extends Enum> clazz : classes) {
      Class<TE> tec = (Class<TE>) clazz;
      for (TE te : tec.getEnumConstants()) {
        terms.add(te);
      }
    }
    
    return terms;
  }

  @GET
  @Path("term/{prefix}:{name}")
  public Map<String, Object> term(@PathParam("prefix") String prefix, @PathParam("name") String name, @QueryParam("class") Boolean isClass) throws IllegalAccessException {
    String term = prefix + ":" + name;
    Term t = isClass == null ?
      TermFactory.instance().findTerm(term) :
      TermFactory.instance().findTerm(term, isClass);
    if (t instanceof UnknownTerm) {
      return null;
    }
    Map<String, Object> map = enumFields((Enum)t);
    map.put("prefixedName", t.prefixedName());
    map.put("qualifiedName", t.qualifiedName());
    map.remove("normQName");
    return map;
  }
  
  @GET
  @Path("language")
  public Map<String, String> languageTitles() {
    return Language.values().stream().collect(Collectors.toMap(Language::getCode, Language::getTitle));
  }
  
  @GET
  @Path("language/{code}")
  public String languageTitle(@PathParam("code") String code) {
    return Language.byCode(code).getTitle();
  }
  
  @GET
  @Path("geotime")
  public Collection<GeoTime> geotimes(@QueryParam("type") GeoTimeType type) {
    if (type == null) {
      return GeoTime.TIMES.values();
    }
    // filter by scale
    List<GeoTime> times = new ArrayList<>();
    for (GeoTime gt : GeoTime.TIMES.values()) {
      if (type.equals(gt.getType())) {
        times.add(gt);
      }
    }
    Collections.sort(times);
    return times;
  }
  
  @GET
  @Path("geotime/{name}")
  public GeoTime geotime(@PathParam("name") String name) {
    return GeoTime.byName(name);
  }
  
  @GET
  @Path("geotime/{name}/children")
  public List<GeoTime> geotimeChildren(@PathParam("name") String name) {
    GeoTime time = GeoTime.byName(name);
    if (time == null) {
      throw new life.catalogue.api.exception.NotFoundException(GeoTime.class, name);
    }
    List<GeoTime> children = new ArrayList<>();
    for (GeoTime gt : GeoTime.TIMES.values()) {
      if (gt.getParent() != null && gt.getParent().equals(time)) {
        children.add(gt);
      }
    }
    Collections.sort(children);
    return children;
  }

  @GET
  @Path("area")
  public Collection<?> areas(@QueryParam("gazetteer") Gazetteer gazetteer) throws IllegalAccessException {
    if (gazetteer == null) {
      throw new IllegalArgumentException("gazetteer parameter required");
    }
    // we only support a few gazetteers now
    switch (gazetteer) {
      case ISO:
        return enumList(Country.class);
      case TDWG:
        return TdwgArea.AREAS;
      case LONGHURST:
        return LonghurstArea.AREAS;
      default:
        throw new NotFoundException(gazetteer + " enumeration not available");
    }
  }

  @GET
  @Path("area/{id}")
  public Optional<? extends Area> area(@PathParam("id") String id) throws UnparsableException {
    return AreaParser.PARSER.parse(id);
  }


  @GET
  @Path("{vocab}")
  public List<Map<String, Object>> values(@PathParam("vocab") String vocab) throws IllegalAccessException {
    if (vocab != null && vocabs.containsKey(vocab.toLowerCase())) {
      return enumList(vocabs.get(vocab.toLowerCase()));
    }
    throw new NotFoundException();
  }

  @GET
  @Path("{vocab}/{name}")
  public Optional<Map<String, Object>> value(@PathParam("vocab") String vocab, @PathParam("vocab") String name) {
    if (vocabs.containsKey(vocab.toLowerCase())) {
      return VocabularyUtils.lookup(name, vocabs.get(vocab.toLowerCase()))
        .map(VocabResource::enumFields);
    }
    throw new NotFoundException();
  }

  @GET
  @Path("country/{code}")
  public Optional<Map<String, Object>> country(@PathParam("code") String code) {
    return Country.fromIsoCode(code)
      .map(VocabResource::enumFields);
  }

  private static Map<String, Object> enumFields(Enum entry) {
    Map<String, Object> map = new HashMap<>();
    try {
      for (Field f : entry.getDeclaringClass().getDeclaredFields()) {
        if (!f.isEnumConstant() && !Modifier.isStatic(f.getModifiers()) && !f.getName().equals("$VALUES")) {
          Object val = null;
            val = FieldUtils.readField(f, entry, true);
          if (val != null) {
            if (val instanceof Class) {
              Class<?> cl = (Class<?>) val;
              val = cl.getSimpleName();
            }
          }
          map.put(f.getName(), val);
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    map.put("name", PermissiveEnumSerde.enumValueName(entry));
    return map;
  }

  private static List<Map<String, Object>> enumList(Class<? extends Enum> clazz) throws IllegalAccessException {
    List<Map<String, Object>> values = new ArrayList<>();
    for (Enum entry : clazz.getEnumConstants()) {
      Map<String, Object> map = enumFields(entry);
      values.add(map);
    }
    return values;
  }
  
  private static String binaryName(Class clazz) {
    if (clazz.isMemberClass()) {
      return StringUtils.substringAfterLast(clazz.getName(),".").toLowerCase();
      
    } else {
      return clazz.getSimpleName().toLowerCase();
    }
  }
}
