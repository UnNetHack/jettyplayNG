/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketPermission;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;

/**
 *
 * @author ais523
 */
class TCPURLConnection extends URLConnection {

    protected Socket s;
    private int readTimeout;

    public TCPURLConnection(URL u) {
        super(u);
        readTimeout = 0;
    }

    @Override
    public void connect() throws IOException {
        try {
            int p = url.getPort();
            if (p == -1) p = url.getDefaultPort();
            s = new Socket(url.getHost(), p);
        } catch(IllegalArgumentException x) {
            // It could be the user who gave the illegal argument, so convert
            // this to an IOException so the user sees it.
            throw new IOException(x.getMessage());
        }
        connected = true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if(!connected) connect();
        return s.getInputStream();
    }

    @Override
    public int getReadTimeout() {
        return readTimeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        if (timeout < 0) throw new IllegalArgumentException();
        if (s != null) try {
            s.setSoTimeout(timeout);
        } catch (SocketException ex) {
            return;
        }
        readTimeout = timeout;
    }

    @Override
    public Permission getPermission() throws IOException {
        return new SocketPermission(url.getHost()+":"+url.getPort(),"connect");
    }
}
