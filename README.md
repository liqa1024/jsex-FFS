<!-- = Requirement

TODO -->

= Usage

Run groovy script in `mpiexec` like:

```text
mpiexec -np <ncores> jse <path/to/script.groovy> <workingDir> [args...]
```

for example:

```shell
mpiexec -np 64 jse NiAl.groovy NiAl-FFS
```

