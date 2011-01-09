package jettyplay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A stream that wraps another input stream, doing bzip2 decompression
 * on the wrapped stream in the process.
 * @author ais523
 */
public class BZip2InputStream extends InputStream {
    private final InputStream i;
    private ByteArrayOutputStream o;
    private InputStream m;

    /**
     * Creates a new input stream that decompresses another given input stream.
     * @param i The input stream to decompress.
     */
    public BZip2InputStream(InputStream i) {
        this.i = i;
    }

    // BZip2 inherently needs the whole file to be present to work correctly,
    // so just do all the decode in one go.
    private void doDecode() throws IOException {
        if (o == null) {
            int r;
            o = new ByteArrayOutputStream();
            try {
                r = MicroBunzip.uncompressStream(i, o);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Decode interrupted");
            }
            if (r != 0) {
                throw new IOException(MicroBunzip.bunzip_errors[r]);
            }
            m = new ByteArrayInputStream(o.toByteArray());
        }
    }
    /**
     * Discards the next n bytes of input.
     * @param n The number of bytes to discard.
     * @return The number of bytes actually discarded, which might be less than
     * the number of bytes requested to discard (say, if EOF is reached).
     * @throws IOException if an I/O exception occurs reading the wrapped input
     * stream, or the wrapped input stream is in the wrong format.
     */
    @Override
    public long skip(long n) throws IOException {
        doDecode();
        return m.skip(n);
    }
    /**
     * Reads decompressed data into a byte array. This blocks until data is
     * available.
     * @param b The array to read data into.
     * @param off Which element of the array should receive the first
     * decompressed byte read.
     * @param len The number of bytes to read.
     * @return The number of bytes actually read, or -1 at EOF.
     * @throws IOException if an I/O exception occurs reading the wrapped input
     * stream, or the wrapped input stream is in the wrong format.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        doDecode();
        return m.read(b, off, len);
    }
    /**
     * Reads decompressed data into a byte array. This blocks until data is
     * available.
     * @param b The array to read data into; the entire array will be filled
     * with data, if possible.
     * @return The number of bytes actually read, or -1 at EOF.
     * @throws IOException if an I/O exception occurs reading the wrapped input
     * stream, or the wrapped input stream is in the wrong format.
     */
    @Override
    public int read(byte[] b) throws IOException {
        doDecode();
        return m.read(b);
    }
    /**
     * Returns the next decompressed byte available.
     * @return The next byte of data, or -1 at EOF.
     * @throws IOException if an I/O exception occurs reading the wrapped input
     * stream, or the wrapped input stream is in the wrong format.
     */
    @Override
    public int read() throws IOException {
        doDecode();
        return m.read();
    }
    /**
     * Returns the number of bytes that can be read from the input without
     * blocking.
     * @return The number of bytes available. This might be 0 if at EOF, or if
     * more input data is needed to be able to produce any more decompressed
     * data.
     * @throws IOException if an I/O exception occurs reading the wrapped input
     * stream, or the wrapped input stream is in the wrong format.
     */
    @Override
    public int available() throws IOException {
        doDecode();
        return m.available();
    }
    /**
     * Closes the input stream and releases any system resources associated
     * with it.
     * @throws IOException if an I/O exception occurs closing the wrapped input
     * stream.
     */
    @Override
    public void close() throws IOException {
        i.close();
    }
}
