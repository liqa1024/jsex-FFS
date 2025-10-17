import jse.code.UT
import jse.code.random.IRandom
import jsex.rareevent.BufferedFullPathGenerator
import jsex.rareevent.ForwardFluxSampling
import jsex.rareevent.IParameterCalculator
import jsex.rareevent.IPathGenerator

import static jse.code.UT.Math.zeros
import static jse.code.UT.Plot.semilogy
import static jse.code.UT.Plot.xlabel
import static jse.code.UT.Plot.ylabel


// path gen and para cal define
class RandomWalk {
    static class Point {
        final int value, time
        Point(int value, int time) {
            this.value = value
            this.time = time
        }
        @Override String toString() {
            return value
        }
    }
    static class PathGenerator implements IPathGenerator<Point> {
        private final int pathLen
        PathGenerator(int pathLen) {
            this.pathLen = pathLen
        }
        @Override Point initPoint(IRandom rng) {
            return new Point(0, 0)
        }
        @Override List<Point> pathFrom(Point point, IRandom rng) {
            def path = new ArrayList<Point>(pathLen)
            path.add(point)
            for (i in 1..<pathLen) {
                point = new Point(point.value + (rng.nextBoolean() ? 1 : -1), point.time+1)
                path.add(point)
            }
            return path
        }
        @Override double timeOf(Point point) {
            return point.time
        }
    }
    static class ParameterCalculator implements IParameterCalculator<Point> {
        @Override double lambdaOf(Point point) {
            return Math.abs(point.value)
        }
    }
}

// FFS
def biPathGen = new RandomWalk.PathGenerator(10)
def biCal = new RandomWalk.ParameterCalculator()
def fullPath = new BufferedFullPathGenerator(biPathGen, biCal)

int N0 = 10000
def lambda = 1..10
def k = zeros(lambda.size())

def FFS = new ForwardFluxSampling<>(fullPath, 0, lambda, N0)
UT.Timer.tic()
FFS.run()
k[0] = FFS.getK0()
int i = 0
while (!FFS.finished()) {
    FFS.run()
    k[i+1] = FFS.getProb(i)
    ++i
}
UT.Timer.toc("k = ${FFS.getK()}, step1PointNum = ${FFS.step1PointNum()}, step1PathNum = ${FFS.step1PathNum()}, totPointNum = ${FFS.totalPointNum()},")
FFS.shutdown()

// plot
k = k.op().cumprod()
semilogy(lambda, k, null).marker('o').fill().markerFaceColor('w')
xlabel('lambda')
ylabel('rate')

