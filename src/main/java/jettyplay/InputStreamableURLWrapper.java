/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A wrapper to fit an URL into the InputStreamable interface.
 * @author ais523
 */
public class InputStreamableURLWrapper implements InputStreamable {

    private final URL url;
    private InputStream stream;
    /**
     * Creates a new InputStreamable from a given URL.
     * <p>
     * This currently correctly supports the protocols file, http, ftp, jar,
     * tcp, telnet, termcast, dgamelaunch; other protocols might work, but it
     * will assume default values for some behaviours of the protocols, which
     * may or may not be correct.
     * @param url The URL to connect to to obtain the input stream.
     */
    public InputStreamableURLWrapper(URL url) {
        this.url = url;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        try {
            URLConnection u = url.openConnection();
            u.setReadTimeout(0); // milliseconds
            stream = u.getInputStream();
            return stream;
        } catch (IOException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }
    public URI getURI() throws URISyntaxException {
        return url.toURI();
    }
    public boolean isReadable() {
        return true;
    }
    public boolean isEOFPermanent() {
        String p = url.getProtocol();
        if (p.equals("tcp")) return true;
        if (p.equals("ftp")) return true;
        return false;
    }
    public long getLength() {
        return 0;
    }
    public boolean couldBeStreamable() {
        if (mustBeStreamable()) return true;
        String p = url.getProtocol();
        if (p.equals("file")) return true;
        return false;
    }
    public boolean mustBeStreamable() {
        String p = url.getProtocol();
        if (p.equals("tcp")) return true;
        if (p.equals("telnet")) return true;
        if (p.equals("termcast")) return true;
        if (p.equals("dgamelaunch")) return true;
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
