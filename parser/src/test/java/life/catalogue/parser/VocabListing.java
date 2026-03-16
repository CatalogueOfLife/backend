package life.catalogue.parser;

import life.catalogue.api.vocab.DegreeOfEstablishment;
import life.catalogue.api.vocab.EstablishmentMeans;

import life.catalogue.api.vocab.Sex;

import org.gbif.api.vocabulary.LifeStage;

import org.gbif.nameparser.api.Rank;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("static-method")
public class VocabListing {

  public static void listVocab(Class<? extends Enum<?>> enumClass) {
    System.out.println("  - enum: " + enumClass.getSimpleName());
    System.out.println("    file: " + enumClass.getSimpleName().toLowerCase() + ".csv");
    System.out.println("    concepts:");
    for (Enum<?> e : enumClass.getEnumConstants()) {
      System.out.println("      - " + e.name() + ": " + e.name());
    }
  }
  public static void main(String[] args) {
    listVocab(EstablishmentMeans.class);
    listVocab(DegreeOfEstablishment.class);
    listVocab(Sex.class);
  }

}
