package life.catalogue.command;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AddTableCmdTest extends CmdTestBase {

    public AddTableCmdTest() {
        super(() -> new AddTableCmd());
    }

    @Test
    public void execute() throws Exception {
        assertTrue(run("addTable", "--prompt", "0", "-t", "type_material").isEmpty());
    }

}