
PomDeclarationInfo = provider(fields = {
    "file": "",
    "parent_file": "",
    "flags_line": "mvn flags, specific for each execution",
    "deps": "",
})



def _declare_pom_impl(ctx):
    pom_file = ctx.file.pom_file
    parent_file = None
    transitive_deps = []

    if ctx.attr.parent:
        mvn_module_info = ctx.attr.parent[PomDeclarationInfo]
        parent_file = mvn_module_info.file
        transitive_deps = [mvn_module_info.deps]

    return [
        DefaultInfo(files = depset([pom_file])),
        PomDeclarationInfo(
            file = pom_file,
            parent_file = parent_file,
            flags_line = ctx.attr.mvn_flags,
            deps = depset(direct=[pom_file], transitive = transitive_deps)
        )
    ]


declare_pom = rule(
    implementation = _declare_pom_impl,
    attrs = {
        "pom_file": attr.label(allow_single_file=True, mandatory = True),
        "parent": attr.label(),
        "mvn_flags": attr.string_list(),
    }
)

RepositoryInfo = provider(fields={
    "tar": "Snapshot"
})

_BuildDef = provider(fields={
    "file": "pom file",
    "parent_file": "pom file",
    "flags_line": "mvn flags, specific for each execution"
})


def _maven_repository_impl(ctx):
    tar = None

    reposiotry_def_args = ctx.actions.args()
    pom_providers = [dep[PomDeclarationInfo] for dep in ctx.attr.modules]
    for pom_provider in pom_providers:
        reposiotry_def_args.add(
            _BuildDef(
                file = pom_provider.file.path,
                parent_file = pom_provider.parent_file.path if pom_provider.parent_file else None,
                flags_line = pom_provider.flags_line
            ).to_json()
        )

    build_def = ctx.actions.declare_file('reposiotry_def_args.json')
    ctx.actions.write(
        output = build_def,
        content = reposiotry_def_args
    )

    args = ctx.actions.args()
    # jfm flags
    args.add("--jvm_flag=-Dtools.jvm.mvn.BazelLabelName=%s" % (ctx.label))
    args.add("--jvm_flag=-Dtools.jvm.mvn.Ws=%s" % (ctx.workspace_name))

    # cli options
    args.add('build-repository')
    args.add('--def', build_def.path)

    outputs = []

    args.add('--settings', ctx.attr.unsafe_global_settings)

    if not ctx.attr.use_global_cache:
        tar_name = ctx.label.name + ".tar"
        tar = ctx.actions.declare_file(tar_name)
        args.add('--mk-snapshot', tar.path)
        outputs.append(tar)

    ctx.actions.run(
        inputs = depset([build_def], transitive = [depset(ctx.files.data)] + [d.deps for d in pom_providers]),
        outputs = outputs,
        arguments = [args],
        executable = ctx.executable._tool,
        mnemonic = "MavenRepositoryMaker"
    )

    return [
        DefaultInfo(
            files= depset(outputs)
        ),
        RepositoryInfo(
           tar = tar
        )
    ]

maven_repository = rule(
    implementation = _maven_repository_impl,
    attrs = {
        "modules": attr.label_list(),
        "unsafe_global_settings": attr.string(),
        "use_global_cache": attr.bool(),
        "data": attr.label_list(allow_files = True),
        "_tool": attr.label(default="//private/tools/jvm/mvn", allow_files = True, executable = True, cfg = "host")
    }
)