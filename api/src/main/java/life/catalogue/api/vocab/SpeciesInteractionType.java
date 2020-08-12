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

  RELATED_TO("http://purl.obolibrary.org/obo/RO_0002321", "Ecologically related to"),

  CO_OCCURS_WITH("http://purl.obolibrary.org/obo/RO_0008506", "",
    RELATED_TO),

  INTERACTS_WITH("http://purl.obolibrary.org/obo/RO_0002437", "Generic interaction relation",
    CO_OCCURS_WITH),

  ADJACENT_TO("http://purl.obolibrary.org/obo/RO_0002220", "",
    CO_OCCURS_WITH),

  SYMBIONT_OF("http://purl.obolibrary.org/obo/RO_0002440", "",
    INTERACTS_WITH),



  // ***
  // *** directional interactions with inverse
  // ***

  /**
   * Herbivores, fungivores, predators or other forms of organims eating or feeding on the related taxon.
   */
  EATS("http://purl.obolibrary.org/obo/RO_0002470", "",
    INTERACTS_WITH),
  EATEN_BY("http://purl.obolibrary.org/obo/RO_0002471", "",
    INTERACTS_WITH),

  KILLS("http://purl.obolibrary.org/obo/RO_0002626", "",
    INTERACTS_WITH),
  KILLED_BY("http://purl.obolibrary.org/obo/RO_0002627", "",
    INTERACTS_WITH),

  PREYS_UPON("http://purl.obolibrary.org/obo/RO_0002439", "An interaction relationship involving a predation process, " +
    "where the subject kills the object in order to eat it or to feed to siblings, offspring or group members",
    EATS, KILLS),
  PREYED_UPON_BY("http://purl.obolibrary.org/obo/RO_0002458", "Inverse of preys upon",
    EATEN_BY, KILLED_BY),

  /**
   * E.g. a host plant for some herbivore.
   */
  HOST_OF("http://purl.obolibrary.org/obo/RO_0002453", "",
    SYMBIONT_OF),
  HAS_HOST("http://purl.obolibrary.org/obo/RO_0002454", "",
    SYMBIONT_OF),

  PARASITE_OF("http://purl.obolibrary.org/obo/RO_0002444", "",
    EATS, HAS_HOST),
  HAS_PARASITE("http://purl.obolibrary.org/obo/RO_0002445", "",
    EATEN_BY, HOST_OF),

  PATHOGEN_OF("http://purl.obolibrary.org/obo/RO_0002556", "",
    PARASITE_OF),
  HAS_PATHOGEN("http://purl.obolibrary.org/obo/RO_0002557", "",
    HAS_PARASITE),

  VECTOR_OF("http://purl.obolibrary.org/obo/RO_0002459", "",
    HOST_OF),
  HAS_VECTOR("http://purl.obolibrary.org/obo/RO_0002460", "",
    HAS_HOST),

  ENDOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002634", "",
    PARASITE_OF),
  HAS_ENDOPARASITE("http://purl.obolibrary.org/obo/RO_0002635", "",
    HAS_PARASITE),

  ECTOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002632", "",
    PARASITE_OF),
  HAS_ECTOPARASITE("http://purl.obolibrary.org/obo/RO_0002633", "",
    HAS_PARASITE),

  HYPERPARASITE_OF("http://purl.obolibrary.org/obo/RO_0002553", "",
    PARASITE_OF),
  HAS_HYPERPARASITE("http://purl.obolibrary.org/obo/RO_0002554", "",
    HAS_PARASITE),

  KLEPTOPARASITE_OF("http://purl.obolibrary.org/obo/RO_0008503", "",
    PARASITE_OF),
  HAS_KLEPTOPARASITE("http://purl.obolibrary.org/obo/RO_0008503", "",
    HAS_PARASITE),

  PARASITOID_OF("http://purl.obolibrary.org/obo/RO_0002208", "",
    PARASITE_OF),
  HAS_PARASITOID("http://purl.obolibrary.org/obo/RO_0002209", "",
    HAS_PARASITE),

  HYPERPARASITOID_OF("http://purl.obolibrary.org/obo/RO_0002553", "",
    PARASITOID_OF),
  HAS_HYPERPARASITOID("http://purl.obolibrary.org/obo/RO_0002554", "",
    HAS_PARASITOID),

  VISITS("http://purl.obolibrary.org/obo/RO_0002618", "",
    HAS_HOST),
  VISITED_BY("http://purl.obolibrary.org/obo/RO_0002619", "",
    HOST_OF),

  VISITS_FLOWERS_OF("http://purl.obolibrary.org/obo/RO_0002622", "",
    VISITS),
  FLOWERS_VISITED_BY("http://purl.obolibrary.org/obo/RO_0002623", "",
    VISITED_BY),

  POLLINATES("http://purl.obolibrary.org/obo/RO_0002455", "",
    VISITS_FLOWERS_OF),
  POLLINATED_BY("http://purl.obolibrary.org/obo/RO_0002456", "",
    FLOWERS_VISITED_BY),

  LAYS_EGGS_ON("http://purl.obolibrary.org/obo/RO_0008507", "",
    HAS_HOST),
  HAS_EGGS_LAYED_ON_BY("http://purl.obolibrary.org/obo/RO_0008508", "",
    HOST_OF),

  EPIPHYTE_OF("http://purl.obolibrary.org/obo/RO_0008501", "",
    SYMBIONT_OF),
  HAS_EPIPHYTE("http://purl.obolibrary.org/obo/RO_0008502", "",
    SYMBIONT_OF),

  COMMENSALIST_OF("http://purl.obolibrary.org/obo/RO_0002441", "",
    SYMBIONT_OF),
  MUTUALIST_OF("http://purl.obolibrary.org/obo/RO_0002442", "",
    SYMBIONT_OF);


  static final Map<SpeciesInteractionType, SpeciesInteractionType> INVERSE = Map.ofEntries(
    // bidirectional
    Map.entry(RELATED_TO, RELATED_TO),
    Map.entry(CO_OCCURS_WITH, CO_OCCURS_WITH),
    Map.entry(INTERACTS_WITH, INTERACTS_WITH),
    Map.entry(ADJACENT_TO, ADJACENT_TO),
    Map.entry(SYMBIONT_OF, SYMBIONT_OF),
    // directional
    Map.entry(EATS, EATEN_BY),
    Map.entry(EATEN_BY, EATS),
    Map.entry(KILLS, KILLED_BY),
    Map.entry(KILLED_BY, KILLS),
    Map.entry(PREYS_UPON, PREYED_UPON_BY),
    Map.entry(PREYED_UPON_BY, PREYS_UPON),
    Map.entry(HOST_OF, HOST_OF),
    Map.entry(HAS_HOST, HAS_HOST),
    Map.entry(PARASITE_OF, PARASITE_OF),
    Map.entry(HAS_PARASITE, HAS_PARASITE),
    Map.entry(PATHOGEN_OF, PATHOGEN_OF),
    Map.entry(HAS_PATHOGEN, HAS_PATHOGEN),
    Map.entry(VECTOR_OF, VECTOR_OF),
    Map.entry(HAS_VECTOR, HAS_VECTOR),
    Map.entry(ENDOPARASITE_OF, ENDOPARASITE_OF),
    Map.entry(HAS_ENDOPARASITE, HAS_ENDOPARASITE),
    Map.entry(ECTOPARASITE_OF, ECTOPARASITE_OF),
    Map.entry(HAS_ECTOPARASITE, HAS_ECTOPARASITE),
    Map.entry(HYPERPARASITE_OF, HYPERPARASITE_OF),
    Map.entry(HAS_HYPERPARASITE, HAS_HYPERPARASITE),
    Map.entry(KLEPTOPARASITE_OF, KLEPTOPARASITE_OF),
    Map.entry(HAS_KLEPTOPARASITE, HAS_KLEPTOPARASITE),
    Map.entry(PARASITOID_OF, PARASITOID_OF),
    Map.entry(HAS_PARASITOID, HAS_PARASITOID),
    Map.entry(HYPERPARASITOID_OF, HYPERPARASITOID_OF),
    Map.entry(HAS_HYPERPARASITOID, HAS_HYPERPARASITOID),
    Map.entry(VISITS, VISITS),
    Map.entry(VISITED_BY, VISITED_BY),
    Map.entry(VISITS_FLOWERS_OF, VISITS_FLOWERS_OF),
    Map.entry(FLOWERS_VISITED_BY, FLOWERS_VISITED_BY),
    Map.entry(POLLINATES, POLLINATES),
    Map.entry(POLLINATED_BY, POLLINATED_BY),
    Map.entry(LAYS_EGGS_ON, LAYS_EGGS_ON),
    Map.entry(HAS_EGGS_LAYED_ON_BY, HAS_EGGS_LAYED_ON_BY),
    Map.entry(EPIPHYTE_OF, EPIPHYTE_OF),
    Map.entry(HAS_EPIPHYTE, HAS_EPIPHYTE),
    Map.entry(COMMENSALIST_OF, COMMENSALIST_OF),
    Map.entry(MUTUALIST_OF, MUTUALIST_OF)
  );

  private final SpeciesInteractionType[] superTypes;
  private final URI obo;
  private final String documentation;


  SpeciesInteractionType(String obo, String documentation, SpeciesInteractionType... superTypes) {
    this.obo = URI.create(obo);
    this.documentation = documentation;
    this.superTypes = superTypes;
  }

  public SpeciesInteractionType[] getSuperTypes() {
    return superTypes;
  }

  public URI getObo() {
    return obo;
  }

  public String getDocumentation() {
    return documentation;
  }

  public SpeciesInteractionType getInverse() {
    return INVERSE.get(this);
  }

  boolean isBidirectional() {
    return this == INVERSE.get(this);
  }
}
