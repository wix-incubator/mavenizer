package tools.jvm.mvn;

import com.google.common.collect.AbstractIterator;
import com.google.common.io.Closer;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.Delegate;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.cactoos.Output;
import org.cactoos.Proc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

public interface Archive {

    void writeTo(Output out) throws Exception;

    /**
     * Tar.
     */
    @SuppressWarnings({"DuplicatedCode", "UnstableApiUsage"})
    @AllArgsConstructor
    class TAR implements Archive {

        private final Collection<File> files;
        private final Function<File, Path> tarPath;


        @Override
        public void writeTo(Output out) throws Exception {
            final Closer closer = Closer.create();
            final TarArchiveOutputStream aos = closer.register(new TarArchiveOutputStream(out.stream()));
            aos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            try {
                for (File file : files) {
                    final ArchiveEntry entry = aos.createArchiveEntry(file, tarPath.apply(file).toString());
                    aos.putArchiveEntry(entry);
                    com.google.common.io.Files.asByteSource(file).copyTo(aos);
                    aos.closeArchiveEntry();
                }
                aos.finish();
            } finally {
                closer.close();
            }
        }
    }

    /**
     * Handle archiving local maven directory.
     */
    class LocalRepositoryDir implements Archive {

        @Delegate
        private final Archive archive;


        /**
         * Ctor.
         * @param repository project local repository
         */
        public LocalRepositoryDir(Path repository) {
            this.archive = new TarDirectory(
                    repository,
                    FileFilterUtils.and(
                            FileFilterUtils.fileFileFilter(),
                            // SEE: https://stackoverflow.com/questions/16866978/maven-cant-find-my-local-artifacts
                            //
                            //So with Maven 3.0.x, when an artifact is downloaded from a repository,
                            // maven leaves a _maven.repositories file to record where the file was resolved from.
                            //
                            //Namely: when offline, maven 3.0.x thinks there are no repositories, so will always
                            // find a mismatch against the _maven.repositories file
                            FileFilterUtils.notFileFilter(
                                    FileFilterUtils.prefixFileFilter("_remote.repositories")
                            )
                    )
            );
        }
    }



    /**
     * Tar all directory.
     */
    @SuppressWarnings({"DuplicatedCode"})
    @ToString
    class TarDirectory implements Archive {

        @Delegate
        private final Archive archive;

        /**
         * Ctro.
         * @param dir archive all files from dir
         */
        public TarDirectory(Path dir)  {
            this(dir, FileFilterUtils.trueFileFilter());
        }

        /**
         * Ctor.
         * @param dir dir
         * @param filesFilter files predicate
         */
        public TarDirectory(Path dir, IOFileFilter filesFilter)  {
            archive = output -> new Archive.TAR(
                    FileUtils.listFiles(
                            dir.toFile(),
                            filesFilter,
                            FileFilterUtils.directoryFileFilter() // recursive
                    ),
                    file -> dir.relativize(file.toPath())
            ).writeTo(output);
        }
    }


    @SuppressWarnings({"UnstableApiUsage"})
    @SneakyThrows
    static void extractTar(Path tar, Path dest) {
        final Closer closer = Closer.create();

        final TarArchiveInputStream ais = closer.register(new TarArchiveInputStream(
                Files.newInputStream(tar, StandardOpenOption.READ)
        ));
        Iterable<TarArchiveEntry> iter = new LSTar(ais, false);
        try {
            final File destFile = dest.toFile();
            iter.forEach(tarEntry -> {
                mkFile(ais, destFile, tarEntry);
            });
        } finally {
            closer.close();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SneakyThrows
    static void mkFile(TarArchiveInputStream ais, File root, TarArchiveEntry tarEntry)  {
        File destPath = new File(root, tarEntry.getName());
//        System.out.println("Write " + destPath + " by " + tarEntry.getName());
        if (tarEntry.isDirectory()) {
            destPath.mkdirs();
        } else {
            if (!destPath.getParentFile().exists()) {
                destPath.getParentFile().mkdirs();
            }
            destPath.createNewFile();
            Files.copy(ais, destPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("NullableProblems")
    @AllArgsConstructor
    class LSTar implements Iterable<TarArchiveEntry> {
        private final TarArchiveInputStream ais;
        private final boolean close;

        public LSTar(Path file) throws IOException {
            this(new TarArchiveInputStream(
                    Files.newInputStream(file, StandardOpenOption.READ)), true);
        }

        @Override
        public Iterator<TarArchiveEntry> iterator() {
            return new AbstractIterator<TarArchiveEntry>() {
                @SneakyThrows
                @Override
                protected TarArchiveEntry computeNext() {
                    final TarArchiveEntry entry = ais.getNextTarEntry();
                    return entry != null ? entry : fin();
                }

                private TarArchiveEntry fin() {
                    if (close) {
                        IOUtils.closeQuietly(ais);
                    }
                    return endOfData();
                }
            };
        }
    }
}
