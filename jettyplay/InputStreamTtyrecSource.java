/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Date;

/**
 *
 * @author ais523
 */
class InputStreamTtyrecSource extends TtyrecSource {
    private final InputStreamable iStream;
    private boolean lengthKnown;

    public InputStreamTtyrecSource(InputStreamable iStream) {
        this.iStream = iStream;
        lengthKnown = false;
    }

    @Override
    public void run() {
        try {
            boolean reachedEOF = false;
            ByteChunkList bytestream = getBytestream();
            if (!iStream.isReadable()) throw new IOException("Unreadable file");
            InputStream i = iStream.getInputStream();
            if (iStream.mustBeStreamable()) getTtyrec().setIsStreaming(true);
            int bytesRead = 0;
            for(;;) {
                int l = i.available();
                if (l == 0) l = (int) (iStream.getLength() - bytesRead);
                if (l < 0) l = 10000; // a sensible chunk size
                byte[] b = new byte[l];
                int obr = bytesRead;
                try {
                    if (b.length > 0) bytesRead += i.read(b);
                } catch(SocketTimeoutException s) {
                    bytesRead = -1;
                }
                if (interrupted()) throw new InterruptedException();
                if (obr > bytesRead || b.length == 0) {
                    if (iStream.isEOFPermanent()) break;
                    reachedEOF = true;
                    bytesRead = obr;
                    setReadEventHappened(true);
                    Thread.sleep(100);
                    continue;
                }
                setReadEventHappened(true);
                getTtyrec().setLastActivity(new Date());
                getTtyrec().setLengthOffset(0);
                // Simple heuristic: If we reached EOF in the past, and are
                // not there now, we must be looking at a growing file. This
                // doesn't apply to things like HTTP where EOF means that we're
                // waiting for data to download from the server. (In other
                // words, "slow-loading" HTTP streaming isn't interpreted as
                // streaming. TODO: Should it be?)
                if (reachedEOF && iStream.couldBeStreamable())
                    getTtyrec().setIsStreaming(true);
                b = Arrays.copyOf(b, bytesRead-obr);
                bytestream.appendArray(b, bytesRead-obr);
                synchronized(bytestream) {
                    bytestream.notifyAll();
                }
            }
        } catch (IOException ex) {
            // TODO: Show in the GUI
            System.out.println("Input failed: "+ex.getMessage());
        } catch (InterruptedException ex) {
            System.out.println("Source ending via interruption...");
            return;
        }
        lengthKnown = true;
        System.out.println("Source ending...");
    }

    @Override
    public boolean knownLength() {
        return lengthKnown;
    }

    @Override
    public URI getURI() throws URISyntaxException {
        return iStream.getURI();
    }

    @Override
    protected void cancelIO() {
        try {
            iStream.cancelIO();
        } catch (Exception ex) {
            // If this goes wrong, it's probably shutting down anyway.
        }
    }
}
