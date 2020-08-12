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
 * Vocabulary classifying the kind of name relation.
 */
public enum NomRelType {
  
  /**
   * This name is a spelling correction, called emendation in zoology, of the related name having the same type.
   * Intentional changes in the original spelling of an available name, whether justified or unjustified.
   * The binomial authority remains unchanged.
   * Valid emendations include changes made to correct:
   * a) typographical errors in the original work describing the species,
   * b) errors in transliteration from non-Latin languages,
   * c) names that included diacritics, hyphens
   * d) endings of species to match the gender of the generic name, particularly when the combination has been changed
   * <p>
   * For example, Hieronima and Hyeronima are orthographical variants of Hieronyma.
   * One of the spellings must be treated as the correct one. In this case, the spelling Hieronyma has been conserved
   * and is to be used as the correct spelling.
   * <p>
   * Botany (Article 61).: An inadvertent use of one of the other spellings has no consequences:
   * the name is to be treated as if it were correctly spelled.
   * <p>
   * Zoology (Art. 32-33 ): Orthographical variants in the formal sense do not exist;
   * a misspelling or orthographic error is treated as a lapsus, a form of inadvertent error.
   * The first reviser is allowed to choose one variant for mandatory further use, but in other ways,
   * these errors generally have no further formal standing.
   */
  SPELLING_CORRECTION(true, null, null),
  
  /**
   * This name has a basionym and therefore is either
   * a recombination (combinatio nova, comb. nov.) of the name pointed to
   * (and the name pointed to is not, itself, a recombination),
   * or a change in rank (status novus, stat. nov.).
   */
  BASIONYM(true, NomStatus.ESTABLISHED, NomStatus.ESTABLISHED),
  
  /**
   * This name is the validation of a name that was not fully published before.
   * Covers the use of ex in botanical author strings.
   * <p>
   * ICN Art. 46.4: e.g. if this name object represents G. tomentosum Nutt. ex Seem.
   * then the related name should be G. tomentosum Nutt.
   */
  BASED_ON(true, NomStatus.ESTABLISHED, NomStatus.NOT_ESTABLISHED),
  
  /**
   * This name is a replacement for the homotypic related name.
   * Also called 'Nomen Novum' or 'avowed substitute'
   *
   * In zoology this is called a "new replacement name" or "new substitute name"
   * and is easily confused with just "replacement name"
   *
   * ICN: Article 7.3
   * ICZN: Article 60.3, 67.8, 72.7
   *
   */
  REPLACEMENT_NAME(true, NomStatus.ESTABLISHED, NomStatus.UNACCEPTABLE),
  
  /**
   * This name or spelling is conserved / protected against the related name
   * or the related name is suppressed / rejected in favor of the current name.
   * <p>
   * A spelling which has been conserved relates two homotypic names, otherwise
   * the related names should be based on different types.
   * <p>
   * Based on an individual publication but more often
   * due to actions of the ICZN or ICBN exercising its Plenary Powers.
   * <p>
   * ICN: Conservation is covered under Article 14 and Appendix II and Appendix III (this name is nomina conservanda).
   * ICZN: Reversal of precedence under Article 23.9 (this name is nomen protectum and the target name is nomen oblitum)
   * or suppression via plenary power Article 81.
   */
  CONSERVED(null, NomStatus.CONSERVED, NomStatus.REJECTED),
  
  /**
   * This name has the same spelling as the related name
   * but was published later and has priority over it (unless conserved or sanctioned)
   * and is based on a different type. Called a junior homonym in zoology.
   * <p>
   * This includes botanical parahomonyms which differ slightly in spelling
   * but are similar enough that they are likely to be confused (Art 53.3).
   * The zoological code has a set of spelling variations (article 58) that are considered to be identical.
   * <p>
   * When acts of conservation or suppression have occurred then the terms 'Conserved Later Homonym'
   * and 'Rejected Earlier Homonym' should be used.
   * <p>
   * Two identical and homotypic names (isonyms) should be indicated with the superfluous relation type.
   * <p>
   * ICN: Article 53
   * ICZN: Chapter 12, Article 52.
   */
  LATER_HOMONYM(false, NomStatus.UNACCEPTABLE, NomStatus.ESTABLISHED),
  
  /**
   * This name was superfluous at its time of publication,
   * i. e. it was based on the same type as the related, previously published name (ICN article 52).
   * The superfluous name is available but illegitimate.
   * <p>
   * Includes the special case of isonyms which are identical names.
   * Zoology: unnecessary substitute name
   */
  SUPERFLUOUS(true, NomStatus.UNACCEPTABLE, NomStatus.ESTABLISHED),
  
  /**
   * A relation indicating two homotypic names, i.e. objective or nomenclatural synonymy, but not further specifying why.
   */
  HOMOTYPIC(true, null, null),
  
  /**
   * This name is the type name (species/genus) for the related higher ranked name.
   * The name should be the original combination, i.e. basionym, if subsequent recombinations exist.
   */
  TYPE(true, null, null);
  
  private final Boolean homotypic;
  private final NomStatus from;
  private final NomStatus to;
  
  NomRelType(Boolean homotypic, NomStatus from, NomStatus to) {
    this.homotypic = homotypic;
    this.from = from;
    this.to = to;
  }
  
  /**
   * @return true if homotypic, false if heterotypic or unknown
   */
  public boolean isHomotypic() {
    return Boolean.TRUE.equals(homotypic);
  }
  
  /**
   * @return implicit status of the name having this outgoing relation
   */
  public NomStatus getStatusFrom() {
    return from;
  }
  
  /**
   * @return implicit status of the related name having this incoming relation
   */
  public NomStatus getStatusTo() {
    return to;
  }
}
