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
import jse.code.IO
import jse.code.OS
import jse.lmp.NativeLmp
import jse.parallel.MPI

def lmpPkgEnv = OS.env('JSE_LMP_PKG')
if (lmpPkgEnv==null || !IO.Text.splitStr(lmpPkgEnv).contains('MANYBODY')) {
    throw new IllegalStateException('''
Illegal environment variable setting, you need to set:
---------------------------------
export JSE_LMP_PKG=MANYBODY
---------------------------------
to ensure that LAMMPS can run EAM potential
''')
}

MPI.InitHelper.init()
NativeLmp.InitHelper.init()

