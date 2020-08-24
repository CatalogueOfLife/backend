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
 * Enumeration to classify name usages by how they originated.
 */
public enum Origin {
  
  SOURCE("Record came straight from source record."),
  
  DENORMED_CLASSIFICATION("Implicit usage from a denormalised classification."),
  
  VERBATIM_PARENT("Implicit usage from a verbatim parent name usage."),
  
  VERBATIM_ACCEPTED("Implicit usage from a verbatim accepted name usage."),
  
  VERBATIM_BASIONYM("Implicit usage from a verbatim basionym/original name."),

  AUTONYM("Generated, missing autonym."),
  
  IMPLICIT_NAME("Generated, missing genus or species for \"orphaned\" lower name."),
  
  MISSING_ACCEPTED("Artificial accepted usage for a synonym if its missing to preserve the taxonomic hierarchy."),
  
  BASIONYM_PLACEHOLDER("Placeholder usage for a missing or implicit basionym."),
  
  EX_AUTHOR_SYNONYM("Implicit synonym based on the illegitimate ex author. " +
    "See ICN article 46: http://www.iapt-taxon.org/nomen/main.php?page=art46"),
  
  USER("An entity was created or modified by a user."),

  OTHER("Any other origin not covered by the above.");


  private final String description;

  Origin(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
