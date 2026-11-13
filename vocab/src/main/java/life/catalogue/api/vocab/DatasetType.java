package life.catalogue.api.vocab;

/**
 * The kind of checklist a dataset is.
 */
public enum DatasetType implements Described {

  NOMENCLATURAL("A list of names focussing on nomenclature and not dealing with taxonomy."),

  TAXONOMIC("A checklist with some taxonomic assertion. Can be global or regional and does not have to be complete."),

  PHYLOGENETIC("A taxonomic checklist with the primary focus on producing a phylogenetic tree."),

  ARTICLE("A dataset representing taxonomic treatments of a single scientific article. " +
    "Mostly published through Plazi or Pensoft at this stage. Subtype of TAXONOMIC."),

  LEGAL("A checklist which is part of some legal document."),

  THEMATIC("A dataset focussed on a specific theme like red lists, invasiveness, traits, species interactions, etc."),

  IDENTIFICATION("A dataset with the focus on being a species identification resource, e.g. keys."),

  OTHER("A dataset that fits none of the other types.");

  private final String description;

  DatasetType(String description) {
    this.description = description;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
