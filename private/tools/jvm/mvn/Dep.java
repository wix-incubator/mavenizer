package tools.jvm.mvn;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import lombok.*;
import lombok.experimental.Delegate;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extenral dependency.
 */
public interface Dep {

    Logger log = LoggerFactory.getLogger(Dep.class);


    /**
     * Group Id.
     */
    String groupId();

    /**
     * Artifact Id.
     */
    String artifactId();

    /**
     * Version.
     */
    String version();

    /**
     * Jar.
     * @return jar
     */
    Path source();

    /**
     * Maven packaging type.
     * @return only jar for now
     */
    default Map<String,String> tags() {
        return Collections.emptyMap();
    }



    @Data
    @NoArgsConstructor
    class DepArtifact implements Dep {
        private Path path;
        private Map<String, String> tags = Collections.emptyMap();

        @SuppressWarnings("Guava")
        @Getter(AccessLevel.PRIVATE)
        private final Supplier<Dep> cached = Suppliers.memoize(this::original);

        /**
         * Ctor. for a path only.
         * @param p path
         */
        public DepArtifact(Path p) {
            this.path = p;
        }

        @Override
        public String groupId() {
            return cached.get().groupId();
        }

        @Override
        public String artifactId() {
            return cached.get().artifactId();
        }

        @Override
        public String version() {
            return cached.get().version();
        }

        @Override
        public Path source() {
            return path;
        }

        @Override
        public void installTo(Path repo) {
            cached.get().installTo(repo);
        }

        @SuppressWarnings("UnstableApiUsage")
        @SneakyThrows
        private Dep original() {
            final String extension = FilenameUtils.getExtension(path.getFileName().toString());
            if (extension.endsWith("jar")) {
                // regular jar, install hashed coordinates
                return new Dep.DigestCoords(path);
            }
            if (extension.endsWith("tar")) {
                // installed artifact as it was
                return Streams.stream(new Archive.LSTar(path))
                        .filter(entry -> {
                            final String name = entry.getName();
                            return name.endsWith(".jar");
                        }).map(TarArchiveEntry::getName)
                        .map(artifactPath -> new Archived(path, artifactPath))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("tar has not resolvable content in " + path));
            }
            throw new IllegalStateException("not supported extension for " + path);
        }

    }


    /**
     * Install
     * @param repo repo
     */
    @SneakyThrows
    default void installTo(Path repo) {
        final Path artifactFolder = this.relativeTo(repo);
        //noinspection ResultOfMethodCallIgnored
        artifactFolder.toFile().mkdirs();
        String fileName = this.artifactId() + "-" + this.version();

        Path pomFile = artifactFolder.resolve(fileName + ".pom");
        String pom = "<project xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                "<modelVersion>4.0.0</modelVersion>\n" +
                "<groupId>" + this.groupId() + "</groupId>\n" +
                "<artifactId>" + this.artifactId() + "</artifactId>\n" +
                "<version>" + this.version() + "</version>\n" +
                "<description>\nPom file generated by " + this.getClass()
                + " for \n" + this.source() + "\n</description>\n" +
                "</project>";
        Files.write(pomFile, pom.getBytes(StandardCharsets.UTF_8));

        Path jarFile = artifactFolder.resolve(fileName + ".jar");
        Files.copy(source(), jarFile);
    }

    /**
     * Resolve maven layout, relative to repo
     * @param repo relative to this
     * @return artifact folder
     */
    default Path relativeTo(Path repo) {
        String[] gidParts = this.groupId().split("\\.");
        Path thisGroupIdRepo = repo;
        for (String gidPart : gidParts) {
            thisGroupIdRepo = thisGroupIdRepo.resolve(gidPart);
        }
        return thisGroupIdRepo.resolve(this.artifactId()).resolve(this.version());
    }

    @AllArgsConstructor
    @ToString
    class Wrap implements Dep {
        @Delegate
        private final Dep dep;
    }

    @EqualsAndHashCode(of = {"gid", "aid", "version"})
    @ToString
    class Simple implements Dep {
        private final File file;
        private final String gid;
        private final String aid;
        private final String version;
        private final Map<String,String> tags;

        public Simple(File file, String gid, String aid, String v, Map<String, String> tags) {
            this.file = file;
            this.version = v;
            this.gid = gid;
            this.aid = aid;
            this.tags = tags;
        }

        public Simple(File file, String gid, String aid, String v) {
            this(file, gid, aid, v, Collections.emptyMap());
        }

        @Override
        public String groupId() {
            return gid;
        }

        @Override
        public String artifactId() {
            return aid;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public Path source() {
            return file.toPath();
        }
    }


    @EqualsAndHashCode(of = {"orig"})
    @ToString
    class DigestCoords implements Dep {
        private final Dep orig;

        @SneakyThrows
        public DigestCoords(Path path) {
            this.orig = create(path.toFile());
        }

        @Override
        public Map<String, String> tags() {
            return this.orig.tags();
        }

        @Override
        public String groupId() {
            return orig.groupId();
        }

        @Override
        public String artifactId() {
            return orig.artifactId();
        }

        @Override
        public String version() {
            return orig.version();
        }

        @Override
        public Path source() {
            return orig.source();
        }

        @SuppressWarnings("UnstableApiUsage")
        private static Dep create(File jarFile) {
            String filePath = jarFile.getPath();
            String hash = Hashing
                    .murmur3_128()
                    .hashString(filePath, StandardCharsets.UTF_8)
                    .toString();
            String groupId = "io.bazelbuild." + hash;
            String artifactId = jarFile.getName()
                    .replace("/", "_")
                    .replace("=", "_")
                    .replace(".jar", "");
            String version = "rev-" + hash.substring(0, 7);
            return new Simple(jarFile, groupId, artifactId, version);
        }
    }



    /**
     * Dependency resolved .
     */
    @ToString(callSuper = true)
    class Archived extends Wrap {

        /**
         * Ctro.
         */
        public Archived(Path tar, String someFileInTar) {
            super(build(tar, someFileInTar));
        }


        @Override
        public void installTo(Path repo) {
            final Path tarSrc = source();
            Archive.extractTar(tarSrc, repo);
        }

        @SneakyThrows
        private static Dep build(Path tar, String someFileInTar) {
            final List<String> parts = Arrays.asList(someFileInTar.split("/"));
            final String version = parts.get(parts.size() - 2);
            final String art = parts.get(parts.size() - 3);
            final String gid = Joiner.on(".").join(parts.subList(0, parts.size() - 3));
            return new Dep.Simple(tar.toFile(), gid, art, version);
        }
    }
}
