package searchengine.exceptions;

public class IncorrectUrlForIndexing extends RuntimeException
{
    public IncorrectUrlForIndexing(String message)
    {
        super(message);
    }
}
