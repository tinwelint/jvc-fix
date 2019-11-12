#!/usr/bin/env python
import sys, os
path = sys.argv[ 1 ]
for aFile in os.listdir( path ):
	if aFile.endswith( ".MOD" ):
		niceNumber = str( int( aFile[ 3:6 ], 16 ) ).zfill( 4 )
		os.rename( os.path.join( path, aFile ), os.path.join( path, "mov" + niceNumber + ".mpg" ) )
