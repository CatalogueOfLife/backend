package life.catalogue.matching.person;

import life.catalogue.api.event.PersonListener;
import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.config.PersonConfig;
import life.catalogue.db.mapper.PersonMapper;

import org.gbif.nameparser.api.NomCode;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Lists;

/**
 * The person registry in Postgres. Two bounded caches keep what was asked for: the person ids of a key under a code, and
 * the person of any id. A miss is one indexed select. Both are cleared when the registry announces a change, and every
 * entry expires after a while in case an announcement was missed.
 */
public class PgPersonStore implements PersonStore, PersonListener {
  private static final int BATCH = 1000;
  private final SqlSessionFactory factory;
  private final Cache<String, List<String>> idsByKey;
  private final LoadingCache<String, Person> persons;

  public PgPersonStore(SqlSessionFactory factory, PersonConfig cfg) {
    this.factory = factory;
    idsByKey = Caffeine.newBuilder()
      .maximumSize(cfg.keyCacheSize)
      .expireAfterWrite(cfg.cacheExpireMinutes, TimeUnit.MINUTES)
      .build();
    persons = Caffeine.newBuilder()
      .maximumSize(cfg.personCacheSize)
      .expireAfterWrite(cfg.cacheExpireMinutes, TimeUnit.MINUTES)
      .build(new CacheLoader<>() {
        @Override
        public @Nullable Person load(String anyId) {
          return loadAll(Set.of(anyId)).get(anyId);
        }

        @Override
        public Map<String, Person> loadAll(Set<? extends String> anyIds) {
          Map<String, Person> map = new HashMap<>();
          try (SqlSession session = factory.openSession(true)) {
            var mapper = session.getMapper(PersonMapper.class);
            List<String> ids = new ArrayList<>(anyIds);
            for (List<String> batch : Lists.partition(ids, BATCH)) {
              for (var r : mapper.getByAnyIds(batch)) {
                map.put(r.anyId, r.toPerson());
              }
            }
          }
          return map;
        }
      });
  }

  @Override
  @Nullable
  public Person get(String anyId) {
    return persons.get(anyId);
  }

  @Override
  public Set<Person> byKey(String key, @Nullable NomCode code) {
    return byKeys(List.of(key), code).getOrDefault(key, Set.of());
  }

  @Override
  public Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code) {
    boolean zoo = code == NomCode.ZOOLOGICAL;
    String prefix = zoo ? "Z|" : "B|";
    Map<String, List<String>> ids = new HashMap<>();
    List<String> missing = new ArrayList<>();
    for (String key : new LinkedHashSet<>(keys)) {
      List<String> cached = idsByKey.getIfPresent(prefix + key);
      if (cached == null) {
        missing.add(key);
      } else {
        ids.put(key, cached);
      }
    }
    if (!missing.isEmpty()) {
      Map<String, List<String>> loaded = new HashMap<>();
      missing.forEach(k -> loaded.put(k, new ArrayList<>(1)));
      try (SqlSession session = factory.openSession(true)) {
        var mapper = session.getMapper(PersonMapper.class);
        for (List<String> batch : Lists.partition(missing, BATCH)) {
          for (var r : mapper.idsByKeys(batch, zoo)) {
            loaded.get(r.key).add(r.personId);
          }
        }
      }
      // keys without persons are cached as well, a miss costs a select only once
      loaded.forEach((k, v) -> {
        idsByKey.put(prefix + k, List.copyOf(v));
        ids.put(k, v);
      });
    }
    Set<String> all = new HashSet<>();
    ids.values().forEach(all::addAll);
    Map<String, Person> byId = all.isEmpty() ? Map.of() : persons.getAll(all);
    Map<String, Set<Person>> result = new HashMap<>();
    ids.forEach((k, v) -> {
      Set<Person> ps = new LinkedHashSet<>();
      for (String id : v) {
        Person p = byId.get(id);
        if (p != null) {
          ps.add(p);
        }
      }
      if (!ps.isEmpty()) {
        result.put(k, ps);
      }
    });
    return result;
  }

  @Override
  public Set<Person> relatives(Person p) {
    List<String> ids;
    try (SqlSession session = factory.openSession(true)) {
      ids = session.getMapper(PersonMapper.class).relatives(p.id());
    }
    return ids.isEmpty() ? Set.of() : new LinkedHashSet<>(persons.getAll(ids).values());
  }

  @Override
  public Set<String> keys(Person p, @Nullable NomCode code) {
    try (SqlSession session = factory.openSession(true)) {
      return new LinkedHashSet<>(session.getMapper(PersonMapper.class).keys(p.id(), code == NomCode.ZOOLOGICAL));
    }
  }

  @Override
  @Nullable
  public PersonInfo info(String anyId) {
    Person p = get(anyId);
    if (p == null) return null;
    try (SqlSession session = factory.openSession(true)) {
      var mapper = session.getMapper(PersonMapper.class);
      return new PersonInfo(p,
        mapper.names(p.id()).stream().map(PersonMapper.NameRow::toName).toList(),
        mapper.relations(p.id()).stream().map(PersonMapper.RelationRow::toRelation).toList());
    }
  }

  @Override
  public void personsChanged(PersonsChanged event) {
    idsByKey.invalidateAll();
    persons.invalidateAll();
  }
}
