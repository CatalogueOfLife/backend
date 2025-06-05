package life.catalogue.release;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.dao.DatasetSourceDao;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

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
   * Appends a list of unique and sorted source authors, i.e. creators or editors, to the creator list of a given release dataset.
   * Sources are taken from the project.
   * @param d dataset to append authors to and to take contributors from
   * @param cfg metadata configs from the release
   * @return true if source authors were added
   */
  public boolean appendSourceAuthors(Dataset d, ProjectReleaseConfig.MetadataConfig cfg) {
    if (!cfg.addSourceAuthors && !cfg.addSourceContributors) {
      return false;
    }
    var exclusion = new HashSet<>();
    if (cfg.authorSourceExclusion != null) {
      exclusion.addAll(cfg.authorSourceExclusion);
    }
    var sources = dao.listSimple(d.getKey(), true, false);
    // prepare unique agents for appending to release
    final List<Agent> agents = new ArrayList<>();
    // add some configured authors in alphabetical order
    if (cfg.additionalCreators != null) {
      agents.addAll(cfg.additionalCreators);
    }
    sources.stream()
      .filter(src -> !exclusion.contains(src.getType()))
      .forEach(src -> {
      if (src.getCreator() != null) {
        agents.addAll(addSourceNote(src, src.getCreator()));
      }
      if (src.getEditor() != null) {
        agents.addAll(addSourceNote(src, src.getEditor()));
      }
    });
    // remove same authors and merge information
    LinkedList<Agent> uniq = new LinkedList<>();
    for (Agent a : agents) {
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
      uniq.forEach(a -> {
        // move sources to unique notes
        if (a instanceof SrcAgent) {
          ((SrcAgent) a).addSourcesToNptes();
        }
        a.validateAndNullify(validator);
      });
      // now append them to already existing creators
      if (cfg.addSourceAuthors) {
        d.setCreator(append(d.getCreator(), uniq));
      }
      if (cfg.addSourceContributors) {
        d.setContributor(append(d.getContributor(), uniq));
      }
      return true;
    }
    return false;
  }

  private static List<Agent> append(List<Agent> existing, List<Agent> toAppend) {
    List<Agent> result = new ArrayList<>(toAppend);
    if (existing != null) {
      // merge notes from existing agents before appending
      for (Agent c : existing) {
        var iter = result.iterator();
        while (iter.hasNext()) {
          Agent a = iter.next();
          if (c.sameAs(a)) {
            c.addNote(a.getNote());
            iter.remove(); // remove duplicate
            break;
          }
        }
      }
      result.addAll(0, existing);
    }
    return result;
  }

  private static List<SrcAgent> addSourceNote(Dataset d, List<Agent> agents) {
    return agents.stream()
      .map(a -> new SrcAgent(a, d.getAliasOrTitle()))
      .collect(Collectors.toUnmodifiableList());
  }

  private static class SrcAgent extends Agent {
    Set<String> sources = new HashSet<>();

    public SrcAgent(Agent other, String source) {
      super(other);
      if (source != null) {
        this.sources.add(source);
      }
    }

    @Override
    public void merge(Agent addition) {
      super.merge(addition);
      if (addition instanceof SrcAgent) {
        sources.addAll(((SrcAgent) addition).sources);
      }
    }

    public void addSourcesToNptes() {
      List<String> srcs = new ArrayList<>(sources);
      Collections.sort(srcs);
      addNote(String.join(", ", srcs));
    }
  }

}
