package tools.jvm.mvn;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.gson.*;
import lombok.*;
import org.cactoos.io.ReaderOf;

import java.io.File;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collection of a paths.
 */
public interface FilePaths extends Iterable<FilePaths.Target> {

    Gson G = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Path.class, (JsonDeserializer<Path>) (json, typeOfT, context) -> {
                final String asString = json.getAsString();
                return Paths.get(asString);
            }).create();


    @Data
    @NoArgsConstructor
    class Target {
        private Path path;
        private Map<String, String> tags = Collections.emptyMap();

        public Target(Path p) {
            this.path = p;
        }
    }

    /**
     * New stream of paths
     *
     * @return stream of paths
     */
    @SuppressWarnings("UnstableApiUsage")
    default Stream<Path> paths() {
        return Streams.stream(this).map(Target::getPath);
    }

    /**
     * New stream of paths
     *
     * @return stream of paths
     */
    @SuppressWarnings("UnstableApiUsage")
    default Stream<Target> stream() {
        return Streams.stream(this);
    }

    /**
     * Try to find common prefix for all paths.
     *
     * @return common prefix path
     */
    default Path resolveCommonPrefix() {
        final Path first = paths().findFirst().orElseThrow(() -> new IllegalStateException("empty"));

        BiPredicate<Path, Integer> matches = (path, i) -> {
            if (path == null) return false;
            if (i >= path.getNameCount()) return false;
            if (i >= first.getNameCount()) return false;
            if (i == 0) return first.getName(i).equals(path.getName(i));
            final Path path0 = first.subpath(0, i);
            final Path path1 = path.subpath(0, i);
            return path0.startsWith(path1);
        };

        return this.paths().map(Path::getNameCount).max(Comparator.naturalOrder()).flatMap(max -> {
            for (int x = 1; x <= max; x++) {
                final int i = x;
                if (!paths().allMatch(p -> matches.test(p, i))) {
                    return Optional.of(first.subpath(0, i - 1));
                }
            }
            return Optional.empty();
        }).map(path -> {
            final Path src = Paths.get("src");
            for (int i = 0; i < path.getNameCount(); i++) {
                if (path.getName(i).equals(src)) {
                    return path.subpath(0, i);
                }
            }
            return path;
        }).orElseThrow(() -> new IllegalStateException("no maven layout within paths: " + this));
    }


    /**
     * Paths based on manifest file.
     */
    @SuppressWarnings("UnstableApiUsage")
    class Manifest implements FilePaths {
        public static final String WRAP = "'";
        private final Collection<FilePaths.Target> paths;

        public Manifest(File man) {
            this(Files.asCharSource(man, StandardCharsets.UTF_8));
        }

        @lombok.SneakyThrows
        public Manifest(CharSource source) {
            this.paths = source
                    .readLines().stream()
                    .map(p -> {
                        String base = p.trim();
                        if (base.startsWith(WRAP)) {
                            base = base.substring(1);
                        }
                        if (base.endsWith(WRAP)) {
                            base = base.substring(0, base.length() - 1);
                        }
                        return base;
                    })
                    .map(p -> {
                        return G.fromJson(p, Target.class);
//                        return new Target(null, null);
                    })
                    .collect(Collectors.toSet());
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public Iterator<FilePaths.Target> iterator() {
            return paths.iterator();
        }

        @Override
        public String toString() {
            MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
            this.forEach(helper::addValue);
            return helper.toString();
        }
    }
}
