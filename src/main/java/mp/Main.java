package mp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

public class Main
{
    private static final byte[] TO_LOOK_FOR = new byte[] {
        0, 0, 1
    };
    
    public static void main( String[] args ) throws Exception
    {
        if ( args.length < 2 )
        {
            System.out.println(
                "Supply file name and ratio ('4:3' or '16:9')" );
            return;
        }
        
        for ( File file : getFiles( args[ 0 ] ) )
        {
            process( file, args[ 1 ] );
        }
    }
    
    private static File[] getFiles( String fileTemplate )
    {
        File file = new File( fileTemplate );
        if ( file.exists() )
        {
            if ( file.isFile() )
            {
                return new File[] { file };
            }
            else
            {
                Collection<File> files = new ArrayList<File>();
                gatherFiles( files, file );
                return files.toArray( new File[ 0 ] );
            }
        }
        else
        {
            final Pattern pattern = Pattern.compile( file.getName() );
            File parent = file.getParentFile();
            if ( parent.exists() )
            {
                return parent.listFiles( new FileFilter()
                {
                    @Override
                    public boolean accept( File pathname )
                    {
                        return pathname.isFile() &&
                            pattern.matcher( pathname.getName() ).matches();
                    }
                } );
            }
        }
        
        return new File[ 0 ];
    }

    private static void gatherFiles( Collection<File> files, File file )
    {
        if ( file.isDirectory() )
        {
            for ( File child : file.listFiles() )
            {
                gatherFiles( files, child );
            }
        }
        else
        {
            files.add( file );
        }
    }

    private static void process( File file, String ratio ) throws Exception
    {
        System.out.print( "Processing " + file.getAbsolutePath() + " ...  " );
        InputStream stream = new BufferedInputStream(
            new FileInputStream( file ) );
        Collection<Long> locations = null;
        try
        {
            locations = getLocations( stream );
        }
        finally
        {
            stream.close();
        }
        
        RandomAccessFile rwFile = new RandomAccessFile( file, "rw" );
        FileChannel channel = rwFile.getChannel();
        boolean modified = false;
        try
        {
            for ( Long location : locations )
            {
                if ( fixHeader( channel, location, ratio ) )
                {
                    modified = true;
                }
            }
            System.out.println( modified ? "MODIFIED" : "" );
        }
        finally
        {
            channel.close();
            rwFile.close();
        }
    }

    private static Collection<Long> getLocations( InputStream stream )
        throws Exception
    {
        HeaderIterator itr = new HeaderIterator( stream );
        Collection<Long> result = new ArrayList<Long>();
        while ( itr.hasNext() )
        {
            int b = itr.next();
            if ( b == 0xB3 )
            {
                result.add( itr.position );
            }
        }
        return result;
    }
    
    private static boolean fixHeader( FileChannel channel, long pos,
        String ratio ) throws Exception
    {
        channel.position( pos );
        ByteBuffer buffer = ByteBuffer.allocate( 7 );
        channel.read( buffer );
        buffer.position( 0 );
        byte[] bytes = buffer.array();
        
        int width = ( bytes[ 0 ] << 4 ) + ( ( bytes[ 1 ]&0xF0 ) >> 4 );
        int height = ( ( bytes[ 1 ]&0x0F ) << 8 ) + ( bytes[ 2 ] );
        int aspect = ( bytes[ 3 ]&0xF0 ) >> 4;
        if ( width != 720 || height != 576 )
        {
            return false;
        }
        
        
        // Change the size/aspect ratio
//        width = 1024;
//        height = 576;
//        bytes[ 0 ] = ( byte ) ( ( width&0xFF0 ) >> 4 );
//        bytes[ 1 ] = ( byte ) ( ( ( width&0x0F ) << 4 ) +
//            ( ( height&0xF00 ) >> 8 ) );
//        bytes[ 2 ] = ( byte ) ( height&0xFF );
        
        int newAspect = 0;
        if ( ratio.equals( "1:1" ) )
        {
            newAspect = 1;
        }
        else if ( ratio.equals( "4:3" ) )
        {
            newAspect = 2;
        }
        else if ( ratio.equals( "16:9" ) )
        {
            newAspect = 3;
        }
        else
        {
            throw new RuntimeException( "Unknown ratio " + ratio );
        }
        
        if ( newAspect == aspect )
        {
            return false;
        }
        
        bytes[ 3 ] = ( byte ) ( ( bytes[ 3 ]&0x0F ) + ( newAspect << 4 ) );
        
        channel.position( pos );
        channel.write( ByteBuffer.wrap( bytes ) );
        return true;
    }

    private static class HeaderIterator extends PrefetchingIterator<Integer>
    {
        private final InputStream stream;
        private long position;
        
        HeaderIterator( InputStream stream )
        {
            this.stream = stream;
        }
        
        private int readByte() throws IOException
        {
            int result = this.stream.read();
            position++;
            return result;
        }
        
        @Override
        protected Integer fetchNextOrNull()
        {
            try
            {
                int read = -1;
                while ( ( read = readByte() ) != -1 )
                {
                    if ( read == TO_LOOK_FOR[ 0 ] )
                    {
                        long markedPos = position;
                        stream.mark( 1000 );
                        
                        Integer id = tryGetId();
                        if ( id != null )
                        {
                            return id;
                        }
                        else
                        {
                            position = markedPos;
                            stream.reset();
                        }
                    }
                }
                return null;
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }

        private Integer tryGetId() throws IOException
        {
            // First byte matches... let's see if the rest does
            int read = -1;
            int i = 1;
            while ( i < TO_LOOK_FOR.length && ( read = readByte() ) != -1 )
            {
                if ( read != TO_LOOK_FOR[ i ] )
                {
                    return null;
                }
                i++;
            }
            
            // Match, get the next byte
            return readByte();
        }
    }
}
