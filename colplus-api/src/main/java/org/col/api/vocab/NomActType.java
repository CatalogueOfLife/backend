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
 * Vocabulary classifying the kind of nomenclatural act.
 * TODO: how to deal with homotypic relations for superflous names?
 */
public enum NomActType {

  /**
   * The current name is a spelling correction, called emendation in zoology, of the related name.
   * Intentional changes in the original spelling of an available name, whether justified or unjustified.
   * The binomial authority remains unchanged.
   * Valid emendations include changes made to correct:
   *    a) typographical errors in the original work describing the species,
   *    b) errors in transliteration from non-Latin languages,
   *    c) names that included diacritics, hyphens
   *    d) endings of species to match the gender of the generic name, particularly when the combination has been changed
   */
  SPELLING_CORRECTION,

  /**
   * The current name has a basionym and therefore is either
   * a recombination (combinatio nova, comb. nov.) of the name pointed to
   *  (and the name pointed to is not, itself, a recombination),
   * or a change in rank (status novus, stat. nov.).
   */
  BASIONYM,

  /**
   * The current name is the validation of a that was not fully published before.
   * Covers the use of ex in botanical author strings.
   *
   * ICBN Art. 46.4: e.g. if this name object represents G. tomentosum Nutt. ex Seem.
   * then the related name should be G. tomentosum Nutt.
   */
  BASED_ON,

  /**
   *  Current name is replacement for the related name.
   *  Also called 'Nomen Novum' or 'avowed substitute'
   *  ICBN: Article 7.3
   *  ICZN: Article 60.3.
   */
  REPLACEMENT_NAME,

  /**
   *  The current name is conserved against the related name or the related name is suppressed in favor of the current name.
   *  Acts taken by the official committee.
   *
   *  ICBN: Conservation is covered under Article 14 and Appendix II and Appendix III (this name is nomina conservanda).
   *  ICZN: Conservation is covered under Article 23.9 (this name is nomen protectum and the target name is nomen oblitum)
   */
  CONSERVED,

  /**
   *  Current name has same spelling as related name
   *  but was published later and has priority over it (unless conserved or sanctioned).
   *  Called a junior homonym in zoology.
   *
   *  When acts of conservation or suppression have occurred then the terms “Conserved Later Homonym”
   *  and “Rejected Earlier Homonym” should be used.
   *
   *  See ICBN: Article 53
   *  ICZN: Chapter 12, Article 52.
   */
  LATER_HOMONYM;
}
