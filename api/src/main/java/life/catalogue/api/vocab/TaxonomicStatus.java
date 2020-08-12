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
 * The taxonomic status of an accepted taxon.
 * For synonyms only a nomenclatural status is allowed.
 */
public enum TaxonomicStatus {
  
  ACCEPTED("A taxonomically accepted, current name"),
  
  PROVISIONALLY_ACCEPTED("Treated as accepted, but doubtful whether this is correct."),
  
  SYNONYM("Names which point unambiguously at one species (not specifying whether homo- or heterotypic)." +
    "Synonyms, in the CoL sense, include also orthographic variants and published misspellings."),
  
  AMBIGUOUS_SYNONYM("Names which are ambiguous because they point at the current species and one or more others " +
    "e.g. homonyms, pro-parte synonyms (in other words, names which appear more than in one place in the Catalogue)."),
  
  MISAPPLIED("A misapplied name. Usually accompanied with an accordingTo on the synonym to indicate the " +
    "source the misapplication can be found in.");

  private final String description;

  TaxonomicStatus(String description) {
    this.description = description;
  }

  /**
   * @return true for a status valid for a synonym, false if valid for an accepted taxon.
   */
  public boolean isSynonym() {
    return this != ACCEPTED && this != PROVISIONALLY_ACCEPTED;
  }

  public String getDescription() {
    return description;
  }
}
