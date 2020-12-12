
#
# tar
#

_buildpack = "@maven_e2e//:pinned_maven_repository"

#sh_library(
#    name = "MavenImageDummy",
#    srcs = ["dummy.sh"],
#    data = [_buildpack],
#)
#
#sh_test(
#    name = "MavenImageTest",
#    srcs = ["test_files_exist.sh"],
#    args = ["external/maven_e2e/pinned_maven_repository.tar"],
#    data = [_buildpack],
#    deps = [":MavenImageDummy"]
#)

#
# Snap
#

sh_library(
    name = "MavenImageDummy2",
    srcs = ["dummy.sh"],
    data = ["@maven_e2e//:pinned_maven_repository"],
)

sh_test(
    name = "MavenImageTarTest",
    srcs = ["test_tar_repo.sh"],
    args = ["external/maven_e2e/pinned_maven_repository.tar"],
    data = ["@maven_e2e//:pinned_maven_repository"],
    deps = [":MavenImageDummy2"]
)

#
# running
#

_run = "//tests/e2e/mvn-build-lib"

sh_library(
    name = "MavenRunDummy",
    srcs = ["dummy.sh"],
    data = [_run],
)

sh_test(
    name = "MavenRunTest",
    srcs = ["test_jar.sh"],
    args = ["tests/e2e/mvn-build-lib/myapi-1.0.0-SNAPSHOT.jar", "com/mavenizer/examples/jaxbapi/API.class"],
    data = [_run],
    deps = [":MavenRunDummy"]
)

#
# profiles extra flags
#

sh_library(
    name = "mvn-build-lib-with-profile__maven",
    srcs = ["dummy.sh"],
    data = ["//tests/e2e/mvn-build-lib-with-profile"],
)

sh_test(
    name = "mvn-build-lib-with-profile__HasJarTest",
    srcs = ["test_jar.sh"],
    args = ["tests/e2e/mvn-build-lib-with-profile/libmvn-build-lib-with-profile.jar"],
    data = [":mvn-build-lib-with-profile__maven"],
    deps = [":mvn-build-lib-with-profile__maven"]
)