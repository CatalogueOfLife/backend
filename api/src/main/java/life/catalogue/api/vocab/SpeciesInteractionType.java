/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.api.vocab;

import java.net.URI;
import java.util.Map;

/**
 * Vocabulary classifying the kind of species interactions which
 * are inspired by https://www.globalbioticinteractions.org
 * and the OBO Relation Ontology http://www.ontobee.org/ontology/RO.
 *
 * Species interaction relations and their types are directed and for each type there is a corresponding reverse type.
 *
 * See also https://github.com/globalbioticinteractions/globalbioticinteractions/tree/main/eol-globi-ontology
 * and https://github.com/globalbioticinteractions/globalbioticinteractions/blob/main/eol-globi-lib/src/main/java/org/eol/globi/domain/InteractType.java
 */
public enum SpeciesInteractionType {

  /**
   * Alternatives for parser
   *
   * PREYS_ON PREYS_UPON
   * PREYED_ON_BY PREYED_UPON_BY
   * has predator PREYED_UPON_BY
   * parasitizes PARASITE_OF
   *
   * EOL:
   * HasDispersalVector HAS_VECTOR
   * DispersalVector VECTOR_OF
   * FlowersVisitedBy FLOWERS_VISITED_BY
   * isKilledBy KILLED_BY
   * emergedFrom INTERACTS_WITH
   */

  // ***
  // *** bidirectional interactions
  // ***

  RELATED_TO("http://purl.obolibrary.org/obo/RO_0002321",
    "Ecologically related to"),

  CO_OCCURS_WITH("http://purl.obolibrary.org/obo/RO_0008506",
    "An interaction relationship describing organisms that often occur together at the same time and space or in the same environment.",
    RELATED_TO),

  INTERACTS_WITH("http://purl.obolibrary.org/obo/RO_0002437",
    "An interaction relationship in which at least one of the partners is an organism and the other is either an organism " +
      "or an abiotic entity with which the organism interacts.",
    CO_OCCURS_WITH),

  ADJACENT_TO("http://purl.obolibrary.org/obo/RO_0002220",
    "X adjacent to y if and only if x and y share a boundary.",
    CO_OCCURS_WITH),

  SYMBIONT_OF("http://purl.obolibrary.org/obo/RO_0002440",
    "A symbiotic relationship, a more or less intimate association, with another organism. " +
      "The various forms of symbiosis include parasitism, in which the association is disadvantageous or destructive to one of the organisms; " +
      "mutualism, in which the association is advantageous, or often necessary to one or both and not harmful to either; " +
      "and commensalism, in which one member of the association benefits while the other is not affected. " +
      "However, mutualism, parasitism, and commensalism are often not discrete categories of interactions " +
      "and should rather be perceived as a continuum of interaction ranging from parasitism to mutualism. " +
      "In fact, the direction of a symbiotic interaction can change during the lifetime of the symbionts " +
      "due to developmental changes as well as changes in the biotic/abiotic environment in which the interaction occurs. ",
    INTERACTS_WITH),



  // ***
  // *** directional interactions with inverse
  // ***

  EATS("http://purl.obolibrary.org/obo/RO_0002470",
    "Herbivores, fungivores, predators or other forms of organims eating or feeding on the related taxon.",
    INTERACTS_WITH),
  EATEN_BY("http://purl.obolibrary.org/obo/RO_0002471",
    "Inverse of eats",
    INTERACTS_WITH),

  KILLS("http://purl.obolibrary.org/obo/RO_0002626",
    null,
    INTERACTS_WITH),
  KILLED_BY("http://purl.obolibrary.org/obo/RO_0002627",
    "Inverse of kills",
    INTERACTS_WITH),

  PREYS_UPON("http://purl.obolibrary.org/obo/RO_0002439",
    "An interaction relationship involving a predation process, " +
    "where the subject kills the object in order to eat it or to feed to siblings, offspring or group members",
    EATS, KILLS),
  PREYED_UPON_BY("http://purl.obolibrary.org/obo/RO_0002458",
    "Inverse of preys upon",
    EATEN_BY, KILLED_BY),

  /**
   * E.g. a host plant for some herbivore.
   */
  HOST_OF("http://purl.obolibrary.org/obo/RO_0002453",
    "The term host is usually used for the larger (macro) of the two members of a symbiosis",
    SYMBIONT_OF),
  HAS_HOST("http://purl.obolibrary.org/obo/RO_0002454",
    "Inverse of host of",
    SYMBIONT_OF),

  PARASITE_OF("http://purl.obolibrary.org/obo/RO_0002444",
    null,
    EATS, HAS_HOST),
  HAS_PARASITE("http://purl.obolibrary.org/obo/RO_0002445",
    "Inverse of parasite of",
    EATEN_BY, HOST_OF),

  PATHOGEN_OF("http://purl.obolibrary.org/obo/RO_0002556",
    null,
    PARASITE_OF),
  HAS_PATHOGEN("http://purl.obolibrary.org/obo/RO_0002557",
    "Inverse of pathogen of",
    HAS_PARASITE),

  VECTOR_OF("http://purl.obolibrary.org/obo/RO_0002459",
    "a is a vector for b if a carries and transmits an infectious pathogen b into another living organism",
    HOST_OF),
  HAS_VECTOR("http://purl.obolibrary.org/obo/RO_0002460",
    "Inverse of vector of",
    HAS_HOST),

  ENDOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002634",
    "A sub-relation of parasite-of in which the parasite lives inside the host, beneath the integumental system",
    PARASITE_OF),
  HAS_ENDOPARASITE("http://purl.obolibrary.org/obo/RO_0002635",
    "Inverse of endoparasite of",
    HAS_PARASITE),

  ECTOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002632",
    "A sub-relation of parasite-of in which the parasite lives on or in the integumental system of the host",
    PARASITE_OF),
  HAS_ECTOPARASITE("http://purl.obolibrary.org/obo/RO_0002633",
    "Inverse of ectoparasite of",
    HAS_PARASITE),

  HYPERPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002553",
    "X is a hyperparasite of y iff x is a parasite of a parasite of the target organism y",
    PARASITE_OF),
  HAS_HYPERPARASITE("http://purl.obolibrary.org/obo/RO_0002554",
    "Inverse of hyperparasite of",
    HAS_PARASITE),

  KLEPTOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0008503",
    "A sub-relation of parasite of in which a parasite steals resources from another organism, usually food or nest material",
    PARASITE_OF),
  HAS_KLEPTOPARASITE("http://purl.obolibrary.org/obo/RO_0008503",
    "Inverse of kleptoparasite of",
    HAS_PARASITE),

  PARASITOID_OF("http://purl.obolibrary.org/obo/RO_0002208",
    "A parasite that kills or sterilizes its host",
    PARASITE_OF),
  HAS_PARASITOID("http://purl.obolibrary.org/obo/RO_0002209",
    "Inverse of parasitoid of",
    HAS_PARASITE),

  HYPERPARASITOID_OF("http://purl.obolibrary.org/obo/RO_0002553",
    "X is a hyperparasite of y if x is a parasite of a parasite of the target organism y",
    PARASITOID_OF),
  HAS_HYPERPARASITOID("http://purl.obolibrary.org/obo/RO_0002554",
    "Inverse of hyperparasitoid of",
    HAS_PARASITOID),

  VISITS("http://purl.obolibrary.org/obo/RO_0002618",
    null,
    HAS_HOST),
  VISITED_BY("http://purl.obolibrary.org/obo/RO_0002619",
    "Inverse of visits",
    HOST_OF),

  VISITS_FLOWERS_OF("http://purl.obolibrary.org/obo/RO_0002622",
    null,
    VISITS),
  FLOWERS_VISITED_BY("http://purl.obolibrary.org/obo/RO_0002623",
    "Inverse of visits flowers of",
    VISITED_BY),

  POLLINATES("http://purl.obolibrary.org/obo/RO_0002455",
    "This relation is intended to be used for biotic pollination - e.g. a bee pollinating a flowering plant. ",
    VISITS_FLOWERS_OF),
  POLLINATED_BY("http://purl.obolibrary.org/obo/RO_0002456",
    "Inverse of pollinates",
    FLOWERS_VISITED_BY),

  LAYS_EGGS_ON("http://purl.obolibrary.org/obo/RO_0008507",
    "An interaction relationship in which organism a lays eggs on the outside surface of organism b. " +
      "Organism b is neither helped nor harmed in the process of egg laying or incubation.",
    HAS_HOST),
  HAS_EGGS_LAYED_ON_BY("http://purl.obolibrary.org/obo/RO_0008508",
    "Inverse of lays eggs on",
    HOST_OF),

  EPIPHYTE_OF("http://purl.obolibrary.org/obo/RO_0008501",
    "An interaction relationship wherein a plant or algae is living on the outside surface of another plant.",
    SYMBIONT_OF),
  HAS_EPIPHYTE("http://purl.obolibrary.org/obo/RO_0008502",
    "Inverse of epiphyte of",
    SYMBIONT_OF),

  COMMENSALIST_OF("http://purl.obolibrary.org/obo/RO_0002441",
    "An interaction relationship between two organisms living together in more or less intimate association in a relationship " +
      "in which one benefits and the other is unaffected (GO).",
    SYMBIONT_OF),

  MUTUALIST_OF("http://purl.obolibrary.org/obo/RO_0002442",
    "An interaction relationship between two organisms living together in more or less intimate association in a relationship " +
      "in which both organisms benefit from each other (GO).",
    SYMBIONT_OF);


  static final Map<SpeciesInteractionType, SpeciesInteractionType> INVERSE = Map.ofEntries(
    // symmetric
    Map.entry(RELATED_TO, RELATED_TO),
    Map.entry(CO_OCCURS_WITH, CO_OCCURS_WITH),
    Map.entry(INTERACTS_WITH, INTERACTS_WITH),
    Map.entry(ADJACENT_TO, ADJACENT_TO),
    Map.entry(SYMBIONT_OF, SYMBIONT_OF),
    Map.entry(MUTUALIST_OF, MUTUALIST_OF),
    //TODO: not really symmetric, but OBO and GLOBI lacks an inverse
    Map.entry(COMMENSALIST_OF, COMMENSALIST_OF),
    // asymmetric
    Map.entry(EATS, EATEN_BY),
    Map.entry(EATEN_BY, EATS),
    Map.entry(KILLS, KILLED_BY),
    Map.entry(KILLED_BY, KILLS),
    Map.entry(PREYS_UPON, PREYED_UPON_BY),
    Map.entry(PREYED_UPON_BY, PREYS_UPON),
    Map.entry(HOST_OF, HAS_HOST),
    Map.entry(HAS_HOST, HOST_OF),
    Map.entry(PARASITE_OF, HAS_PARASITE),
    Map.entry(HAS_PARASITE, PARASITE_OF),
    Map.entry(PATHOGEN_OF, HAS_PATHOGEN),
    Map.entry(HAS_PATHOGEN, PATHOGEN_OF),
    Map.entry(VECTOR_OF, HAS_VECTOR),
    Map.entry(HAS_VECTOR, VECTOR_OF),
    Map.entry(ENDOPARASITE_OF, HAS_ENDOPARASITE),
    Map.entry(HAS_ENDOPARASITE, ENDOPARASITE_OF),
    Map.entry(ECTOPARASITE_OF, HAS_ECTOPARASITE),
    Map.entry(HAS_ECTOPARASITE, ECTOPARASITE_OF),
    Map.entry(HYPERPARASITE_OF, HAS_HYPERPARASITE),
    Map.entry(HAS_HYPERPARASITE, HYPERPARASITE_OF),
    Map.entry(KLEPTOPARASITE_OF, HAS_KLEPTOPARASITE),
    Map.entry(HAS_KLEPTOPARASITE, KLEPTOPARASITE_OF),
    Map.entry(PARASITOID_OF, HAS_PARASITOID),
    Map.entry(HAS_PARASITOID, PARASITOID_OF),
    Map.entry(HYPERPARASITOID_OF, HAS_HYPERPARASITOID),
    Map.entry(HAS_HYPERPARASITOID, HYPERPARASITOID_OF),
    Map.entry(VISITS, VISITED_BY),
    Map.entry(VISITED_BY, VISITS),
    Map.entry(VISITS_FLOWERS_OF, FLOWERS_VISITED_BY),
    Map.entry(FLOWERS_VISITED_BY, VISITS_FLOWERS_OF),
    Map.entry(POLLINATES, POLLINATED_BY),
    Map.entry(POLLINATED_BY, POLLINATES),
    Map.entry(LAYS_EGGS_ON, HAS_EGGS_LAYED_ON_BY),
    Map.entry(HAS_EGGS_LAYED_ON_BY, LAYS_EGGS_ON),
    Map.entry(EPIPHYTE_OF, HAS_EPIPHYTE),
    Map.entry(HAS_EPIPHYTE, EPIPHYTE_OF)
  );

  private final SpeciesInteractionType[] superTypes;
  private final URI obo;
  private final String description;


  SpeciesInteractionType(String obo, String description, SpeciesInteractionType... superTypes) {
    this.obo = URI.create(obo);
    this.description = description;
    this.superTypes = superTypes;
  }

  public SpeciesInteractionType[] getSuperTypes() {
    return superTypes;
  }

  public URI getObo() {
    return obo;
  }

  public String getDescription() {
    return description;
  }

  public SpeciesInteractionType getInverse() {
    return INVERSE.get(this);
  }

  public boolean isSymmetric() {
    return this == INVERSE.get(this);
  }
}
