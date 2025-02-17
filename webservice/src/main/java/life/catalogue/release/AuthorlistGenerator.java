package life.catalogue.release;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.dao.DatasetSourceDao;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import scala.reflect.internal.util.Set;

public class AuthorlistGenerator {
  private final Validator validator;
  private final DatasetSourceDao dao;
  public AuthorlistGenerator(DatasetSourceDao dao) {
    this(Validation.buildDefaultValidatorFactory().getValidator(), dao);
  }

  public AuthorlistGenerator(Validator validator, DatasetSourceDao dao) {
    this.validator = validator;
    this.dao = dao;
  }

  /**
   * Appends a list of unique and sorted source authors to the creator list of a given release dataset.
   * Sources are taken from the project.
   * @param d dataset to append authors to and to take contributors from
   * @param ds settings to consider for appending
   * @return true if source authors were added
   */
  public boolean appendSourceAuthors(Dataset d, DatasetSettings ds) {
    if (!ds.isEnabled(Setting.RELEASE_ADD_SOURCE_AUTHORS) && !ds.isEnabled(Setting.RELEASE_ADD_CONTRIBUTORS)) {
      return false;
    }
    var exclusion = new HashSet<>();
    if (ds.has(Setting.RELEASE_AUTHOR_SOURCE_EXCLUSION)) {
      List<DatasetType> types = ds.getList(Setting.RELEASE_AUTHOR_SOURCE_EXCLUSION);
      exclusion.addAll(types);
    }
    var sources = dao.listSimple(d.getKey(), true);
    // append authors for release?
    final List<Agent> authors = new ArrayList<>();
    if (ds.isEnabled(Setting.RELEASE_ADD_SOURCE_AUTHORS)) {
      sources.stream()
        .filter(src -> !exclusion.contains(src.getType()))
        .forEach(src -> {
        if (src.getCreator() != null) {
          authors.addAll(addSourceNote(src, src.getCreator()));
        }
        if (src.getEditor() != null) {
          authors.addAll(addSourceNote(src, src.getEditor()));
        }
      });
    }
    if (ds.isEnabled(Setting.RELEASE_ADD_CONTRIBUTORS) && d.getContributor() != null) {
      authors.addAll(d.getContributor());
      // remove contributors from release now that they are part of the creators
      d.setContributor(Collections.EMPTY_LIST);
    }
    // remove same authors and merge information
    LinkedList<Agent> uniq = new LinkedList<>();
    for (Agent a : authors) {
      if (a != null) {
        boolean add = true;
        for (Agent old : uniq) {
          if (old.sameAs(a)) {
            old.merge(a);
            add = false;
            break;
          }
        }
        if (add) {
          uniq.add(a);
        }
      }
    }
    // sort them alphabetically
    if (!uniq.isEmpty()) {
      Collections.sort(uniq);
      // verify emails as they can break validation on insert
      uniq.forEach(a -> a.validateAndNullify(validator));
      // now append them to already existing creators
      if (d.getCreator() != null) {
        // merge notes from existing agents before appending
        for (Agent c : d.getCreator()) {
          var iter = uniq.iterator();
          while (iter.hasNext()) {
            Agent a = iter.next();
            if (c.sameAs(a)) {
              c.addNote(a.getNote());
              iter.remove(); // remove duplicate
              break;
            }
          }
        }
        uniq.addAll(0, d.getCreator());
      }
      d.setCreator(uniq);
      return true;
    }
    return false;
  }

  private static List<Agent> addSourceNote(Dataset d, List<Agent> agents) {
    agents.forEach(a -> a.addNote(d.getAliasOrTitle()));
    return agents;
  }

}
