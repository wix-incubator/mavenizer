
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", _jvm_maven_import_external = "jvm_maven_import_external")

_def_server_urls = [
        "https://jcenter.bintray.com/",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
]

_maven_binary_version = "3.6.3"
_maven_binary_sha256 = "26ad91d751b3a9a53087aefa743f4e16a17741d3915b219cf74112bf87a438c5"

MAVEN_BINARY_NAME = "wix_incubator_bazelizer_maven_binary_tool"
MAVEN_BINARY_NAME_CLI = "wix_incubator_bazelizer_maven_binary_tool"

def _jvm_external_deps(deps):
    return [ "@" + _jvm_external_name( x ) for x in  deps ]

def _jvm_external_name(name):
    return "wix_incubator_bazelizer_" + str(name)

def _jvm_import(name,artifact,server_urls,fetch_sources,**kwargs):
    _id = _jvm_external_name(name)
    if 'deps' in kwargs:
        kwargs['deps'] = _jvm_external_deps(kwargs['deps'])

    _jvm_maven_import_external(
        name= _id,
        artifact=artifact,
        server_urls=server_urls,
        fetch_sources=fetch_sources,
        licenses = ["notice"], # Apache 2.0
        **kwargs)

    native.bind(
        name = "wix_incubator_bazelizer_rules/dependency/%s" % (name),
        actual = "@%s" % (_id)
    )


_MAVEN_BUILD_FILE = """

filegroup(
    name = "{mvn_files_target}",
    visibility = ["//visibility:public"],
    srcs = glob(["bin/**", "boot/**", "conf/**", "lib/**"]),
)
"""

def install(server_urls = _def_server_urls, sources = False):

    if native.existing_rule(MAVEN_BINARY_NAME) == None:
        http_archive(
           name = MAVEN_BINARY_NAME,
           url = "https://www2.apache.paket.ua/maven/maven-3/" + _maven_binary_version + "/binaries/apache-maven-" + _maven_binary_version + "-bin.tar.gz",
           build_file_content = _MAVEN_BUILD_FILE.format(
               mvn_files_target=MAVEN_BINARY_NAME
            ),
           sha256 = _maven_binary_sha256,
           strip_prefix = "apache-maven-" + _maven_binary_version
        )

    _jvm_import(
        name = "org_apache_maven_shared_maven_invoker",
        artifact = 'org.apache.maven.shared:maven-invoker:3.0.1',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "d20e5d26c19c04199c73fd4f0b6caebf4bbdc6b872a4504c5e71a192751d9464",
        srcjar_sha256 = "94b766c063bf6345faa571ada4bd19fd4804ff2354ea6779d5894b3e37c7afa7",
    )

    _jvm_import(
        name = "org_apache_maven_shared_maven_shared_utils",
        artifact = 'org.apache.maven.shared:maven-shared-utils:3.2.1',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "3ba9c619893c767db0f9c3e826d5118b57c35229301bcd16d865a89cec16a7e5",
        srcjar_sha256 = "25064c72c178a98335048d0f7c3e08839e949426bc92bf905ea964146235f388",
    )

    _jvm_import(
        name = "com_github_spullara_mustache_java_compiler",
        artifact = 'com.github.spullara.mustache.java:compiler:0.9.6',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "c4d697fd3619cb616cc5e22e9530c8a4fd4a8e9a76953c0655ee627cb2d22318",
        srcjar_sha256 = "fb3cf89e4daa0aaa4e659aca12a8ddb0d7b605271285f3e108201e0a389b4c7a",
    )

    _jvm_import(
        name = "info_picocli_picocli",
        artifact = 'info.picocli:picocli:4.5.0',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "9058d90167d293f1379f49129f94424bc4c8c6cdc6a33e31bd6e4886a77733c1",
        srcjar_sha256 = "ccdad5e02718c03ef21fe0767425cb12cbf24c1be072f4b9414446fbe3332637",
    )

    _jvm_import(
        name = "com_jcabi_jcabi_xml",
        artifact = 'com.jcabi:jcabi-xml:0.22.2',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "dba351234ffa0c37557c45d06eed51b3c972f801ad56f9bfb12c6a355469e2c3",
        srcjar_sha256 = "c68148e5343c7bfa8e3aafb2ebbefdadd49d8518612f8703fc4ce9cab0d93dc5",
        deps = ["com_jcabi_jcabi_log", "org_cactoos_cactoos"]
    )

    _jvm_import(
        name = "com_jcabi_jcabi_log",
        artifact = 'com.jcabi:jcabi-log:0.17.2',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "9f7bc8d4ea2b17287e53add1415f08dd8095543c81db82e78c65f20d23f2454b",
        srcjar_sha256 = "1f013cef943e68f1c966714cc1fbb412c8cc791f0d500f99175634546c460c81",
    )

    _jvm_import(
	    name = "org_cactoos_cactoos",
        artifact = 'org.cactoos:cactoos:0.20',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "3ac075084c5dda9ede829d74a382fbdbe29fb49a4df2abf4d79bf01d924801f7",
        srcjar_sha256 = "010aafbc1d9ef952c7c5d74e3aca589f1474e92ae0a499439a507fd65ab95775",
    )

    _jvm_import(
	    name = "junit_junit",
        artifact = 'junit:junit:4.13.1',
        fetch_sources = False,
        server_urls = server_urls,
        testonly = 1,
        artifact_sha256 = "c30719db974d6452793fe191b3638a5777005485bae145924044530ffa5f6122",
    )

    _jvm_import(
	    name = "org_hamcrest_hamcrest_core",
        artifact = 'org.hamcrest:hamcrest-core:1.3',
        fetch_sources = False,
        server_urls = server_urls,
        testonly = 1,
        artifact_sha256 = "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9",
    )

    _jvm_import(
	    name = "org_hamcrest_hamcrest_library",
        artifact = 'org.hamcrest:hamcrest-library:1.3',
        fetch_sources = False,
        server_urls = server_urls,
        testonly = 1,
        artifact_sha256 = "711d64522f9ec410983bd310934296da134be4254a125080a0416ec178dfad1c",
    )

    _jvm_import(
	    name = "org_projectlombok_lombok",
        artifact = 'org.projectlombok:lombok:1.18.12',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406",
        srcjar_sha256 = "e673bd5c8a9e253ca32c8a40b8f433659d4bc34dae19813182eb430a79d5a4d9",
    )

    _jvm_import(
	    name = "com_google_guava_guava",
        artifact = 'com.google.guava:guava:29.0-jre',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "b22c5fb66d61e7b9522531d04b2f915b5158e80aa0b40ee7282c8bfb07b0da25",
        srcjar_sha256 = "cfcbe29dd5125f5b360370b4ecd7f7ef44fba68f4f037e90bce7315682afc0ea",
    )

    _jvm_import(
	    name = "ch_qos_logback_logback_classic",
        artifact = 'ch.qos.logback:logback-classic:1.2.3',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "fb53f8539e7fcb8f093a56e138112056ec1dc809ebb020b59d8a36a5ebac37e0",
        srcjar_sha256 = "480cb5e99519271c9256716d4be1a27054047435ff72078d9deae5c6a19f63eb",
    )

    _jvm_import(
	    name = "ch_qos_logback_logback_core",
        artifact = 'ch.qos.logback:logback-core:1.2.3',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "5946d837fe6f960c02a53eda7a6926ecc3c758bbdd69aa453ee429f858217f22",
        srcjar_sha256 = "1f69b6b638ec551d26b10feeade5a2b77abe347f9759da95022f0da9a63a9971",
    )

    _jvm_import(
	    name = "org_slf4j_slf4j_api",
        artifact = 'org.slf4j:slf4j-api:1.7.30',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "cdba07964d1bb40a0761485c6b1e8c2f8fd9eb1d19c53928ac0d7f9510105c57",
        srcjar_sha256 = "9ee459644577590fed7ea94afae781fa3cc9311d4553faee8a3219ffbd7cc386",
    )

    _jvm_import(
	    name = "commons_io_commons_io",
        artifact = 'commons-io:commons-io:2.5',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "a10418348d234968600ccb1d988efcbbd08716e1d96936ccc1880e7d22513474",
        srcjar_sha256 = "3b69b518d9a844732e35509b79e499fca63a960ee4301b1c96dc32e87f3f60a1",
    )

    _jvm_import(
	    name = "org_apache_commons_commons_compress",
        artifact = 'org.apache.commons:commons-compress:1.20',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "0aeb625c948c697ea7b205156e112363b59ed5e2551212cd4e460bdb72c7c06e",
        srcjar_sha256 = "0eb5d7f270c2fccdab31daa5f7091b038ad0099b29885040604d66e07fd46a18",
    )

    _jvm_import(
	    name = "com_google_code_gson_gson",
        artifact = 'com.google.code.gson:gson:jar:2.8.6',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f",
        srcjar_sha256 = "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc",
    )

    _jvm_import(
	    name = "com_jcabi_incubator_xembly",
        artifact = "com.jcabi.incubator:xembly:0.24.0",
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "81684631e93c1db7030581a9c83c14feb84cc596f61d46957b5dd4d31c78a092",
        srcjar_sha256 = "5bf5f56a7610154fbd730a4c1722fb17fe13c9e669ddc8751ed590d73cc552ae",
        deps = [ "org_antlr_antlr_runtime", "org_mockito_mockito_core" ]
    )

    _jvm_import(
	    name = "org_antlr_antlr_runtime",
        artifact ='org.antlr:antlr-runtime:jar:3.5.2',
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "ce3fc8ecb10f39e9a3cddcbb2ce350d272d9cd3d0b1e18e6fe73c3b9389c8734",
        srcjar_sha256 = "3a8fde6cabadd1f6c6dcddc92edbe17501448e0553fee893cfc62becce57531a",
    )

    _jvm_import(
	    name = "org_mockito_mockito_core",
        artifact = "org.mockito:mockito-core:jar:3.5.15",
        fetch_sources = sources,
        server_urls = server_urls,
        artifact_sha256 = "f2c9c7080ada5640d28d01fc29d814438dd143e5331f76ef6908dcdd3ec141f0",
        srcjar_sha256 = "a5e40b66d9ed5cbab59d7eb9950d9da44a24518f274d0c68ef3d743313117e7f",
    )

    _jvm_import(
	    name = "org_thymeleaf_thymeleaf",
        artifact = "org.thymeleaf:thymeleaf:jar:3.0.11.RELEASE",
        fetch_sources = sources,
        server_urls = server_urls,
        deps = [ "@ognl_ognl", "@org_attoparser_attoparser", ]
#        artifact_sha256 = "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f",
#        srcjar_sha256 = "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc",
    )

    _jvm_import(
	    name = "ognl_ognl",
        artifact = "ognl:ognl:jar:3.1.12",
        fetch_sources = sources,
        server_urls = server_urls,
        deps = ["@org_javassist_javassist"]
#        artifact_sha256 = "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f",
#        srcjar_sha256 = "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc",
    )

    _jvm_import(
	    name = "org_attoparser_attoparser",
        artifact = "org.attoparser:attoparser:jar:2.0.5.RELEASE",
        fetch_sources = sources,
        server_urls = server_urls,
#        artifact_sha256 = "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f",
#        srcjar_sha256 = "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc",
    )

    _jvm_import(
	    name = "org_javassist_javassist",
        artifact = "org.javassist:javassist:jar:3.20.0-GA",
        fetch_sources = sources,
        server_urls = server_urls,
#        artifact_sha256 = "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f",
#        srcjar_sha256 = "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc",
    )