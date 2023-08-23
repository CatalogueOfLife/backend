package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AddTableCmdIT extends CmdTestBase {

    public AddTableCmdIT() {
        super(PartitionCmd::new);
    }

    @Test
    public void execute() throws Exception {
        assertTrue(run("addTable", "--prompt", "0", "-t", "type_material").isEmpty());
    }

}