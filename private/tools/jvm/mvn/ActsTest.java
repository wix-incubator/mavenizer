package tools.jvm.mvn;

import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import org.cactoos.io.InputOf;
import org.cactoos.io.OutputTo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("UnstableApiUsage")
public class ActsTest {

    File tmpWorkDir ;

    @Before
    public void tmp() {
        tmpWorkDir = Files.createTempDir();
    }

    @After
    public void rm() {
        //noinspection ResultOfMethodCallIgnored
        tmpWorkDir.delete();
    }

    @Test
    public void generatePom() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project>\n" +
                " <!-- xembly:on -->" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>BBBB</groupId>\n" +
                "    <artifactId>AAAA</artifactId>\n" +
                "    <version>1.0.0-SNAPSHOT</version>\n" +
                "    <dependencies>\n" +
                "    </dependencies>\n" +
                "</project>";

        Project p = Project.builder()
//                .artifactId("AAAA")
//                .groupId("BBBB")
                .workDir(tmpWorkDir.toPath())
                .pomTemplate(ByteSource.wrap(pom.getBytes()))
                .deps(Lists.newArrayList(new Dep.Simple(null, "xyz", "xyz-aaa", "1.0")))
                .build();

        final Act.Iterative act = new Act.Iterative(
                new Acts.PomFile()
        );

        final Project accept = act.accept(p);

        XML xml = new XMLDocument(accept.pom().toFile());
        Assert.assertEquals(xml.xpath("//project/groupId/text()").get(0).trim(), "BBBB");
        Assert.assertEquals(xml.xpath("//project/artifactId/text()").get(0).trim(), "AAAA");
        Assert.assertEquals(xml.xpath("//project/dependencies/dependency/groupId/text()"), Lists.newArrayList("xyz"));
        Assert.assertEquals(xml.xpath("//project/dependencies/dependency/artifactId/text()"), Lists.newArrayList("xyz-aaa"));
        Assert.assertEquals(xml.xpath("//project/dependencies/dependency/version/text()"), Lists.newArrayList("1.0"));
    }


    @Test
    public void install() throws IOException {
        final Act.Iterative act = new Act.Iterative(
                new Acts.Deps()
        );
        Path jar = java.nio.file.Files.createTempFile("jar", "jar");

        Files.touch(jar.toFile());
        jar.toFile().deleteOnExit();

        final Project p = Project.builder()
                .workDir(tmpWorkDir.toPath())
                .m2Directory(tmpWorkDir.toPath())
                .deps(Lists.newArrayList(
                        new Dep.Simple(jar.toFile(), "xyz.com.baz", "xyz-aaa", "1.0")))
                .build();

        act.accept(p);

        final Path resolve = tmpWorkDir.toPath().resolve("repository/xyz/com/baz/xyz-aaa/1.0/");
        Assert.assertTrue(resolve.resolve("xyz-aaa-1.0.jar").toFile().exists());
        Assert.assertTrue(resolve.resolve("xyz-aaa-1.0.pom").toFile().exists());
    }

}
