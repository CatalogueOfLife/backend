package life.catalogue.api.event;

public interface SectorListener extends Listener {

  void sectorDeleted(DeleteSector d);

}
