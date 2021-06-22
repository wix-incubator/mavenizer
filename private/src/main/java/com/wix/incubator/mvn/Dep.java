package com.wix.incubator.mvn;

import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;

/**
 * Dependency from bazel
 */
public abstract class Dep {

    private static class DefinitionStruct {
        public Path file;
        public Map<String, String> tags;
    }

    /**
     * New dep.
     * @param jsonDef json definition
     * @return a dep
     */
    public static Dep create(String jsonDef)  {
        DefinitionStruct dto = Cli.GSON.fromJson(jsonDef, DefinitionStruct.class);
        final Path file = dto.file;
        final String extension = FilenameUtils.getExtension(file.getFileName().toString());
        switch (extension) {
            case "jar":
                return new Bazel(dto);
            case "tar":
                return new Tar(file);
            default:
                throw new IllegalArgumentException("not supported dep from a file " + file);
        }
    }

    public final String groupId;
    public final String artifactId;
    public final String version;

    public String scope() {
        return "compile";
    }

    /**
     * Ctor.
     * @param id id
     */
    protected Dep(String[] id) {
        this(id[0], id[1], id[2]);
    }

    /**
     * Ctor.
     */
    protected Dep(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public abstract void installTo(Path repo) throws IOException;


    @SuppressWarnings("UnstableApiUsage")
    private static class Bazel extends Dep {
        private final Path sourceFile;
        private final String scope;

        private Bazel(DefinitionStruct struct) {
            super(mkVersion(struct));
            this.sourceFile = struct.file;
            this.scope = Optional.ofNullable(struct.tags)
                    .map(t -> t.get("scope")).orElse("compile");
        }

        @Override
        public String scope() {
            return scope;
        }

        @Override
        public void installTo(Path repo) throws IOException {
            final Path depFolder = repo.resolve(Maven.mvnLayout(groupId, artifactId, version));
            Files.createDirectories(depFolder);
            String fileName = this.artifactId + "-" + this.version;
            Files.copy(this.sourceFile, depFolder.resolve(fileName + ".jar"), StandardCopyOption.REPLACE_EXISTING);
            generatePom(this, depFolder);
        }

        private static String[] mkVersion(DefinitionStruct struct) {
            String filePath = struct.file.toAbsolutePath().toString();
            String hash = Hashing.murmur3_128().hashString(filePath, StandardCharsets.UTF_8).toString();
            String groupId = "bazelizer." + hash;
            String artifactId = struct.file.getFileName().toString().replace("/", "_")
                    .replace("=", "_").replace(".jar", "");
            String version = "rev-" + hash.substring(0, 7);
            return new String[]{groupId, artifactId, version};
        }
    }


    private static class Tar extends Dep {
        private final Path tar;

        protected Tar(Path source)  {
            super(readIds(source));
            tar = source;
        }

        private static String[] readIds(Path source)  {
            final List<String> tarFiles;
            try {
                tarFiles = IOUtils.list(source);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            return tarFiles.stream()
                    .filter(name -> name.endsWith(".jar"))
                    .findFirst()
                    .map(pathWithinTar -> {
                        final List<String> parts = asList(pathWithinTar.split("/"));
                        final String version = parts.get(parts.size() - 2);
                        final String art = parts.get(parts.size() - 3);
                        final String gid = Joiner.on(".").join(parts.subList(0, parts.size() - 3));
                        return new String[]{gid, art, version};
                    }).orElseThrow(() -> new IllegalStateException("tar has not resolvable content in "
                            + source + ": " + tarFiles));
        }

        @Override
        public void installTo(Path repo) throws IOException {
            final Path depFolder = repo.resolve(
                    Maven.mvnLayout(groupId, artifactId, version)
            );
            Files.createDirectories(depFolder);
            IOUtils.untar(tar, depFolder);
            Dep.generatePom(this, depFolder);
        }
    }

    private static void generatePom(Dep dep, Path folder) throws IOException {
        String pomXml = "<project>\n" +
                "   <modelVersion>4.0.0</modelVersion>\n" +
                "   <groupId>" + dep.groupId + "</groupId>\n" +
                "   <artifactId>" + dep.artifactId + "</artifactId>\n" +
                "   <version>" + dep.version + "</version>\n" +
                "   <description>generated</description>\n" +
                "</project>";

        Files.write(folder.resolve((dep.artifactId + "-" + dep.version) + ".pom"),
                pomXml.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    }
}
