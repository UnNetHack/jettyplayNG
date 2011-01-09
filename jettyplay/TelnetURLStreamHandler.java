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
 * An URL stream handler for Telnet.
 * @author ais523
 */
class TelnetURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new TelnetURLConnection(u);
    }

    @Override
    protected int getDefaultPort() {
        return 23;
    }
}
