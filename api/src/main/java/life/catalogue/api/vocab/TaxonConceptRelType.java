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

/**
 * Vocabulary classifying the kind of taxon concept relation.
 * The enumeration contains classic RCC5 concept relations and are (mostly) directed.
 */
public enum TaxonConceptRelType {

  EQUALS("equal (EQ)", "The circumscription of this taxon is (essentially) identical to the related taxon."),

  INCLUDES("proper part inverse (PPi)", "The related taxon concept is a subset of this taxon concept."),

  INCLUDED_IN("proper part (PP)", "This taxon concept is a subset of the related taxon concept."),

  OVERLAPS("partially overlapping (PO)", "Both taxon concepts share some members/children in common, and each contain some members not shared with the other."),

  EXCLUDES("disjoint (DR)", "The related taxon concept is not a subset of this concept.");

  private final String rcc5;
  private final String description;

  TaxonConceptRelType(String rcc5, String description) {
    this.rcc5 = rcc5;
    this.description = description;
  }

  public String getRcc5() {
    return rcc5;
  }

  public String getDescription() {
    return description;
  }

}
