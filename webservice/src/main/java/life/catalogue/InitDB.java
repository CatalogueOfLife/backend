package life.catalogue;

/**
 * Wrapper class to manually execute a database initialisation using the regular InitCmd cli command.
 * Requires a regular yaml service configuration to define the database to be initialised which must be passed as the first argument
 * and a second integer parameter to specify the number of partions to be created for external datasets.
 *
 * Warning. This will erase all existing data!
 */
public class InitDB {

  public static void main(String[] args) throws Exception {
    String configFile = args[0];
    Integer partitions = Integer.parseInt(args[1]);
    System.out.printf("Initialising database with %s partitions using configurations at %s", partitions, configFile);
    new WsServer().run(new String[]{"init", configFile, "--prompt", "0", "--num", partitions.toString()});
  }
}
