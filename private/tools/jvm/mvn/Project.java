package tools.jvm.mvn;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import lombok.*;
import lombok.experimental.Accessors;
import org.cactoos.io.InputOf;
import org.cactoos.text.TextOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Accessors(fluent = true)
@Getter
@Builder(toBuilder = true)
@ToString
public final class Project {

    interface ProjectView {
        Iterable<Dep> deps();
        String parent();
    }

    /**
     * Dependencies.
     */
    @Builder.Default
    private Iterable<Dep> deps = ImmutableList.of();

    /**
     * Working directory.
     */
    private Path workDir;

    /**
     * Maven home directory.
     */
    @Builder.Default
    private Path m2Directory = getTmpDirectory();

    /**
     * Output files for the project.
     */
    @Builder.Default
    private List<OutputFile> outputs = Lists.newArrayList();

    /**
     * Arguments for the maven.
     */
    @Builder.Default
    private Args args = new Args();

    /**
     * Pom template source.
     */
    private ByteSource pomTemplate;

    /**
     * Generated pom file.
     */
    private Path pom;

    /**
     * Parent pom file.
     * Not templating supported so far/
     */
    private Path parentPom;

    /**
     * cached computation
     */
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private Path repositoryCached;

    /**
     * POM XML.
     */
    @Getter(AccessLevel.PRIVATE)
    private Pom pomXML;

    /**
     * Local repository
     * @return path
     */
    public Path repository() {
        if (repositoryCached == null)
            repositoryCached = this.m2Directory().resolve("repository");
        return repositoryCached;
    }

    /**
     * project view for props access
     */
    public ProjectView toView() {
        return new ProjectView() {
            @Override
            public Iterable<Dep> deps() {
                return ImmutableList.copyOf(deps);
            }


            @Override
            public String parent() {
                if (parentPom != null) {
                    final Path pom = pom();
                    final Path relativize = pom.relativize(parentPom);
                    return relativize.toString();
                }
                return null;

            }
        };
    }

    /**
     * Loaded and cached pom xml.
      * @return pom
     */
    public Pom getPomXML() {
        if (pomXML == null) {
            pomXML = new Pom.Cached(
                    new Pom.Standard(new InputOf(pom()))
            );
        }
        return pomXML;
    }

    /**
     * Current project's default artifact folder within local repo
     * @return local repo's folder
     */
    public Path getArtifactFolder() {
        final Pom pom = getPomXML();
        return new Dep.Simple(null,
                pom.groupId(),
                pom.artifactId(),
                pom.version()
        ).artifactFolder(repository());
    }

    /**
     * Pom path (auto generated if any)
     * @return path
     */
    public Path pom() {
        if (pom == null) {
            pom = syntheticPomFile(workDir);
        }
        return pom;
    }


    public String debug() throws IOException {
        return MoreObjects.toStringHelper(this)
                .add("pom", "\n" + new TextOf(pom()).asString())
                .toString();
    }

    @SneakyThrows
    private static Path getTmpDirectory() {
        final String dirName = SysProps.labelHex().map(n ->
                "__" + Long.toHexString(System.currentTimeMillis()).toUpperCase()
                        + "__M2_HOME@" + n + "@").orElse("M2_HOME@");
        return Files.createTempDirectory(dirName);
    }

    public static Path syntheticPomFile(Path workDir) {
        for (int i = 0; i < 1000; i++) {
            final Path pom = workDir.resolve(
                    Texts.randomFileName("pom-synthetic") + "-" + i + ".xml"
            );
            if (Files.notExists(pom)) {
                return pom;
            }
        }
        throw new IllegalStateException();
    }

    public static String name() {
        return SysProps.labelHex().orElse("");
    }
}
