# Requirement

- [jse](https://github.com/CHanzyLazer/jse/) (latest)

- C & C++ Compiler

  For Windows, [MSVC](https://visualstudio.microsoft.com/vs/features/cplusplus/) is recommended

  For Linux, [GCC](https://gcc.gnu.org/) is recommended

- MPI Development Environment

  For Windows, [Microsoft MPI](https://www.microsoft.com/download/details.aspx?id=105289) is recommended
  (both `msmpisdk.msi` and `msmpisetup.exe` are required)

  For Linux like Ubuntu, you can use `sudo apt install libopenmpi-dev`

- Environment variable:
  
  ```shell
  export JSE_LMP_PKG=MANYBODY
  ```
  
  to ensure auto compiled LAMMPS can run EAM potential

# Usage

## Random walk

The `randomwalk.groovy` script demonstrates using FFS to sample a one-dimensional
random walk process. This script only requires the basic jse. Run:

```shell
jse randomwalk.groovy
```

to execute the script directly. By default, it will calculate the rate of a
random walk from 0 to a distance of 10, with a theoretical value of 0.01.


## Nucleation Rate in Supercooled Metallic Glass-Forming Liquids

`CuZr.groovy` and  `NiAl.groovy` are used to sample the nucleation rates for
the Cu60Zr40 and Ni50Al50 systems, respectively.

Before running for the first time, first ensure the above requirements are met.
Then run `initlmp.groovy` for automatic initialization (automatically downloading
and compiling LAMMPS, as well as compiling the relevant JNI libraries):

```shell
jse initlmp.groovy
```

Run the Groovy script in `mpiexec` as follows:

```text
mpiexec -np <ncores> jse <path/to/script.groovy> <workingDir> [args...]
```

For example:

```shell
mpiexec -np 64 jse NiAl.groovy NiAl-FFS
```

For Windows, you may need to use `jse.bat` with MPI like:

```shell
mpiexec -np 64 jse.bat NiAl.groovy NiAl-FFS
```


# Citation

Qing-an Li, Yuxuan Chen, Bin Xu, Shiwu Gao, Pengfei Guan,
[Revealing Crystal Nucleation Behaviors in Metallic Glass-Forming Liquids
via Parallel Forward Flux Sampling with Multi-Type Bond-Orientational Order
Parameter](https://www.sciencedirect.com/science/article/abs/pii/S1359645425011589),
*Acta Materialia* **(2025)**


# License

Scripts on this repository are licensed under the **GNU GPL v3**.
See [LICENSE](LICENSE) for details.

