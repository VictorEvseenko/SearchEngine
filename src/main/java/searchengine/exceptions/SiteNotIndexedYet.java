package searchengine.exceptions;

public class SiteNotIndexedYet extends RuntimeException
{
    public SiteNotIndexedYet(String message)
    {
        super(message);
    }
}
