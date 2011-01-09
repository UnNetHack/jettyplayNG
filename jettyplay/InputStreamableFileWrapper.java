package jettyplay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * A wrapper to fit Files into the InputStreamable interface.
 * @author ais523
 */
public class InputStreamableFileWrapper implements InputStreamable {
    private final File file;
    private InputStream stream;

    /**
     * Creates a new InputStreamable from a given File.
     * @param file The filename of the file to open.
     */
    public InputStreamableFileWrapper(File file) {
        this.file = file;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        stream = new FileInputStream(file);
        return stream;
    }

    public URI getURI() {
        return file.getAbsoluteFile().toURI();
    }

    public boolean isReadable() {
        return file.canRead();
    }

    public long getLength() {
        return file.length();
    }

    public boolean isEOFPermanent() {
        return false;
    }

    public boolean couldBeStreamable() {
        return true;
    }

    public boolean mustBeStreamable() {
        return false;
    }

    public void cancelIO() {
        try {
            stream.close();
        } catch (IOException ex) {
            // do nothing, it must have been closed anyway
        }
    }
}
