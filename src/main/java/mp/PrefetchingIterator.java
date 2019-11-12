package mp;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Abstract class for how you usually implement iterators when you don't know
 * how many objects there are (which is pretty much every time)
 * 
 * Basically the {@link #hasNext()} method will look up the next object and
 * cache it with {@link #setPrefetchedNext(Object)}. The cached object is
 * then set to null in {@link #next()}.
 */
public abstract class PrefetchingIterator<T> implements Iterator<T>
{
    private boolean hasFetchedNext;
	private T nextObject;
	
	public boolean hasNext()
	{
		if ( hasFetchedNext )
		{
		    return getPrefetchedNextOrNull() != null;
		}
		
		T nextOrNull = fetchNextOrNull();
        hasFetchedNext = true;
		if ( nextOrNull != null )
		{
			setPrefetchedNext( nextOrNull );
		}
		return nextOrNull != null;
	}

	public T next()
	{
		if ( !hasNext() )
		{
			throw new NoSuchElementException();
		}
		T result = getPrefetchedNextOrNull();
		setPrefetchedNext( null );
		hasFetchedNext = false;
		return result;
	}
	
	protected abstract T fetchNextOrNull();
	
	protected void setPrefetchedNext( T nextOrNull )
	{
		this.nextObject = nextOrNull;
	}
	
	protected T getPrefetchedNextOrNull()
	{
		return nextObject;
	}

	public void remove()
	{
		throw new UnsupportedOperationException();
	}
}
