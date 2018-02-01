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
package org.col.dw.api.vocab;

/**
 * Vocabulary classifying the kind of nomenclatural act.
 */
public enum NomActType {

  /**
   * The published description of a new original name,
   * a new recombination (combinatio nova, comb. nov.),
   * a replacement name (nomen novum, nom. nov.) or
   * a change in rank (status novus, stat. nov.).
   *
   * The publication linked to the description act is the dwc:namePublishedIn reference.
   */
  DESCRIPTION,

  /**
   * Acts taken by the official committee e.g. suppressed, rejected or conserved names.
   */
  RULING,

  /**
   * Corrected or improved names.
   * Intentional changes in the original spelling of an available name.
   * The binomial authority remains unchanged.
   * Valid emendations include changes made to correct:
   *    a) typographical errors in the original work describing the species,
   *    b) errors in transliteration from non-Latin languages,
   *    c) names that included diacritics, hyphens
   *    d) endings of species to match the gender of the generic name, particularly when the combination has been changed
   */
  EMENDATION,

  /**
   * Subsequent type designations.
   */
  TYPIFICATION;
}
