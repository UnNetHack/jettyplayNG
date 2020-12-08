/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 *
 * @author ais523
 */
class TelnetURLConnection extends TCPURLConnection {

    public TelnetURLConnection(URL u) {
        super(u);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream i = super.getInputStream();
        OutputStream o = s.getOutputStream();
        return new TelnetFilterStream(i,o);
    }
}
