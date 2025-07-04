package life.catalogue.matching.model;

import com.opencsv.bean.CsvBindByName;

import lombok.*;

/** A simple class to represent a name usage ready to be indexed. */
@Data
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameUsage {

  @CsvBindByName(column = "id")
  String id;

  @CsvBindByName(column = "parentId")
  String parentId;

  @CsvBindByName(column = "scientificName")
  String scientificName;

  @CsvBindByName(column = "authorship")
  String authorship;

  @CsvBindByName(column = "status")
  String status;

  @CsvBindByName(column = "rank")
  String rank;

  @CsvBindByName(column = "code")
  String code;

  @CsvBindByName(column = "genericName")
  String genericName;

  @CsvBindByName(column = "infragenericEpithet")
  String infragenericEpithet;

  @CsvBindByName(column = "specificEpithet")
  String specificEpithet;

  @CsvBindByName(column = "infraspecificEpithet")
  String infraspecificEpithet;

  @CsvBindByName(column = "type")
  String type;

  @CsvBindByName(column = "category")
  String category;

  @CsvBindByName(column = "extension")
  String extension;
}
