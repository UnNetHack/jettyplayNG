/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.regex.Pattern;

/**
 *
 * @author ais523
 */
class TermcastFilterStream extends TelnetFilterStream {

    private final URL url;
    private final OutputStream stream;
    private Date lastActivity;

    private enum TermcastState {
        LOADING,
        MENU,
        WATCHING,
    }
    private TermcastState state = TermcastState.LOADING;
    private final vt320 checkTerminal;

    public TermcastFilterStream(InputStream i, OutputStream o,
            String prefix, URL url)
            throws IOException {
        super(i,o);
        for (char c: prefix.toCharArray())
            o.write(c);
        this.url = url;
        stream = o;
        checkTerminal = new vt320(80, 24);
    }

    @Override
    public int read() throws IOException {
        int i = super.read();
        //while (state != TermcastState.WATCHING) {
            if (i == -1) return -1;
            signalRead(i);
            i = super.read();
        //}
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        int r;
        if (state != TermcastState.WATCHING) {
            int i = read();
            if (i == -1) return 0;
            b[off] = (byte)i;
            r = super.read(b, off+1, len-1) + 1;
        } else r = super.read(b, off, len);
        for (int i = off; i < off+r; i++) {
            signalRead(b[i]);
        }
        return r;
    }

    private void signalRead(int i) throws IOException {
        if (state == TermcastState.WATCHING) return;
        i = (i + 256) % 256;
        lastActivity = new Date();
        if (i != 0) checkTerminal.putString(String.valueOf((char)i));
        String path = "";
        try {
            path = url.toURI().normalize().getPath();
        } catch (URISyntaxException ex) {
            throw new IOException(url + " is not a valid URI", ex);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        path = path.toLowerCase();
        Pattern p = Pattern.compile("are in progress");
        if (!checkTerminal.containsPattern(p)) return;
        if (state == TermcastState.LOADING) {
            System.err.println("Menu found!");
            state = TermcastState.MENU;
            final String finalPath = path;
            final TermcastFilterStream finalThis = this;
            // Start scanning for the end of the menu.
            new Thread() {
                @Override
                public void run() {
                    while(true) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            return;
                        }
                        if (new Date().getTime() -
                                lastActivity.getTime() > 1000) {
                            // Look for an instance of the player we want.
                            // If there is one, press the matching button
                            // and break out of this loop; if there isn't,
                            // press >.
                            for(char c = 'a'; c < 'z'; c++) {
                                Pattern p = Pattern.compile(
                                        c + "\\) " + finalPath,
                                        Pattern.CASE_INSENSITIVE);
                                if (checkTerminal.containsPattern(p)) {
                                    try {
                                        System.err.println("Player found: "+c);
                                        state = TermcastState.WATCHING;
                                        if (checkTerminal.containsPattern(
                                                Pattern.compile(
                                                "\\(use uppercase to try " +
                                                "to change size\\)")))
                                            finalThis.stream.write(
                                                    Character.toUpperCase(c));
                                        else {
                                            finalThis.stream.write(c);
                                            finalThis.stream.write('r');
                                        }
                                        return;
                                    } catch (IOException ex) {
                                        return;
                                    }
                                }
                            }
                            try {
                                finalThis.stream.write('>');
                            } catch (IOException ex) {
                                return;
                            }
                        }
                    }
                }
            }.start();
        }
    }
}
