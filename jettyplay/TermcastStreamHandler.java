/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * A stream handler for viewing termcasting; like viewing a telnet film,
 * except it actually sends a bit of data to select which frame to view.
 * @author ais523
 */
class TermcastStreamHandler extends URLStreamHandler {

    private final String prefix;

    /**
     * Creates a new termcast stream handler.
     * @param prefix The sequence of characters which must be sent in order to
     * bring up the menu of who to watch.
     */
    public TermcastStreamHandler(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new TermcastURLConnection(u, prefix);
    }

    @Override
    protected int getDefaultPort() {
        return 23;
    }
}
