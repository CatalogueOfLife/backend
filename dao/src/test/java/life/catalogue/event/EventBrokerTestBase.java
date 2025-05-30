package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TmpIO;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class EventBrokerTestBase {

  EventBroker broker;
  AtomicInteger cntD;
  AtomicInteger cntDL;
  TmpIO dir;

  public void init() throws Exception {
    var cfg = new BrokerConfig();
    dir = new TmpIO.Dir();
    cfg.queueDir = dir.file+"/queue";
    cfg.pollingLatency = 1;
    this.broker = new EventBroker(cfg);
    this.cntD = new AtomicInteger(0);
    this.cntDL = new AtomicInteger(0);
    broker.register(new DatasetListener() {

      @Override
      public void datasetChanged(DatasetChanged event) {
        cntD.incrementAndGet();
        System.out.println(event);
      }

      @Override
      public void datasetLogoChanged(DatasetLogoChanged event) {
        cntDL.incrementAndGet();
        System.out.println(event);
      }

      @Override
      public void datasetDataChanged(DatasetDataChanged event) {
        System.out.println(event);
      }
    });
    broker.start();
  }

  public void stop() throws Exception {
    broker.stop();
    dir.close();
  }
}