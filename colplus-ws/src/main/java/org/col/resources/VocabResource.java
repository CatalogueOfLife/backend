package org.col.resources;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import org.col.api.vocab.AreaStandard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/vocab")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class VocabResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(VocabResource.class);
  private final Map<String, Class<Enum<?>>> vocabs;

  public VocabResource() {
    Map<String, Class<Enum<?>>> enums = Maps.newHashMap();
    try {
      for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader()).getTopLevelClasses(AreaStandard.class.getPackage().getName())) {
        Class<?> clazz = info.load();
        if (clazz.isEnum()) {
          Class<Enum<?>> enumClazz = (Class<Enum<?>>) clazz;
          enums.put(enumClazz.getSimpleName().toLowerCase(), enumClazz);
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to init enum class map", e);
    }
    vocabs = ImmutableMap.copyOf(enums);
  }

  @GET
  public List<Class<Enum<?>>> list() {
    return Lists.newArrayList(vocabs.values());
  }

  @GET
  @Path("{name}")
  public List<String> values(@PathParam("name") String name) {
    if (name != null && vocabs.containsKey(name.toLowerCase())) {
      return Arrays.stream(vocabs.get(name.toLowerCase()).getEnumConstants())
          .map(Enum::name)
          .collect(Collectors.toList());
    }
    return Lists.newArrayList();
  }

}
