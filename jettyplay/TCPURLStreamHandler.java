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
 *
 * @author ais523
 */
class TCPURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new TCPURLConnection(u);
    }
}
