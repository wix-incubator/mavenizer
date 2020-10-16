

_M2_REPO_IMG_EXT = ".tar"
_BASE_POM_NAME = "base_pom.xml"
_TOOL = Label("//private/tools/jvm/mvn:mvn")
_MARKER_SRC_DEFAULT_OUTPUT_JAR = "@@TARGET-JAR-OUTPUT@@"
#_MARKER_TALLY = "@@MVN_ARTIFACT_ALL@@"

MvnBuildpackInfo = provider()
_DepInfo = provider(
    fields = {
        "path": """Artifact binaries as is. Can be more then one file""",
        "tags": ""
    }
)
MvnRunArtifactInfo = provider(fields = {
    "tar": """Artifact binaries as is. Can be more then one file"""
})


def _merged_dict(dicta, dictb):
    return dict(dicta.items() + dictb.items())

_common_attr = {
    "log_level": attr.string(doc="specify log level for the tool")
}

_create_mvn_repository_attr_outputs = {
    "image": "%{name}_img.tar",
}

_create_mvn_repository_attr = _merged_dict(
    _common_attr,
    {
        "pom_parent": attr.label(allow_single_file = True),
        "pom_file_vars": attr.string_dict(),
        "pom_file": attr.label(allow_single_file = True, mandatory = True),
        "_tool": attr.label(default = _TOOL, allow_files = True, executable = True, cfg = "host")
    }
)


def _rlocation(ctx, file):
       return "${RUNFILES_DIR}/" + ctx.workspace_name + "/" + file.short_path

def _create_mvn_repository_impl(ctx):
    pom_file = ctx.file.pom_file
    archive = ctx.outputs.image
    optional_transitive_inputs = []

    args = ctx.actions.args()
    if ctx.attr.log_level:
        # add jvm flags for java_binary
        args.add("--jvm_flag=-Dtools.jvm.mvn.LogLevel=%s" % (ctx.attr.log_level))

    args.add("repo2tar")
    args.add("--output", archive.path)
    args.add("--pom", pom_file.path)
    pom_parent_file = ctx.file.pom_parent
    if pom_parent_file:
            args.add("--parent", pom_parent_file.path)
            optional_transitive_inputs.append(pom_parent_file)

    ctx.actions.run(
        inputs = depset([pom_file], transitive = [depset(optional_transitive_inputs)]),
        outputs = [archive],
        arguments = [args],
        executable = ctx.executable._tool,
        # use_default_shell_env = True,
        progress_message = "createing mvn embedded tool... %s" % (ctx.label),
    )

    # Write the wrapper.
    # There is a {rulename}.runfiles directory adjacent to the tool's
    # executable file which contains all runfiles. This is not guaranteed
    # to be relative to the directory in which the executable file is run.

    # Use java binary as maven executable
    java_binary_tool = ctx.workspace_name + "/" + ctx.attr._tool[DefaultInfo].files_to_run.executable.short_path

    # Since this tool may be used by another tool, it must support accepting
    # a different runfiles directory root. The runfiles directory is always
    # adjacent to the *root* tool being run, which may not be this tool.
    # (In this case, this is done by environment variable RUNFILES_DIR.)

    runfiles_relative_archive_path = _rlocation(ctx, archive)
    runfiles_relative_pom_path = _rlocation(ctx, pom_file)
    if pom_parent_file: runfiles_relative_parent_pom_path = _rlocation(ctx, pom_parent_file)

    script_args = " ".join([
        "build",
        "--pom={}".format(runfiles_relative_pom_path),
        "--repo={}".format(runfiles_relative_archive_path),
        "--parent={}".format(runfiles_relative_parent_pom_path) if pom_parent_file else ""
    ])

    executable = ctx.outputs.executable
    ctx.actions.write(
        output = executable,
        content = "\n".join([
            "#!/bin/bash",
            "# !! Autogenerated - do not edit !!",
            "if [[ -z \"${RUNFILES_DIR}\" ]]; then",
            "  RUNFILES_DIR=${0}.runfiles",
            "fi",
            "",
            "jvm_bin=${RUNFILES_DIR}/%s" % (java_binary_tool),
            "args=\"%s\"" % (script_args),
            "${jvm_bin} ${args} \"$@\""
        ]),
        is_executable = True,
    )

    runfiles = ctx\
        .runfiles(files = [pom_file, executable, archive, ctx.executable._tool] + optional_transitive_inputs)\
        .merge(ctx.attr._tool[DefaultInfo].default_runfiles)

    files_to_build = depset(direct=[executable], transitive = [
        depset([archive, pom_file] + optional_transitive_inputs)
    ])
    return [
        DefaultInfo(runfiles = runfiles, files = files_to_build),
        MvnBuildpackInfo()
    ]


create_mvn_buildpack = rule(
    implementation = _create_mvn_repository_impl,
    attrs = _create_mvn_repository_attr,
    outputs = _create_mvn_repository_attr_outputs,
    executable = True,
)



_run_mvn_buildpack_attr = _merged_dict(
    _common_attr,
    {
        "deps": attr.label_list(),
        "srcs": attr.label_list(mandatory = True,allow_files = True),
        "outputs": attr.string_list(),
        "artifactId": attr.string(),
        "groupId": attr.string(),
        "args": attr.string_list(),
        "install": attr.bool(),
        "buildpack": attr.label(
            mandatory = True,
            allow_files = True,
            executable = True,
            cfg = "host",
        )
    }
)

def _create_manifest_file(name, ctx, files_paths):
    manifest = ctx.actions.declare_file(name + ".manifest")
    args = ctx.actions.args()
    for x in files_paths:
        args.add(x.to_json())
    ctx.actions.write(manifest, args)
    return manifest


def _collect_dep(file, **kwargs):
    # Dep id if url with file schema
    # All metadata comes as url params
    return _DepInfo(path=file, tags=dict(kwargs))


def _collect_deps(dep_targets):
    # Collect only direct dependencies for each target
    _direct_deps = []
    _direct_deps_files = []
    for dep_target in dep_targets:
        if MvnRunArtifactInfo in dep_target:
            mvn_run_out = dep_target[MvnRunArtifactInfo]
            _direct_deps.append(_collect_dep(mvn_run_out.tar.path, artifact=True))
            _direct_deps_files.append(mvn_run_out.tar)
            continue

        # Expect only java compatible targets
        java_provider = dep_target[JavaInfo]

        # We should use full_compile_jars to omit interface jars (ijar and hjar)
        # as maven build required fully compiled jar files
        for d in java_provider.full_compile_jars.to_list():
            # Rule scala_import provide specific jar to collect all exports
            # so, we should exclude it
            if d.path.endswith("PlaceHolderClassToCreateEmptyJarForScalaImport.jar"):
                continue
            _direct_deps.append(_collect_dep(d.path))
            _direct_deps_files.append(d)

    return _direct_deps, _direct_deps_files


def _run_mvn_buildpack_impl(ctx):
    if not ctx.attr.buildpack[MvnBuildpackInfo]:
        fail("attr.buildpack must be created by 'create_mvn_buildpack' rule")

    deps_ids, deps = _collect_deps(ctx.attr.deps)
    srcs_manifest = _create_manifest_file("srcs--%s" % (ctx.label.name), ctx, [_collect_dep(src.path) for src in ctx.files.srcs])
    deps_manifest = _create_manifest_file("deps--%s" % (ctx.label.name), ctx, deps_ids)
    providers = []
    outputs = []
    output_flags = []
    output_param_format = "-O{declared_file}={file_in_mvn_target}"
    for o in ctx.attr.outputs:
        declare_file = ctx.actions.declare_file(o)
        outputs.append(declare_file)
        output_flags.append(
            output_param_format.format(
                declared_file = declare_file.path,
                file_in_mvn_target = o
            )
        )

    args = ctx.actions.args()
    args.add("--srcs", srcs_manifest)
    args.add("--deps", deps_manifest)
    # if ctx.attr.args: args.add("--args", " ".join(ctx.attr.args))
    if ctx.attr.artifactId: args.add("--artifactId", ctx.attr.artifactId)
    if ctx.attr.groupId: args.add("--groupId", ctx.attr.groupId)

    special_output_flags = []
    special_output_flags_fmt = "--defOutFlag={flag}={declared_file}"

    def_output_jar = "lib" + ctx.label.name + ".jar"
    def_output_jar_file = ctx.actions.declare_file(def_output_jar)
    outputs.append(def_output_jar_file)
    special_output_flags.append(
        special_output_flags_fmt.format(flag="@DEF_JAR", declared_file=def_output_jar_file.path)
    )

    def_output_pkg = "lib" + ctx.label.name + "_pkg.tar"
    def_output_pkg_file = ctx.actions.declare_file(def_output_pkg)
    outputs.append(def_output_pkg_file)
    special_output_flags.append(
        special_output_flags_fmt.format(flag="@DEF_PKG", declared_file=def_output_pkg_file.path)
    )

    if ctx.attr.install:
        pass
#        tally = "%s_tally.tar" % (ctx.label.name)
#        tally_file = ctx.actions.declare_file(tally)
#        declared_outputs.append(tally_file)
#        output_flags.append(
#            output_param_format.format(
#                declared_file = tally_file.path,
#                file_in_mvn_target = _MARKER_TALLY
#            )
#        )
#        providers.append(
#            MvnRunArtifactInfo(tar = tally_file)
#        )

    args.add_all(output_flags)
    args.add_all(special_output_flags)

    if ctx.attr.log_level:
        # add jvm flags for java_binary
        # wrap via wrapper_script_flag so it have to goes at the end of args line
        args.add("--wrapper_script_flag=--jvm_flag=-Dtools.jvm.mvn.LogLevel=%s" % (ctx.attr.log_level))

    # Name of current target + package
    args.add("--wrapper_script_flag=--jvm_flag=-Dtools.jvm.mvn.BazelLabelName=%s" % (ctx.label))

    ctx.actions.run(
        inputs = depset([srcs_manifest, deps_manifest],
                        transitive = [depset(deps)] + [f.files for f in ctx.attr.srcs]),
        outputs = outputs,
        arguments = [args],
        executable = ctx.executable.buildpack,
        use_default_shell_env = True,
        progress_message = "running maven build... %s" % (ctx.label),
    )

    runfiles = ctx.runfiles(files = outputs).merge(ctx.attr.buildpack[DefaultInfo].default_runfiles)

    return providers + [
        DefaultInfo(files = depset(outputs), runfiles = runfiles),
        JavaInfo(output_jar = def_output_jar_file, compile_jar = def_output_jar_file)
    ]


run_mvn_buildpack = rule(
    implementation = _run_mvn_buildpack_impl,
    attrs = _run_mvn_buildpack_attr,
)
