package org.col.resources;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import org.apache.commons.lang3.StringUtils;
import org.col.api.model.ColUser;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Sector;
import org.col.api.vocab.AreaStandard;
import org.col.img.ImgConfig;
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
  @Path("{name}")
  public Enum[] values(@PathParam("name") String name) {
    if (name != null && vocabs.containsKey(name.toLowerCase())) {
      return vocabs.get(name.toLowerCase()).getEnumConstants();
    }
    throw new NotFoundException();
  }
  
  private static String binaryName(Class clazz) {
    if (clazz.isMemberClass()) {
      return StringUtils.substringAfterLast(clazz.getName(),".").toLowerCase();
      
    } else {
      return clazz.getSimpleName().toLowerCase();
    }
  }
}
