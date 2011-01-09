package jettyplay;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * An interface representing things that can provide InputStreams, such
 * as filenames, sockets, etc.
 * @author ais523
 */
public interface InputStreamable {
    /**
     * Gets the InputStream associated with this InputStreamable thing.
     * @return The InputStream.
     * @throws FileNotFoundException If the InputStream cannot be created,
     * say because this InputStreamable represents a resource that does
     * not exist.
     */
    public InputStream getInputStream() throws FileNotFoundException;
    /**
     * Returns a URI that allows this InputStreamable to be reconstructed.
     * This should generally be the URI of a file or socket it represents.
     * @return The URI.
     * @throws URISyntaxException If a valid URI cannot be constructed.
     */
    public URI getURI() throws URISyntaxException;
    /**
     * Returns true if it is possible to read from the resource that this
     * InputStreamable represents.
     * @return Whether the resource is readable.
     */
    public boolean isReadable();
    /**
     * Returns true if it is impossible that any more data will ever
     * appear in this InputStream after it returns EOF. (This is not
     * generally the case for files, which can be edited after they have
     * been opened, but is generally the case for sockets.)
     * @return Whether an EOF on this InputStream is permanent.
     */
    public boolean isEOFPermanent();
    /**
     * Returns an estimate of the number of bytes that exist in this
     * stream altogether. This can be an underestimate, but should
     * never be an overestimate; and should only be equal to the number
     * of bytes read so far if there is no more data in the stream, if
     * implemented at all. If the value is unavailable, 0 should be
     * returned.
     * @return The number of bytes in this stream at the moment.
     */
    public long getLength();
    /**
     * Returns true if this resource is of a sort that could plausibly
     * stream in real-time, and which never becomes EOF and then
     * readable again unless it's being used for streaming.
     * @return Whether the resource is streamable.
     */
    public boolean couldBeStreamable();
    /**
     * Returns true if this resource is of a sort that is only
     * ever used for streaming.
     * @return Whether the resource is always streamable.
     */
    public boolean mustBeStreamable();

    /**
     * Tries to destroy the InputStreamable by getting rid of any resources
     * that might associated with its InputStream, forcing anything trying to
     * read from it to error out.
     */
    public void cancelIO();
}
