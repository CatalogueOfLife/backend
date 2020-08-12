package life.catalogue.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.vocab.*;
import life.catalogue.img.ImgConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

@Path("/vocab")
@Produces(MediaType.APPLICATION_JSON)
public class VocabResource {
  
  private static final Logger LOG = LoggerFactory.getLogger(VocabResource.class);
  private final Map<String, Class<Enum>> vocabs;
  private final List<String> vocabNames;
  
  public VocabResource() {
    Map<String, Class<Enum>> enums = Maps.newHashMap();
    try {
      for (Package p : Lists.newArrayList(DatasetOrigin.class.getPackage(), Rank.class.getPackage())) {
        LOG.debug("Scan package {} for enums", p);
        for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(p.getName())) {
          add(enums, info.load());
        }
      }
      
      for (Class<?> clazz : ImmutableList.of(
          User.Role.class,
          ImgConfig.Scale.class,
          EditorialDecision.Mode.class,
          Sector.Mode.class,
          NameUsageSearchParameter.class,
          TreeNode.Type.class)) {
        add(enums, clazz);
      }
      
    } catch (IOException e) {
      LOG.error("Failed to init enum class map", e);
    }
    vocabs = ImmutableMap.copyOf(enums);
    List<String> names = new ArrayList<>(enums.keySet());
    names.add("language");
    names.add("geotime");
    names.add("terms");
    names.remove(binaryName(ColDwcTerm.class));
    Collections.sort(names);
    vocabNames = ImmutableList.copyOf(names);
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
  @Path("terms")
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
  @Path("{name}")
  public List<Map<String, Object>> values(@PathParam("name") String name) throws IllegalAccessException {
    if (name != null && vocabs.containsKey(name.toLowerCase())) {
      return enumList(vocabs.get(name.toLowerCase()));
    }
    throw new NotFoundException();
  }
  
  private static List<Map<String, Object>> enumList(Class<Enum> clazz) throws IllegalAccessException {
    List<Map<String, Object>> values = new ArrayList<>();
    for (Enum entry : clazz.getEnumConstants()) {
      Map<String, Object> map = new HashMap<>();
      for (Field f : clazz.getDeclaredFields()) {
        if (!f.isEnumConstant() && !Modifier.isStatic(f.getModifiers()) && !f.getName().equals("$VALUES")) {
          Object val = FieldUtils.readField(f, entry, true);
          if (val != null) {
            if (val instanceof Class) {
              Class<?> cl = (Class<?>) val;
              val = cl.getSimpleName();
            }
          }
          map.put(f.getName(), val);
        }
      }
      map.put("name", PermissiveEnumSerde.enumValueName(entry));
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
