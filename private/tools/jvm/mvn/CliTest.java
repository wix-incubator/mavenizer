package tools.jvm.mvn;

import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.Collections;

public class CliTest {

    @Test
    public void parseRunCmd() {
        final Cli.Run command = new Cli.Run();
        new CommandLine(command).parseArgs(
                "-Oxxx=yyy",
                "--pom=/some/pom.xml",
                "--srcs=/some/manifest.xml");
        Assert.assertEquals(command.outputs, Collections.singletonMap("xxx", "yyy"));
    }
}
