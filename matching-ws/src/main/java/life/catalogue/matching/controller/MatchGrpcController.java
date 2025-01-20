package life.catalogue.matching.controller;

import io.grpc.stub.StreamObserver;

import life.catalogue.matching.grpc.*;
import life.catalogue.matching.model.Classification;
import life.catalogue.matching.model.NameUsageMatch;
import life.catalogue.matching.model.NameUsageQuery;
import life.catalogue.matching.service.MatchingService;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import org.gbif.nameparser.api.Rank;

import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * gRPC controller for the match service.
 */
@Slf4j
@GrpcService
public class MatchGrpcController extends MatchServiceGrpc.MatchServiceImplBase {

  private final MatchingService matchingService;

  public MatchGrpcController(MatchingService matchingService) {
    this.matchingService = matchingService;
  }

  @Override
  public void match(NameUsageQueryRpc request, StreamObserver<NameUsageMatchRpc> responseObserver) {

    try {
      NameUsageQuery query = NameUsageQuery.builder()
        .usageKey(request.getUsageKey())
        .taxonID(request.getTaxonId())
        .taxonConceptID(request.getTaxonConceptId())
        .scientificNameID(request.getScientificNameId())
        .scientificName(request.getScientificName())
        .genericName(request.getGenericName())
        .specificEpithet(request.getSpecificEpithet())
        .infraSpecificEpithet(request.getInfraSpecificEpithet())
        .rank(StringUtils.isNotBlank(request.getRank()) ? Rank.valueOf(request.getRank()) : null)
        .classification(Classification.builder()
          .kingdom(request.getClassification().getKingdom())
          .phylum(request.getClassification().getPhylum())
          .clazz(request.getClassification().getClass_())
          .order(request.getClassification().getOrder())
          .family(request.getClassification().getFamily())
          .genus(request.getClassification().getGenus())
          .subgenus(request.getClassification().getSubgenus())
          .species(request.getClassification().getSpecies())
          .build())
        .authorship(request.getAuthorship())
        .strict(request.getStrict())
        .verbose(request.getVerbose())
        .build();

      StopWatch stopWatch = StopWatch.createStarted();

      // Call the matching service
      NameUsageMatch response = matchingService.match(query);

      // Convert the response to the gRPC response
      NameUsageMatchRpc.Builder b = NameUsageMatchRpc.newBuilder()
        .setUsage(convertUsage(response.getUsage()));

      if (response.getAcceptedUsage() != null) {
        b.setAcceptedUsage(convertUsage(response.getAcceptedUsage()));
      }

      for (int i = 0; i < response.getClassification().size(); i++) {
        b.addClassification(
          life.catalogue.matching.grpc.RankedName.newBuilder()
            .setKey(response.getClassification().get(i).getKey())
            .setName(response.getClassification().get(i).getName())
            .setCanonicalName(response.getClassification().get(i).getCanonicalName())
            .setRank(response.getClassification().get(i).getRank().name())
            .build()
        );
      }

      if (response.getAdditionalStatus() != null) {
        for (int i = 0; i < response.getAdditionalStatus().size(); i++) {
          b.addStatus(
            life.catalogue.matching.grpc.Status.newBuilder()
              .setDatasetKey(response.getAdditionalStatus().get(i).getDatasetKey())
              .setDatasetAlias(response.getAdditionalStatus().get(i).getDatasetAlias())
              .setStatus(response.getAdditionalStatus().get(i).getStatus())
              .setSourceId(response.getAdditionalStatus().get(i).getSourceId())
              .build()
          );
        }
      }

      responseObserver.onNext(b.build());
      responseObserver.onCompleted();
      log(query, stopWatch);
    } catch (Exception e) {
      log.error("GRPC Error matching name", e);
      responseObserver.onError(e);
    }
  }

  private Usage convertUsage(NameUsageMatch.Usage usage) {
    if (usage == null) {
      return null;
    }
    Usage.Builder b =  Usage.newBuilder()
      .setKey(usage.getKey())
      .setName(usage.getName())
      .setCanonicalName(usage.getCanonicalName())
      .setAuthorship(usage.getAuthorship())
      .setParentId(usage.getParentID() != null ? usage.getParentID() : "")
      .setRank(usage.getRank().name())
      .setCode(usage.getCode().name())
      .setUninomial(usage.getUninomial() != null ? usage.getUninomial() : "")
      .setGenus(usage.getGenus() != null ? usage.getGenus() : "")
      .setInfragenericEpithet(usage.getInfragenericEpithet()  != null ? usage.getInfragenericEpithet() : "")
      .setSpecificEpithet(usage.getSpecificEpithet() != null ? usage.getSpecificEpithet() : "")
      .setInfraspecificEpithet(usage.getInfraspecificEpithet() != null ? usage.getInfraspecificEpithet() : "")
      .setCultivarEpithet(usage.getCultivarEpithet()  != null ? usage.getCultivarEpithet() : "")
      .setPhrase(usage.getPhrase() != null ? usage.getPhrase() : "")
      .setVoucher(usage.getVoucher() != null ? usage.getVoucher() : "")
      .setNominatingParty(usage.getNominatingParty()  != null ? usage.getNominatingParty() : "")
      .setCandidatus(usage.isCandidatus())
      .setNotho(usage.getNotho() != null ? usage.getNotho() : "")
      .setOriginalSpelling(usage.getOriginalSpelling() != null ? usage.getOriginalSpelling() : false)
      .putAllEpithetQualifier(usage.getEpithetQualifier() != null ? usage.getEpithetQualifier() : Map.of())
      .setType(usage.getType() != null ? usage.getType() : "")
      .setExtinct(usage.isExtinct())
      .setTaxonomicNote(usage.getTaxonomicNote() != null ? usage.getTaxonomicNote() : "")
      .setNomenclaturalNote(usage.getNomenclaturalNote() != null ? usage.getNomenclaturalNote() : "")
      .setPublishedIn(usage.getPublishedIn() != null ? usage.getPublishedIn() : "")
      .setUnparsed(usage.getUnparsed() != null ? usage.getUnparsed() : "")
      .setDoubtful(usage.isDoubtful())
      .setManuscript(usage.isManuscript())
      .setState(usage.getState() != null ? usage.getState() : "")
      .setIsAbbreviated(usage.isAbbreviated())
      .setIsAutonym(usage.isAutonym())
      .setIsBinomial(usage.isBinomial())
      .setIsIncomplete(usage.isIncomplete())
      .setIsIndetermined(usage.isIndetermined())
      .setIsPhraseName(usage.isPhraseName())
      .setIsTrinomial(usage.isTrinomial())
      .setIsAbbreviated(usage.isAbbreviated())
      .setTerminalEpithet(usage.getTerminalEpithet() != null ? usage.getTerminalEpithet() : "");

    if(usage.getCombinationAuthorship() != null) {
      b.setCombinationAuthorship(Authorship.newBuilder()
        .addAllAuthors(usage.getCombinationAuthorship().getAuthors() != null ? usage.getCombinationAuthorship().getAuthors() : null)
        .build()
      );
    }
    if(usage.getBasionymAuthorship() != null){
        b.setBasionymAuthorship(Authorship.newBuilder()
          .addAllAuthors(usage.getBasionymAuthorship().getAuthors())
          .build()
        );
    }
    return b.build();
  }

  protected static void log(NameUsageQuery query, StopWatch watch) {
    if (log.isInfoEnabled()) {
      StringJoiner joiner = new StringJoiner(", ");

      addIfNotNull(joiner, query.usageKey);
      addIfNotNull(joiner, query.taxonID);
      addIfNotNull(joiner, query.taxonConceptID);
      addIfNotNull(joiner, query.scientificNameID);
      addIfNotNull(joiner, query.scientificName);
      addIfNotNull(joiner, query.authorship);
      addIfNotNull(joiner, query.rank);
      addIfNotNull(joiner, query.genericName);
      addIfNotNull(joiner, query.specificEpithet);
      addIfNotNull(joiner, query.classification != null ? query.classification.getKingdom() : null);
      addIfNotNull(joiner, query.classification != null ? query.classification.getFamily() : null);
      addIfNotNull(joiner, query.classification != null ? query.classification.getGenus() : null);

      log.info("[{}ms] [{}] {}",
        String.format("%4d", watch.getTime(TimeUnit.MILLISECONDS)),
        "grpc",
        joiner.toString()
      );
    }
  }

  private static void addIfNotNull(StringJoiner joiner, Object value) {
    if (Objects.nonNull(value) && !value.toString().isEmpty()) {
      joiner.add(value.toString());
    }
  }
}

