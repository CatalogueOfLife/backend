package life.catalogue.matching.authorship;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

/**
 * What is known about the names whose authors are compared.
 *
 * @param code  selects the author map abbreviations are looked up in
 * @param group the taxonomic group of the names, for a matcher that knows which groups a person worked on
 */
public record AuthorContext(@Nullable NomCode code, @Nullable TaxGroup group) {
}
