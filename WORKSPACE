repository_name = "wix_incubator_bazelizer"

workspace(name = repository_name)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

load("//third_party:rules_repository.bzl", "install")


rules_kotlin_version = "legacy-1.3.0"
rules_kotlin_sha = "4fd769fb0db5d3c6240df8a9500515775101964eebdf85a3f9f0511130885fde"
http_archive(
    name = "io_bazel_rules_kotlin",
    urls = ["https://github.com/bazelbuild/rules_kotlin/archive/%s.zip" % rules_kotlin_version],
    type = "zip",
    strip_prefix = "rules_kotlin-%s" % rules_kotlin_version,
    sha256 = rules_kotlin_sha,
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")
kotlin_repositories() # if you want the default. Otherwise see custom kotlinc distribution below
kt_register_toolchains() # to use the default toolchain, otherwise see toolchains below


install(sources = True)


#
# E2E tests
#

load("//maven:defs.bzl", "maven_repository_registry")
maven_repository_registry(
    name = "maven_e2e",
    modules = [
        "//tests/e2e/mvn-parent-pom:declared_pom",
        "//tests/e2e/mvn-build-lib:declared_pom",
        "//tests/e2e/mvn-build-lib-one:declared_pom",
        "//tests/e2e/mvn-build-lib-with-profile:declared_pom",
    ],
    use_global_cache = False
)