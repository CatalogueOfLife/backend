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
package org.col.api.vocab;

/**
 * The taxonomic status of an accepted taxon.
 * For synonyms only a nomenclatural status is allowed.
 */
public enum TaxonomicStatus {

  ACCEPTED,

  /**
   * Treated as accepted, but doubtful whether this is correct.
   */
  DOUBTFUL,

  /**
   * Any kind of synonym, not specifying whether homo- or heterotypic
   */
  SYNONYM,

  /**
   * A pro parte synonym that has more than one accepted names.
   */
  AMBIGUOUS_SYNONYM,

  /**
   * A misapplied name. Usually accompanied with an accordingTo on the synonym to indicate the
   * source the misapplication can be found in.
   */
  MISAPPLIED;

  /**
   * @return true for a status valid for a synonym, false if valid for an accepted taxon.
   */
  public boolean isSynonym(){
    return this != ACCEPTED && this != DOUBTFUL;
  }

}
