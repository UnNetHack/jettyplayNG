/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Date;
import java.util.zip.GZIPInputStream;

/**
 * A TtyrecWorker that splits a ttyrec into frames. If the input doesn't
 * seem to be in ttyrec format, it replaces itself with workers that try
 * other formats.
 * @author ais523
 */
public class TtyrecAnalyzer extends TtyrecWorker {

    public enum InputFormat { BZIP2, GZIP, TTYREC, SCRIPT };
    private final InputFormat format;
    private long byteloc;
    private InputStream outerInputStream;
    private InputStream innerInputStream;

    private final boolean formatDebug = false;

    TtyrecAnalyzer(TtyrecSource source, int seq, InputFormat format) {
        super(source, seq, "Ttyrec Analyzer");
        this.format = format;
        byteloc = 0;
        if (source.debug)
            System.out.println("Analyzer created! (workingFor="+
                workingFor+workingFor.hashCode()+
                ", this="+this+this.hashCode()+")");
    }

    private void buildInnerInputStream() {
        final ByteChunkList bytestream = workingFor.getBytestream();
        innerInputStream = new InputStream() {

            private int loc = 0;

            @Override
            public int read() throws IOException {
                while (loc > bytestream.size() - 1 &&
                        !workingFor.knownLength()) {
                    try {
                        bytestream.wait();
                    } catch (InterruptedException ex) {
                        throw new IOException("Interrupted");
                    }
                }
                int i;
                try {
                    i = bytestream.get(loc++);
                } catch (IndexOutOfBoundsException ex) {
                    return -1;
                }
                if (i < 0) {
                    i += 256;
                }
                return i;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                while (loc > bytestream.size() - 1 &&
                        !workingFor.knownLength()) {
                    try {
                        bytestream.wait();
                    } catch (InterruptedException ex) {
                        throw new IOException("Interrupted");
                    }
                }
                int i;
                try {
                    i = bytestream.getRestOfChunk(loc, b, off, len);
                    loc += i;
                } catch (IndexOutOfBoundsException ex) {
                    return -1;
                }
                return i;                
            }

            @Override
            public int available() throws IOException {
                return (bytestream.size() - loc);
            }
        };
    }

    // Notes: calls to this method must be synchronized on bytestream.
    // InterruptedException's thrown if interrupted, TtyrecException
    // if we're trying to read from a compressed file and it's in the wrong
    // format, NullPointerException at EOF.
    private byte getNextByte()
            throws InterruptedException, NullPointerException, TtyrecException {
        final ByteChunkList bytestream = workingFor.getBytestream();
        if (format != InputFormat.GZIP && format != InputFormat.BZIP2) {
            while (byteloc > bytestream.size() - 1 && !workingFor.knownLength()) {
                bytestream.wait();
            }
            return bytestream.get((int) byteloc++);
        } else {
            try {
                if (outerInputStream == null) {
                    buildInnerInputStream();
                    if (format == InputFormat.GZIP) {
                        outerInputStream = new GZIPInputStream(innerInputStream);
                    } else {
                        outerInputStream = new BZip2InputStream(innerInputStream);
                    }
                }
                int i = outerInputStream.read();
                if (i == -1) throw new NullPointerException();
                byteloc++;
                return (byte) i;
            } catch (IOException e) {
                if (formatDebug) System.out.println(e.getMessage());
                if (e.getMessage().equals("Interrupted")) {
                    throw new InterruptedException();
                }
                throw new TtyrecException("Failed to read compressed stream");
            }
        }
    }
    
    /**
     * The function that actually does the analysis. This uses the settings
     * already available in the ttyrec object.
     */
    @Override
    public void run() {
        boolean validHeaderFound = false;
        try {
            // Try to deduce length, if we can.
            long bytesTotal = Long.MAX_VALUE;
            ByteChunkList bytestream = workingFor.getBytestream();

            final Ttyrec rec = workingFor.getTtyrec();
            if (workingFor.knownLength() && format == InputFormat.TTYREC) {
                bytesTotal = workingFor.getBytestream().size();
                if (bytesTotal == 0)
                    throw new TtyrecException("File has zero length");
                if (bytesTotal > Integer.MAX_VALUE)
                    throw new TtyrecException("File is too large");
            }

            // Reset some of the values for the ttyrec. Global values in
            // the ttyrec (e.g. encoding possibility) must always be based
            // on the backport thread, so either we're overtaken already,
            // or we can set this safely.
            rec.resetEncodings();

            int[] ibunsigned = new int[12];

            /* Where we are in the file. */
            double lastTimestamp = 0;
            boolean timestampsFudged = false;
            double initialTimestamp = 0;
            boolean couldBeUnicode = true;
            byte[][] choppedOff = new byte[TtyrecFrame.MAX_STREAM_COUNT][];
            boolean firstframe = true;
            TtyrecFrame previousFrame = null;
            byte[] frameBuffer = null;
            int framesAnalyzed = 0;
            try {
                while (continueMainLoop() && byteloc < bytesTotal) {
                    setProgress(byteloc);
                    long length = -1;
                    int stream = -1;
                    double timestamp = -1.0;
                    byte[] frameData;
                    if (format != InputFormat.SCRIPT) {
                        /* The header information is three 4-byte fields:
                         * timestamp in seconds;
                         * milliseconds portion of timestamp;
                         * field length.
                         * Each is stored lsb first. */
                        synchronized (bytestream) {
                            for (int i = 0; i < 12; i++) {
                                try {
                                    ibunsigned[i] = getNextByte();
                                } catch (NullPointerException ex) {
                                    if (i == 0) {
                                        // End of the file, and it's somewhere we
                                        // were expecting; loop until something
                                        // more happens.
                                        synchronized (bytestream) {
                                            bytestream.wait(1000);
                                            i--;
                                            continue;
                                        }
                                    } else {
                                        throw ex;
                                    }
                                }
                                if (ibunsigned[i] < 0) {
                                    ibunsigned[i] += 256;
                                }
                            }
                        }
                        validHeaderFound = true;
                        long time_s = (long) ibunsigned[0]
                                + (long) ibunsigned[1] * (1 << 8)
                                + (long) ibunsigned[2] * (1 << 16)
                                + (long) ibunsigned[3] * (1 << 24);
                        long time_us = (long) ibunsigned[4]
                                + (long) ibunsigned[5] * (1 << 8)
                                + (long) ibunsigned[6] * (1 << 16)
                                + (long) ibunsigned[7] * (1 << 24);
                        length = (long) ibunsigned[8]
                                + (long) ibunsigned[9] * (1 << 8)
                                + (long) ibunsigned[10] * (1 << 16)
                                + (long) ibunsigned[11] * (1 << 24);
                        /* A rather crude check for ttyrec format. It's hard to
                         * do much better than this, though, because of the
                         * simplicity of the format. */
                        if (time_s < 0) {
                            throw new TtyrecException("Negative time field");
                        }
                        if (time_us < 0) {
                            throw new TtyrecException("Negative microseconds field");
                        }
                        if (time_us >= 1000000) {
                            throw new TtyrecException("Microseconds field too large");
                        }
                        if (length < 0) {
                            throw new TtyrecException("Negative-length frame");
                        }
                        if (byteloc + length > bytesTotal - 12
                                && byteloc + length != bytesTotal
                                && (rec.getFileType() != Ttyrec.FileType.MultistreamTtyrec
                                || byteloc + length != bytesTotal - 1)) {
                            throw new TtyrecException("Unexpected EOF");
                        }

                        // Calculate the timestamp.
                        timestamp = time_s + time_us / (double) 1000000;
                        // Process the stream, if this is multiframe.
                        stream = 0;
                        if (rec.getFileType() == Ttyrec.FileType.MultistreamTtyrec) {
                            synchronized (bytestream) {
                                stream = (int) getNextByte();
                            }
                            if (stream < 0 || stream >= TtyrecFrame.MAX_STREAM_COUNT) {
                                throw new TtyrecException("Invalid stream");
                            }
                        }
                        // Store data about the frame.
                        setProgress(byteloc);
                        frameData = new byte[(int) length];
                        synchronized (bytestream) {
                            for (int i = 0; i < length; i++) {
                                frameData[i] = getNextByte();
                            }
                        }
                    } else {
                        // Input format /is/ SCRIPT. Extract values from the
                        // metadata in the ByteChunkList.

                        // Get more data, if necessary.
                        synchronized(bytestream) {
                            // A check for knownLength is omitted here; may as
                            // well just go into an infinite loop if the input
                            while (byteloc > bytestream.size() - 1 &&
                                    !workingFor.knownLength())
                                bytestream.wait();
                            // Are we at a definite EOF?
                            if (byteloc >= bytestream.size()) break;
                        }
                        // The 10000 is arbitrary; it breaks frames up every
                        // 10000 bytes or every packet of input data, whichever
                        // is shorter.
                        if (frameBuffer == null)
                            frameBuffer = new byte[10000];
                        length = bytestream.getRestOfChunk(
                                (int)byteloc, frameBuffer, 0, frameBuffer.length);
                        frameData = Arrays.copyOf(frameBuffer, (int)length);
                        stream = 0;
                        Date d = bytestream.getDate((int)byteloc);
                        byteloc += length;
                        // getTime() outputs in milliseconds, change to seconds.
                        timestamp = d.getTime() / (double)1000;
                        setProgress(byteloc);
                    }
                    
                    // Set the initial timestamp, if necessary, and change
                    // timestamps to be relative.
                    if (timestamp < lastTimestamp && !timestampsFudged) {
                        throw new TtyrecException("Timestamps are not in increasing order");
                    }
                    // Jettyplay dislikes two frames having completely identical
                    // timestamps. So if that happens, increase the timestamp of
                    // the second frame by a small amount, not enough to change
                    // the file's format when saved, but enough to distinguish
                    // them.
                    double d = 1e-12;
                    timestampsFudged = false;
                    while (timestamp <= lastTimestamp) {
                        // yay floating-point arithmetic!
                        timestamp = lastTimestamp + d;
                        d *= 10.0;
                        timestampsFudged = true;
                    }
                    lastTimestamp = timestamp;
                    if (firstframe) {
                        rec.setInitialTimestamp(timestamp);
                        initialTimestamp = timestamp;
                    }
                    timestamp = timestamp - initialTimestamp;

                    // Decoding as ISO-8859-1 turns bytes into codepoints literally,
                    // because it's equal to Unicode for codepoints 0-255.
                    String latin1Data = Charset.forName("ISO-8859-1").
                            decode(ByteBuffer.wrap(frameData)).toString();
                    byte[] oldChoppedOff;
                    if (choppedOff[stream] != null)
                        oldChoppedOff = Arrays.copyOf(choppedOff[stream],
                                choppedOff[stream].length);
                    else oldChoppedOff = new byte[0];
                    byte[] oldFrameData =
                            Arrays.copyOf(frameData,frameData.length);
                    if (couldBeUnicode || stream > 0) {
                        for (int i = 0;; i++) {
                            try {
                                byte[] fd = frameData;
                                if (choppedOff[stream] != null) {
                                    fd = Arrays.copyOf(choppedOff[stream],
                                            choppedOff[stream].length +
                                            frameData.length);
                                    System.arraycopy(frameData, 0, fd,
                                            choppedOff[stream].length,
                                            frameData.length);
                                }
                                /* ignore the result, we're just checking for
                                 * validity... */
                                Charset.forName("UTF-8").newDecoder().
                                        onMalformedInput(CodingErrorAction.REPORT).
                                        decode(ByteBuffer.wrap(
                                               fd,0,fd.length-i)).toString();
                                choppedOff[stream] = Arrays.copyOfRange(fd,
                                        fd.length-i, fd.length);
                                break;
                            } catch (CharacterCodingException ex) {
                                // Looks like it isn't UTF-8 in this frame, implying
                                // that the whole ttyrec isn't Unicode.
                                if (i < 4 && i < frameData.length) continue;
                                try {
                                    if (workingFor.debug) {
                                        System.err.println("\"" +
                                                URLEncoder.encode(latin1Data, "ISO-8859-1") +
                                                "\" is not UTF-8");

                                    }
                                } catch (UnsupportedEncodingException ex1) {
                                }
                                couldBeUnicode = false;
                                rec.setNotUTF8();
                                break;
                            }
                        }
                    }
                    rec.setFrame(previousFrame = new TtyrecFrame(previousFrame,
                            oldFrameData, oldChoppedOff,
                            choppedOff[stream].length, stream, timestamp,
                            sequenceNumber, rec.getBytesRegistry()),
                            framesAnalyzed++);
                    /* A sort of hack to determine autoresizing. The area of the
                     * ttyrec controlled by curses is normally marked with
                     * \e[?1049h .. \e[?1049l, but not all terminals support that.
                     * Therefore, if the recording was taken on a terminal that
                     * doesn't, we mark the /entire recording/ autoresize by
                     * default, and hope for the best. This is done by setting
                     * the autoAutoResize false by default, and setting it true
                     * as soon as we see a command that affects it. */
                    if ((latin1Data.contains("\u001b[?1049h") ||
                         latin1Data.contains("\u001b[?1049l")) &&
                        !rec.containsAutoResizeRangeInformation(sequenceNumber, -1)) {
                        System.out.println("Resetting autoresize data...");
                        rec.setContainsAutoResizeRangeInformation(sequenceNumber);
                        workingFor.resetDecodeWorker();
                        workingFor.cancelLeadingEdgeDecode();
                    }
                    // Has the file finished loading yet?
                    if (workingFor.knownLength() && format == InputFormat.TTYREC) {
                        bytesTotal = workingFor.getBytestream().size();
                    }
                    firstframe = false;
                    if (rec.getLength() < lastTimestamp - rec.getInitialTimestamp())
                        rec.setLength(lastTimestamp - rec.getInitialTimestamp());
                    synchronized(rec) {
                        rec.notifyAll();
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                throw new TtyrecException("Unexpected EOF");
            } catch (NullPointerException ex) {
                ex.printStackTrace();
                throw new TtyrecException("Input in invalid format");
            }
            rec.setLength(lastTimestamp - rec.getInitialTimestamp());
        } catch (InterruptedException ex) {
            // Do nothing, we must have been halted.
        } catch (TtyrecException ex) {
            // The ttyrec couldn't be decoded like this, so let's try
            // to decode it some other way.
            // Order of attempts: first we check to see if it's gzipped
            // (and if it is, try multistream ttyrec, then normal
            // ttyrec), and likewise if it's bzip2ed. If it isn't, we try
            // multistream ttyrec, normal ttyrec, and script. In each case,
            // all multistream attempts are made before all single-stream
            // attempts, so the ttyrec type is changed once or not at all.
            if (formatDebug) System.out.println(ex.getMessage());
            if (format == InputFormat.GZIP && !validHeaderFound) {
                TtyrecWorker analyzer = new TtyrecAnalyzer(workingFor,
                        workingFor.getNextSequenceNumber(),
                        InputFormat.BZIP2);
                workingFor.newBackportWorkerOfType(this, analyzer);
                return;
            }
            if (format == InputFormat.BZIP2 && !validHeaderFound) {
                TtyrecWorker analyzer = new TtyrecAnalyzer(workingFor,
                        workingFor.getNextSequenceNumber(),
                        InputFormat.TTYREC);
                workingFor.newBackportWorkerOfType(this, analyzer);
                return;
            }
            Ttyrec rec = workingFor.getTtyrec();
            if (rec.getFileType() == Ttyrec.FileType.MultistreamTtyrec) {
                // Try again, this time looking for the standard ttyrec
                // format.
                TtyrecWorker analyzer = new TtyrecAnalyzer(workingFor,
                        workingFor.getNextSequenceNumber(), format);
                rec.setFileType(Ttyrec.FileType.Ttyrec);
                workingFor.newBackportWorkerOfType(this, analyzer);
                return;
            } else {
                if (format == InputFormat.TTYREC) {
                    TtyrecWorker analyzer = new TtyrecAnalyzer(workingFor,
                            workingFor.getNextSequenceNumber(),
                            InputFormat.SCRIPT);
                    workingFor.newBackportWorkerOfType(this, analyzer);
                    return;
                }
                // There isn't a further fallthrough here, probably, because
                // InputFormat.SCRIPT is loose enough to recognise anything.
                throw new Error(
                        "Input is in no format, not even script format; "+
                        "system clock jumped backwards?");
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (workingFor.debug)
            System.out.println("Analyzer finalized! (workingFor="+
                    workingFor+workingFor.hashCode()+
                    ", this="+this+this.hashCode()+")");
    }
}
