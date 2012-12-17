/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * A factory that creates the URL stream handlers used by Jettyplay.
 * @author ais523
 */
public class StreamingURLStreamHandlerFactory implements URLStreamHandlerFactory {

    /**
     * Returns the custom URL stream handler used by Jettyplay for the given
     * protocol, if any.
     * @param protocol The usual abbreviation (e.g. "file" or "tcp") for the
     * protocol to return a stream handler for.
     * @return A stream handler for that protocol, or null if the protocol is
     * not handled by Jettyplay itself (the protocol might still be handled by
     * the Java runtime).
     */
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (protocol.equals("tcp")) return new TCPURLStreamHandler();
        if (protocol.equals("telnet")) return new TelnetURLStreamHandler();
        if (protocol.equals("termcast")) return new TermcastStreamHandler("");
        if (protocol.equals("dgamelaunch")) return new TermcastStreamHandler("w");
        return null; // use default
    }
}
