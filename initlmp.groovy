import jse.lmp.NativeLmp
import jse.parallel.MPI

NativeLmp.Conf.CMAKE_SETTING['PKG_MANYBODY'] = 'YES'
NativeLmp.Conf.CMAKE_SETTING['PKG_PLUGIN'] = 'YES'
NativeLmp.Conf.REBUILD = true

MPI.InitHelper.init()
NativeLmp.InitHelper.init()

