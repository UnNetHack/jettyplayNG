package jettyplay;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
//import javax.jnlp.FileContents;

/**
 * A class that fits a javax.jnlp.FileContents object into the InputStreamable
 * interface, doing everything with reflection in case javax.jnlp isn't
 * available.
 * @author ais523
 */
public class InputStreamableFileContentsWrapper implements InputStreamable {
    private final Object fc;

    private Class<?> getFileContentsClass() throws ClassNotFoundException {
        return getClass().getClassLoader().loadClass("javax.jnlp.FileContents");
    }

    /**
     * Creates an InputStreamable that works the same way as a given
     * FileContents object.
     * @param fc The FileContents object to wrap. This must be a FileContents
     * despite being declared as an Object (and it's not verified that it is);
     * the type is given as Object for compatibility with Java environments in
     * which FileContents does not exist.
     */
    public InputStreamableFileContentsWrapper(Object fc) {
        this.fc = fc;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        InputStream i = null;
        try { 
            i = (InputStream) getFileContentsClass().getMethod("getInputStream").
                invoke(fc);
        } catch (Exception ex) {
        }
        if (i == null) throw new FileNotFoundException();
        return i;
    }

    public URI getURI() throws URISyntaxException {
        URI u = null;
        try {
            String n = (String) getFileContentsClass().getMethod("getName").
                invoke(fc);
            u = new File(n).toURI();
        } catch (Exception ex) {
        }
        if (u == null) throw new URISyntaxException("","Filename not available");
        return u;
    }

    public boolean isReadable() {
        try {
            Boolean b = (Boolean) getFileContentsClass().getMethod("canRead").
                invoke(fc);
            return b;
        } catch (Exception ex) {
            return false;
        }
    }

    public long getLength() {
        try {
            Long l = (Long) getFileContentsClass().getMethod("getLength").
                invoke(fc);
            return l;
        } catch (Exception ex) {
            return 0;
        }
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
            getInputStream().close();
        } catch (Exception ex) {
            // closed already or never started, nothing to do here...
        }
    }
}
