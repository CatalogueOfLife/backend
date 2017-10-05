package org.col.db.mapper;

import java.util.Random;

import org.col.api.Dataset;
import org.col.api.Name;
import org.col.api.Reference;
import org.col.api.Taxon;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public abstract class MapperTestBase<T> {

  public final static Random RND = new Random();
  public final static Dataset D1 = new Dataset();
  public final static Dataset D2 = new Dataset();

  public final static Name NAME1 = new Name();
  public final static Name NAME2 = new Name();

  public final static Taxon TAXON1 = new Taxon();
  public final static Taxon TAXON2 = new Taxon();

  public final static Reference REF1 = new Reference();
  public final static Reference REF2 = new Reference();

  static {
    D1.setKey(1);
    D2.setKey(2);
    
    NAME1.setKey(1);
    NAME1.setId("name-1");
    NAME1.setDataset(D1);
    NAME1.setScientificName("Malus sylvestris");
    
    NAME2.setKey(2);
    NAME2.setId("name-2");
    NAME2.setDataset(D1);
    NAME2.setScientificName("Larus fuscus");
    
    TAXON1.setKey(1);
    TAXON1.setId("root-1");
    TAXON1.setDataset(D1);
    TAXON1.setName(NAME1);
    
    TAXON2.setKey(2);
    TAXON2.setId("root-2");
    TAXON2.setDataset(D1);
    TAXON2.setName(NAME2);
    
    REF1.setKey(1);
    REF1.setId("ref-1");
    REF1.setDataset(D1);
    
    REF2.setKey(2);
    REF2.setId("ref-2");
    REF2.setDataset(D2);
  }

  private final Class<T> mapperClazz;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.squirrels();

  public MapperTestBase(Class<T> mapperClazz) {
    this.mapperClazz = mapperClazz;
  }

  public T mapper() {
    return initMybatisRule.getMapper(mapperClazz);
  }

  public void commit() {
    initMybatisRule.commit();
  }


}