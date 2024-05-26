# `hprofdap`

CLI and debug adapter (DAP) that allows to inspect Java heap dumps (.hprof files) via OQL

[See a demo](https://social.fussenegger.pro/system/media_attachments/files/112/506/312/700/884/993/original/f231c3535e15f3b9.mp4)


## Build

Requires Java 22

```bash
mvn package
```

## DAP Installation/Configuration

`hprofdap` communicates via stdio.

Configure a debug-adapter client to spawn:

```bash
java \
    -Dpolyglot.engine.WarnInterpreterOnly=false \
    -jar path/to/repo/target/hprofdap-0.1.0-jar-with-dependencies.jar
```

Currently only the `launch` type is supported with one additional configuration
property: `filepath`, which is expected to be a absolute path to the `.hprof` file
you want to analyze.


### nvim-dap example


```lua
dap.adapters.hprof = {
  type = "executable",
  command = os.getenv("JDK22") .. "/bin/java", -- or just "java"
  args = {
    "-Dpolyglot.engine.WarnInterpreterOnly=false",
    "-jar",
    vim.fn.expand("~/path/to/hprofdap/target/hprofdap-1.0-jar-with-dependencies.jar"),
  }
}
dap.configurations.java = {
  {
    name = "hprof",
    request = "launch",
    type = "hprof",
    filepath = function()
      return require("dap.utils").pick_file({
        executables = false,
        filter = "%.hprof$"
      })
    end,
  },
}
```

## Usage

A `.hprof` file cannot be executed, therefore most of the functionality
available during a regular debug session won't work. There are no breakpoints
to hit, no stopped events, or anything like that.

Instead you can query the heap dump via `OQL` via the debug adapter's client's
evaluate functionality.

Some examples of `OQL` queries:

```oql
select file from java.io.File file
```

```oql
select file.path from java.io.File file
```

```oql
select s from int[] s where s.length > 4000
```


## Development


### Debugging

Start `hprofdap` with `jdwp` enabled and use the bundled `.vscode/launch.json`
attach configuration

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005
```
