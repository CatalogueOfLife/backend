package org.col.resources;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.col.api.jackson.PermissiveEnumSerde;
import org.col.api.model.ColUser;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.model.SectorImport;
import org.col.api.search.NameSearchParameter;
import org.col.api.vocab.*;
import org.col.img.ImgConfig;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/vocab")
@Produces(MediaType.APPLICATION_JSON)
public class VocabResource {
  
  private static final Logger LOG = LoggerFactory.getLogger(VocabResource.class);
  private final Map<String, Class<Enum>> vocabs;
  private final List<String> vocabNames;
  
  public VocabResource() {
    Map<String, Class<Enum>> enums = Maps.newHashMap();
    try {
      for (Package p : Lists.newArrayList(AreaStandard.class.getPackage(), Rank.class.getPackage())) {
        LOG.debug("Scan package {} for enums", p);
        for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(p.getName())) {
          add(enums, info.load());
        }
      }
      
      for (Class<?> clazz : ImmutableList.of(
          ColUser.Role.class,
          ImgConfig.Scale.class,
          EditorialDecision.Mode.class,
          Sector.Mode.class,
          SectorImport.State.class,
          NameSearchParameter.class)) {
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
    return Language.LANGUAGES.values().stream().collect(Collectors.toMap(Language::getCode, Language::getTitle));
  }
  
  @GET
  @Path("language/{code}")
  public String languageTitle(@PathParam("code") String code) {
    return Language.byCode(code).getTitle();
  }
  
  @GET
  @Path("geotime")
  public Collection<GeoTime> geotimes(@QueryParam("scale") GeoTimeScale scale) {
    if (scale == null) {
      return GeoTime.TIMES.values();
    }
    // filter by scale
    List<GeoTime> times = new ArrayList<>();
    for (GeoTime gt : GeoTime.TIMES.values()) {
      if (scale.equals(gt.getScale())) {
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
      throw new org.col.api.exception.NotFoundException(GeoTime.class, name);
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
  public List<Map<String, String>> values(@PathParam("name") String name) throws IllegalAccessException {
    if (name != null && vocabs.containsKey(name.toLowerCase())) {
      return enumList(vocabs.get(name.toLowerCase()));
    }
    throw new NotFoundException();
  }
  
  private static List<Map<String, String>> enumList(Class<Enum> clazz) throws IllegalAccessException {
    List<Map<String, String>> values = new ArrayList<>();
    for (Enum entry : clazz.getEnumConstants()) {
      Map<String, String> map = new HashMap<>();
      for (Field f : clazz.getDeclaredFields()) {
        if (!f.isEnumConstant() && !Modifier.isStatic(f.getModifiers()) && !f.getName().equals("$VALUES")) {
          String sval = null;
          Object val = FieldUtils.readField(f, entry, true);
          if (val != null) {
            if (f.getType().isEnum()) {
              sval = PermissiveEnumSerde.enumValueName((Enum)val);
            } else {
              sval = val.toString();
            }
          }
          map.put(f.getName(), sval);
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
