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

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Vocabulary classifying the kind of taxon relation.
 * The enumeration combines classic RCC5 concept relations with species interactions which
 * are inspired by https://www.globalbioticinteractions.org
 * and the OBO Relation Ontology http://www.ontobee.org/ontology/RO
 */
public enum TaxRelType {

  /**
   * Taxon concept Relationship: The circumscription of this taxon is (essentially) identical to the related taxon.
   */
  CONGRUENT,

  /**
   * Taxon concept Relationship: The related taxon concept is a subset of this taxon concept.
   */
  INCLUDES,

  /**
   * Taxon concept Relationship: Concepts 1 and 2 share some members/children in common, and each contain some members not shared with the other.
   */
  OVERLAPS,

  /**
   * Taxon concept Relationship: Concept 2 is not a subset of Concept 1.
   */
  EXCLUDES,

  /**
   * Taxon concept Relationship: Concepts 1 and 2 have at least one member in common.
   * The more general set relationship to: ‘includes’, ‘congruent’ and ‘overlaps.
   * For use when the specific nature of the set intersection is not known or provided. The opposite of ‘excludes’
   */
  INTERSECTS,


  /**
   * Generic interaction relation.
   */
  INTERACTS_WITH,

  VISITS,

  INHABITS,

  SYMBIONT_OF,

  ASSOCIATED_WITH,

  /**
   * Herbivores, fungivores, predators or other forms of organims eating or feeding on the related taxon.
   */
  EATS,

  POLLINATES,

  PARASITE_OF,

  PATHOGEN_OF,

  /**
   * E.g. a host plant for some herbivore.
   */
  HOST_OF;

  public static final Set<TaxRelType> CONCEPT_RELATION_TYPES = ImmutableSet.of(CONGRUENT, INCLUDES, OVERLAPS, EXCLUDES, INTERSECTS);

  public boolean isConceptRelation(){
    return CONCEPT_RELATION_TYPES.contains(this);
  }

  public boolean isSpeciesInteraction(){
    return !isConceptRelation();
  }
}
