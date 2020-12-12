load(":repository.bzl", "PomDeclarationInfo", "RepositoryInfo")

MvnArtifactInfo = provider(fields = {
    "pkg": """
Artifact binaries from installed maven artifact.
Represent archived maven artifact content as is from the local repository.
    """
})

def _foramt_flags_as_escape_str(flags_list):
        return "'{}'".format(" ".join(flags_list))


def _create_manifest_file(name, ctx, dep_info_items):
    manifest = ctx.actions.declare_file(name + ".manifest")
    args = ctx.actions.args()
    for x in dep_info_items:
        args.add(x.to_json())
    ctx.actions.write(manifest, args)
    return manifest


def _dep_info(file, **kwargs):
    return struct(path=file, tags=dict(kwargs))


def _collect_deps(dep_targets, ctx_scope, **kwargs):
    _dep_infos = ctx_scope.manifests
    _direct_deps_files = ctx_scope.files
    for dep_target in dep_targets:
        if MvnArtifactInfo in dep_target:
            mvn_run_out = dep_target[MvnArtifactInfo]
            _dep_infos.append(_dep_info(mvn_run_out.pkg.path, artifact=True, **kwargs))
            _direct_deps_files.append(mvn_run_out.pkg)
        else:
            # Expect only java compatible targets
            java_provider = dep_target[JavaInfo]
            # We should use full_compile_jars to omit interface jars (ijar and hjar)
            # as maven build required fully compiled jar files
            for d in java_provider.full_compile_jars.to_list():
                # Rule scala_import provide specific jar to collect all exports
                # so, we should exclude it
                if d.path.endswith("PlaceHolderClassToCreateEmptyJarForScalaImport.jar"):
                    continue
                _dep_infos.append(_dep_info(d.path, **kwargs))
                _direct_deps_files.append(d)


def _collect_jars(ctx):
    """
    Collect only direct dependencies for each supported attrs
       * deps - equivalent of maven 'compile' scope
       * runtime_deps - equivalent of maven 'provided' scope
    """
    deps_ctx = struct(manifests=[], files=[])
    _collect_deps(ctx.attr.deps, deps_ctx)
    _collect_deps(ctx.attr.runtime_deps, deps_ctx, scope='provided')
    return deps_ctx



_run_mvn_outputs = {
    "jar": "lib%{name}.jar",
    "img": "%{name}_artifact.tar",
}

_run_mvn_attrs = {
    "repository": attr.label(mandatory=True),
    "deps": attr.label_list(),
    "runtime_deps": attr.label_list(),
    "srcs": attr.label_list(allow_files = True),
    "pom_def": attr.label(mandatory=True),
    "outputs": attr.string_list(),
    "data": attr.label_list(allow_files = True),
    "log_level": attr.string(default="OFF"),
    "_tool": attr.label(default="//private/tools/jvm/mvn", allow_files = True, executable = True, cfg = "host")
}

def _run_mvn_impl(ctx):
    repository_info = ctx.attr.repository[RepositoryInfo]
    pom_def_info = ctx.attr.pom_def[PomDeclarationInfo]

    deps_ctx = _collect_jars(ctx)
    srcs_manifest = _create_manifest_file("srcs_%s" % (ctx.label.name), ctx, [_dep_info(src.path) for src in ctx.files.srcs])
    deps_manifest = _create_manifest_file("deps_%s" % (ctx.label.name), ctx, deps_ctx.manifests)

    def_output_jar_file = ctx.outputs.jar
    def_output_artifact_file = ctx.outputs.img

    output_files = [def_output_jar_file, def_output_artifact_file]
    input_files = [
        pom_def_info.file,
        deps_manifest,
        srcs_manifest
    ] + deps_ctx.files

    input_transitive_files = [f.files for f in ctx.attr.srcs] + [ f.files for f in ctx.attr.data ]

    args = ctx.actions.args()
    args.add("--jvm_flag=-Dtools.jvm.mvn.LogLevel=%s" % (ctx.attr.log_level))
    args.add("--jvm_flag=-Dtools.jvm.mvn.BazelLabelName=%s" % (ctx.label))

    args.add("run")

    if repository_info.tar:
        args.add("--m2-repository", repository_info.tar.path)
        input_files.append(repository_info.tar)

    args.add("--srcs", srcs_manifest)
    args.add("--deps", deps_manifest)
    args.add("--write-artifact", def_output_artifact_file.path)
    args.add("--write-jar", def_output_jar_file.path)
    args.add("--pom", pom_def_info.file.path)
#    if pom_def_info.parent_file:
#        args.add("--parent-pom", pom_def_info.parent_file.path)
#        input_files.append(pom_def_info.parent_file)
    if pom_def_info.flags_line:
        args.add("--args", _foramt_flags_as_escape_str(pom_def_info.flags_line))

    for out in ctx.attr.outputs:
        file = ctx.actions.declare_file(out)
        output_files.append(file)
        args.add("-O{dest}={src}".format(dest=file.path, src=out))

    ctx.actions.run(
        inputs = depset(input_files, transitive = input_transitive_files),
        outputs = output_files,
        arguments = [args],
        executable = ctx.executable._tool,
        use_default_shell_env = True,
        progress_message = "running maven build... %s" % (ctx.label),
    )

    return [
        MvnArtifactInfo(pkg = def_output_artifact_file),
        DefaultInfo(files = depset(output_files)),
        JavaInfo(output_jar = def_output_jar_file, compile_jar = def_output_jar_file)
    ]


run_mvn = rule(
    implementation = _run_mvn_impl,
    attrs = _run_mvn_attrs,
    outputs = _run_mvn_outputs
)