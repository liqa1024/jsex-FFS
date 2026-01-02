/**
 * This file is part of example scripts of FFS in jse
 * Copyright 2025 Qing'an Li
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import groovy.transform.Field
import jse.atom.Structures
import jse.clib.LmpCore
import jse.code.Conf
import jse.code.OS.Slurm
import jse.code.IO
import jse.code.UT
import jse.code.io.RefreshableFilePrintStream
import jse.code.random.LocalRandom
import jse.lmp.Dump
import jse.lmp.Lmpdat
import jse.lmp.NativeLmp
import jse.math.MathEX
import jse.parallel.MPI
import jsex.rareevent.ForwardFluxSampling
import jsex.rareevent.atom.ABOOPSolidChecker_MPI
import jsex.rareevent.atom.MultiTypeClusterSizeCalculator
import jsex.rareevent.lmp.MultipleNativeLmpFullPathGenerator

import static jse.code.CS.MASS
import static jse.code.CS.VERSION
import static jse.code.UT.Code.*
import static jse.code.UT.Math.rng
import static jse.code.IO.Text.percent
import static jsex.rareevent.ForwardFluxSampling.*

final def workingDirIn = args ? args[0] : 'CuZr-FFS'

final boolean isFS1 = false

final int Cu = 60
final int Zr = 100-Cu
final int replicate = 15
@Field static def pairStyle
@Field static def pairCoeff
pairStyle = 'eam/fs'
pairCoeff = isFS1 ? '* * pot/Cu-Zr_2.eam.fs Cu Zr' : '* * pot/Cu-Zr_4.eam.fs Cu Zr'

final int atomNum = replicate*replicate*replicate*4

/** MPI init */
@Field static int me
@Field static int np
MPI.initThread(args, MPI.Thread.MULTIPLE) // MPI.Thread.MULTIPLE required for thread safe
me = MPI.Comm.WORLD.rank()
np = MPI.Comm.WORLD.size()
// redirect output
if (me == 0 && Slurm.IS_SLURM) {
    System.setOut(new RefreshableFilePrintStream("slurm-${Slurm.JOB_ID}.jout"))
}
if (me == 0) println("JSE_VERSION: ${VERSION}")
if (me == 0) println("MPI_VERSION: ${MPI.libraryVersion()}")

for (_ in range(me)) rng().nextInt()
rng(rng().nextLong())

if (me == 0) println("LMP_EXE: ${LmpCore.EXE_PATH}")

@Field final static boolean pbar = true
if (pbar) {
    Conf.PBAR_ERR_STREAM = false
    Conf.UNICODE_SUPPORT = false
}


/** simulation setting */
final boolean genInitPoints = true
final boolean genPostInitPoints = true

final double initTimestep   = 0.002 // ps
final double cellSize       = 4.0
final int meltTemp          = 2000
final int FFSTemp           = 800
final int initRunStep       = 500000
final int FFSRunStep        = isFS1 ? 500000 : 5000000
final int FFSContinueStep   = 200000

final def workingDir   = "${workingDirIn}/"

final def initDir           = workingDir+'init/'
final def initInDataPath    = initDir+'data-in'
final def initOutDataPath   = initDir+'data-out'

final def FFSDir            = workingDir+'ffs/'
final def FFSInDataPath     = FFSDir+'data-in'
final def FFSOutDataPath    = FFSDir+'data-out'

/** FFS setting */
final boolean dumpAllPath   = false
final boolean printPathEff  = true

final double timestep       = 0.002 // ps

final int lmpCores          = 8
final int initParallelNum   = MathEX.Code.divup(np, lmpCores)

final int color             = me.intdiv(lmpCores)
final def subComm           = MPI.Comm.WORLD.split(color)
final def subRoots          = range(0, np, lmpCores)

final int dumpStep          = 10000 // tau_gap = 20 ps for timestep == 0.02

final int N0                = 100
final int step1Mul          = 2
final double surfaceA       = 10

final def surfaces = isFS1 ?
    [20,     30,     40,     50,     60,     70,     80,     90,     100,      110,      120,      140,      160,      180,      200] :
    [20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 130, 140, 150, 160, 170, 180, 190, 200]

final double pruningProb    = 0.5
final int pruningThreshold  = 3
final int pruningStep       = MathEX.Code.divup(100000, dumpStep) // 200 ps for timestep == 0.02
final byte pruningPolicy    = CONSERVATIVE_GUESS

final def FFSDumpPath       = workingDir+'dump-0'
final def FFSRestartPathDu  = workingDir+'restart-dump'
final def FFSRestartPathRe  = workingDir+'restart-rest'
final def FFSAllDumpPath    = workingDir+'dump-all'



/** structure init */
if (genInitPoints) try (def lmp = new NativeLmp('-log', 'none', '-screen', 'none')) {
    if (me == 0) {
    println("=====GENERATOR MELT DATA OF Cu${Cu}Zr${Zr}=====")
    println("PAIR_STYLE: ${pairStyle}")
    println("PAIR_COEFF: ${pairCoeff}")
    println("ATOM_NUM: ${atomNum}")
    println("TEMPERATURE: ${meltTemp}K")
    println("TIMESTEP: ${initTimestep} ps")
    println("STEP_NUM: ${initRunStep}")
    println("INIT_CORE_NUM: ${np}")
    }
    if (me == 0) UT.Timer.tic()
    def inDataInit = Lmpdat.fromAtomData(Structures.FCC(cellSize, replicate).opt().mapTypeRandom(new LocalRandom(seed()), Cu, Zr), [MASS.Cu, MASS.Zr])
    MPI.Comm.WORLD.barrier()
    runMelt(MPI.Comm.WORLD, lmp, inDataInit, initOutDataPath, meltTemp, initRunStep, initTimestep)
    MPI.Comm.WORLD.barrier()
    if (me == 0) UT.Timer.toc('Init melt data')
    
    if (me == 0) {
    println("=====COOLDOWN DATA AND GET THE FFS DATA=====")
    println("PAIR_STYLE: ${pairStyle}")
    println("PAIR_COEFF: ${pairCoeff}")
    println("ATOM_NUM: ${atomNum}")
    println("TEMPERATURE: ${FFSTemp}K")
    println("TIMESTEP: ${initTimestep} ps")
    println("STEP_NUM: ${FFSRunStep}")
    println("INIT_CORE_NUM: ${np}")
    }
    if (me == 0) UT.Timer.tic()
    if (me == 0) IO.copy(initOutDataPath, FFSInDataPath)
    MPI.Comm.WORLD.barrier()
    runMelt(MPI.Comm.WORLD, lmp, FFSInDataPath, FFSOutDataPath, FFSTemp, FFSRunStep, initTimestep)
    MPI.Comm.WORLD.barrier()
    if (me == 0) UT.Timer.toc('Gen FFS data')
}

if (genInitPoints || genPostInitPoints) try (def lmp = new NativeLmp(['-log', 'none', '-screen', 'none'], subComm)) {
    if (me == 0) {
    println("=====CONTINUE FFS DATA AND GET ${initParallelNum} FFS DATA=====")
    println("PAIR_STYLE: ${pairStyle}")
    println("PAIR_COEFF: ${pairCoeff}")
    println("ATOM_NUM: ${atomNum}")
    println("TEMPERATURE: ${FFSTemp}K")
    println("TIMESTEP: ${initTimestep} ps")
    println("STEP_NUM: ${FFSContinueStep}")
    println("PARALLEL_NUM: ${initParallelNum}")
    println("LMP_CORE_NUM: ${lmpCores}")
    }
    if (me == 0) UT.Timer.tic()
    if (subComm.rank() == 0) IO.copy(FFSOutDataPath, "${FFSInDataPath}-${color}")
    MPI.Comm.WORLD.barrier()
    runMelt(subComm, lmp, "${FFSInDataPath}-${color}", "${FFSOutDataPath}-${color}", FFSTemp, FFSContinueStep, initTimestep)
    MPI.Comm.WORLD.barrier()
    if (me == 0) UT.Timer.toc('Continue FFS data')
}


def initPoints = range(initParallelNum).collect {Lmpdat.read("${FFSOutDataPath}-${it}")}

def calComm = subComm.copy()
def dumpCal = new MultiTypeClusterSizeCalculator(
    new ABOOPSolidChecker_MPI().setComm(calComm).setRNearestMul(1.5).setConnectThreshold(0.89).setUseRatio(),
    [new ABOOPSolidChecker_MPI().setComm(calComm).setRNearestMul(1.8).setConnectThreshold(0.84).setUseRatio(),
     new ABOOPSolidChecker_MPI().setComm(calComm).setRNearestMul(1.5).setConnectThreshold(0.84).setUseRatio()]
)

MPI.Comm.WORLD.barrier()
MultipleNativeLmpFullPathGenerator.NO_LMP_IN_WORLD_ROOT = false // or Slurm.IS_SLURM
MultipleNativeLmpFullPathGenerator.withOf(subComm, subRoots, dumpCal, initPoints, [MASS.Cu, MASS.Zr], FFSTemp, pairStyle, pairCoeff, timestep, dumpStep) {fullPathGen ->
    int parallelNum = fullPathGen.parallelNum()
    
    /** begin FFS */
    println("=====BEGIN FFS OF Cu${Cu}Zr${Zr}=====")
    println("TIMESTEP: ${timestep} ps")
    println("PAIR_STYLE: ${pairStyle}")
    println("PAIR_COEFF: ${pairCoeff}")
    println("PARALLEL_NUM: ${parallelNum}")
    println("LMP_CORE_NUM: ${lmpCores}")
    println("TEMPERATURE: ${FFSTemp}K")
    println("N0: ${N0}")
    println("DUMP_STEP: ${dumpStep}")
    println("SURFACE_A: ${surfaceA}")
    println("SURFACES: ${surfaces}")
    if (pruningPolicy==TIME_DEPENDENT) {
    println("PRUNING_STEP: ${pruningStep}")
    println("PRUNING_POLICY: ${pruningPolicy}")
    } else {
    println("PRUNING_THRESHOLD: ${pruningThreshold}")
    println("PRUNING_POLICY: ${pruningPolicy}")
    }
    
    try (def FFS = new ForwardFluxSampling<>(fullPathGen, parallelNum, surfaceA, surfaces, N0).setStep1Mul(step1Mul).setPruningProb(pruningProb).setPruningThreshold(pruningThreshold).setPruningStep(pruningStep).setPruningPolicy(pruningPolicy)) {
        if (pbar) FFS.setProgressBar()
        if (printPathEff) fullPathGen.initTimer()
        UT.Timer.tic()
        FFS.run()
        UT.Timer.toc("i = -1, k0 = ${FFS.getK0()}, step1PointNum = ${FFS.step1PointNum()}, step1PathNum = ${FFS.step1PathNum()},")
        if (printPathEff) {
            def info = fullPathGen.getTimerInfo()
            println("PathGenEff: lmp = ${percent(info.lmp/info.total)}, lambda = ${percent(info.lambda/info.total)}, wait = ${percent(info.wait/info.total)}, else = ${percent((info.other)/info.total)}")
        }
        Dump.fromAtomDataList(FFS.pickPath()).write(FFSDumpPath)
        if (dumpAllPath) {
            for (j in range(FFS.pointsOnLambda().size())) Dump.fromAtomDataList(FFS.pickPath(j)).write(FFSAllDumpPath + '/' + j)
            IO.dir2zip(FFSAllDumpPath, "${FFSAllDumpPath}-0.zip")
            IO.rmdir(FFSAllDumpPath)
        }
        if (FFS.stepFinished()) {
            Dump.fromAtomDataList(FFS.pointsOnLambda()).write(FFSRestartPathDu)
            IO.map2json(FFS.restData(), FFSRestartPathRe)
        }
        
        def i = 0
        while (!FFS.finished()) {
            if (printPathEff) fullPathGen.resetTimer()
            UT.Timer.tic()
            FFS.run()
            UT.Timer.toc("i = ${i}, prob = ${FFS.getProb(i)}, step2PointNum = ${FFS.step2PointNum(i)}, step2PathNum = ${FFS.step2PathNum(i)},")
            if (printPathEff) {
                def info = fullPathGen.getTimerInfo()
                println("PathGenEff: lmp = ${percent(info.lmp/info.total)}, lambda = ${percent(info.lambda/info.total)}, wait = ${percent(info.wait/info.total)}, else = ${percent(info.other/info.total)}")
            }
            Dump.fromAtomDataList(FFS.pickPath()).write(FFSDumpPath)
            if (dumpAllPath) {
                for (j in range(FFS.pointsOnLambda().size())) Dump.fromAtomDataList(FFS.pickPath(j)).write(FFSAllDumpPath + '/' + j)
                IO.dir2zip(FFSAllDumpPath, "${FFSAllDumpPath}-${i + 1}.zip")
                IO.rmdir(FFSAllDumpPath)
            }
            if (FFS.stepFinished()) {
                Dump.fromAtomDataList(FFS.pointsOnLambda()).write(FFSRestartPathDu)
                IO.map2json(FFS.restData(), FFSRestartPathRe)
            }
            ++i
        }
        println("=====FFS FINISHED=====")
        println("k = ${FFS.getK()}, totalPointNum = ${FFS.totalPointNum()}")
    }
}
MPI.Comm.WORLD.barrier()

calComm.shutdown()
subComm.shutdown()
MPI.shutdown()


static void runMelt(MPI.Comm comm, NativeLmp lmp, Lmpdat inData, String outDataPath, double temperature, int runStep, double timestep) {
    if (comm.rank() == 0) IO.validPath(outDataPath)
    if (pbar && me==0) UT.Timer.pbar('runMelt', MathEX.Code.divup(runStep, 5000))
    lmp.command('units           metal')
    lmp.command('boundary        p p p')
    lmp.command("timestep        $timestep")
    lmp.loadData(inData)
    lmp.command("pair_style      $pairStyle")
    lmp.command("pair_coeff      $pairCoeff")
    lmp.command("velocity        all create $temperature ${seedLmp(comm)} dist gaussian mom yes rot yes")
    lmp.command("fix             1 all npt temp $temperature $temperature 0.2 iso 0.0 0.0 2.0")
    if (pbar) {
        for (_ in range(runStep.intdiv(5000))) {
            lmp.command('run             5000')
            if (me==0) UT.Timer.pbar()
        }
        int rest = runStep % 5000
        if (rest > 0) {
            lmp.command("run             $rest")
            if (me==0) UT.Timer.pbar()
        }
    } else {
        lmp.command('thermo          5000')
        lmp.command("run             $runStep")
    }
    lmp.command("write_data      $outDataPath")
    lmp.clear()
}
static void runMelt(MPI.Comm comm, NativeLmp lmp, String inDataPath, String outDataPath, double temperature, int runStep, double timestep) {
    if (comm.rank() == 0) IO.validPath(outDataPath)
    if (pbar && me==0) UT.Timer.pbar('runMelt', MathEX.Code.divup(runStep, 5000))
    lmp.command('units           metal')
    lmp.command('boundary        p p p')
    lmp.command("timestep        $timestep")
    lmp.command("read_data       $inDataPath")
    lmp.command("pair_style      $pairStyle")
    lmp.command("pair_coeff      $pairCoeff")
    lmp.command("velocity        all create $temperature ${seedLmp(comm)} dist gaussian mom yes rot yes")
    lmp.command("fix             1 all npt temp $temperature $temperature 0.2 iso 0.0 0.0 2.0")
    if (pbar) {
        for (_ in range(runStep.intdiv(5000))) {
            lmp.command('run             5000')
            if (me==0) UT.Timer.pbar()
        }
        int rest = runStep % 5000
        if (rest > 0) {
            lmp.command("run             $rest")
            if (me==0) UT.Timer.pbar()
        }
    } else {
        lmp.command('thermo          5000') // debug usage
        lmp.command("run             $runStep")
    }
    lmp.command("write_data      $outDataPath")
    lmp.clear()
}

static int seedLmp(MPI.Comm comm) {
    return randSeed(comm, 0)
}
static long seed(MPI.Comm comm = MPI.Comm.WORLD) {
    return comm.bcastL(me==0 ? rng().nextLong() : -1, 0)
}

