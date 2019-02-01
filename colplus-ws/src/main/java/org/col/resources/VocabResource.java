package org.col.resources;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.col.api.jackson.ApiModule;
import org.col.api.model.ColUser;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.vocab.AreaStandard;
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
  
  public VocabResource() {
    Map<String, Class<Enum>> enums = Maps.newHashMap();
    try {
      for (Package p : Lists.newArrayList(AreaStandard.class.getPackage(), Rank.class.getPackage())) {
        LOG.debug("Scan package {} for enums", p);
        for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(p.getName())) {
          add(enums, info.load());
        }
      }
      
      for (Class<?> clazz : ImmutableList.of(ColUser.Role.class, ImgConfig.Scale.class, EditorialDecision.Mode.class, Sector.Mode.class)) {
        add(enums, clazz);
      }
      
    } catch (IOException e) {
      LOG.error("Failed to init enum class map", e);
    }
    vocabs = ImmutableMap.copyOf(enums);
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
  public Set<String> list() {
    return vocabs.keySet();
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
              sval = ApiModule.enumValueName((Enum)val);
            } else {
              sval = val.toString();
            }
          }
          map.put(f.getName(), sval);
        }
      }
      map.put("name", ApiModule.enumValueName(entry));
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
