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
class TermcastURLConnection extends TelnetURLConnection {
    private final String prefix;

    public TermcastURLConnection(URL u, String prefix) {
        super(u);
        this.prefix = prefix;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream i = super.getInputStream();
        OutputStream o = s.getOutputStream();
        return new TermcastFilterStream(i,o,prefix,url);
    }
}
