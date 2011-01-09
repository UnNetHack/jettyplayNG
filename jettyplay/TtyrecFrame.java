/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Holds information about one frame of a ttyrec.
 * @author ais523
 */
public class TtyrecFrame {
    private final TtyrecFrame previous;
    private final TtyrecFrame[] previousInStream;
    private final byte[] frameData; // the raw bytes that make up the frame
    private final byte[] unicodePrefix; // bytes to prepend for Unicode to work
    private final int unicodeChopEnding; // bytes to chop off for Unicode to work
    private final double relativeTimestamp;
    private vt320 terminalState;
    private final int stream;
    private final int seqNumber; // sequence number of this frame
    private final int analyzerSeqNumber; // sequence number of the analyzer
    private int decoderSeqNumber; // sequence number of the analyzer
    private boolean dirty = true;

    private final Map<Integer, byte[]> bytesRegistry;

    /**
     * The maximum number of streams possible in a ttyrec, across all formats.
     * Currently set to 2, the number used by .ttyrec2 (regular ttyrecs use only
     * 1, a lower value).
     */
    public static final int MAX_STREAM_COUNT = 2;

    //public static long created = 0;
    //public static long destroyed = 0;

    /**
     * Creates a new frame of a ttyrec.
     * @param previous The immediately preceding frame in the same ttyrec. This
     * can be null if this frame is the first one.
     * @param frameData The raw data of the frame.
     * @param unicodePrefix A sequence of bytes that should be prepended to the
     * frame when decoding it as UTF-8, maybe because the frame starts in the
     * middle of a UTF-8 character.
     * @param unicodeChopEnding The number of bytes that should be ignored at
     * the end of the frame when decoding it as UTF-8, maybe because the frame
     * ends in the middle of a UTF-8 character.
     * @param stream Which stream number in the ttyrec this frame represents.
     * Stream 0 is considered raw terminal data; stream 1 is considered binary
     * data that is not decoded in any way (and is shown to the user as a
     * sequence of raw values). Stream 1 is used for user input in the .ttyrec2
     * format.
     * @param relativeTimestamp The timestamp of this frame, minus the timestamp
     * of the first frame in the ttyrec.
     * @param analyzerSeqNumber The sequence number of the analyzer that
     * analyzed this frame.
     * @param bytesRegistry A map from hashcodes of strings representing byte
     * arrays to the byte arrays themselves; used to deduplicate byte arrays
     * needed by the frames to save memory. This might be altered by the
     * constructor to add new byte arrays to it.
     */
    public TtyrecFrame(TtyrecFrame previous, byte[] frameData,
                       byte[] unicodePrefix, int unicodeChopEnding,
                       int stream, double relativeTimestamp, int analyzerSeqNumber,
                       Map<Integer,byte[]> bytesRegistry) {
        this.previous = previous;
        if (previous == null) {
            seqNumber = 0;
            previousInStream = new TtyrecFrame[MAX_STREAM_COUNT];
        } else {
            seqNumber = previous.seqNumber + 1;
            previousInStream = previous.previousInStream.clone();
            previousInStream[previous.stream] = previous;
        }
        this.bytesRegistry = bytesRegistry;
        this.frameData = registerBytes(frameData);
        this.unicodeChopEnding = unicodeChopEnding;
        this.stream = stream;
        this.relativeTimestamp = relativeTimestamp;
        this.terminalState = null;
        this.analyzerSeqNumber = analyzerSeqNumber;
        this.decoderSeqNumber = -1;
        this.unicodePrefix = unicodePrefix;
        //System.out.println("Frames created: " + ++created);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        //System.out.println("Frames destroyed: " + ++destroyed);
    }

    /**
     * Returns the timestamp of this frame, relative to the first frame in the
     * same ttyrec.
     * @return The number of seconds difference between this frame, and the
     * start of its ttyrec.
     */
    public double getRelativeTimestamp() {
        return relativeTimestamp;
    }
    /**
     * Returns the state of the terminal upon displaying this frame. This is
     * unlikely to have a sensible value until the frame is decoded, and may
     * never have a sensible value for streams other than stream 0.
     * @return A vt320 terminal initialized with the required state.
     */
    public synchronized vt320 getTerminalState() {
        return terminalState;
    }
    /**
     * Queries this frame's stream number.
     * @return The stream number of this frame.
     */
    public int getStream() {
        return stream;
    }
    /**
     * Gets the sequence number of the analyzer that determined that this frame
     * existed.
     * @return The analyzer's sequence number.
     */
    public int getAnalyzerSeqNumber() {
        return analyzerSeqNumber;
    }
    /**
     * Gets the sequence number of the decoder that worked out the terminal
     * state of this frame, if any.
     * @return The decoder's sequence number.
     */
    public int getDecoderSeqNumber() {
        return decoderSeqNumber;
    }


    /**
     * Queries whether this frame's decoding was changed since it was last drawn
     * to a screen.
     * @return Whether this frame's decoding has changed recently.
     */
    public boolean isDirty() {
        return dirty;
    }
    /**
     * Sets whether this frame's decoding has changed since it was last drawn to
     * a screen.
     * @param dirty Whether this frame needs to be redrawn to a screen, if
     * currently displayed.
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Decodes one frame. This should be called by a ttyrec decoder.
     * @param encoding The encoding that this frame should be considered to be
     * in when decoding it.
     * @param rows The number of rows in the terminal immediately before this
     * frame is decoded to it.
     * @param columns The number of columns in the terminal immediately before
     * this frame is decoded to it.
     * @param sizeForced True if the terminal size should be considered
     * irrevocably fixed, and not autosized regardless of other autoresize data.
     * @param autoAutoResize True if it should be automatically detected whether
     * the terminal should be automatically resized (i.e. autodetect whether
     * autodetect should be used); false if the terminal should just be
     * autoresized regardless. This is ignored if sizeForced is set to true.
     * @param decoderSeqNumber The sequence number of the decoder that requested
     * this frame decode.
     */
    public synchronized void decodeFrame
            (Ttyrec.Encoding encoding, int rows, int columns,
            boolean sizeForced,
            boolean autoAutoResize, int decoderSeqNumber) {
        if (decoderSeqNumber <= this.decoderSeqNumber) return;
        this.decoderSeqNumber = decoderSeqNumber;
        if (previous == null) {
            terminalState = new vt320();
            terminalState.setScreenSize(columns, rows);
            if (!autoAutoResize)
                terminalState.setAutoResize(true);
            if (sizeForced) {
                terminalState.setAutoResize(false);
                terminalState.setVetoAutoResize(true);
            }
        } else {
            try {
                terminalState = (vt320) previous.getTerminalState().clone();
            } catch (CloneNotSupportedException ex) {
                // Something has gone very wrong...
                throw new Error(ex.getMessage());
            }
        }
        if (stream != 0) return; // nonzero streams don't need decoding
        if (encoding == Ttyrec.Encoding.IBM)
            terminalState.setIBMCharset(true);
        else
            terminalState.setIBMCharset(false);
        if (encoding == Ttyrec.Encoding.UTF8) {
            terminalState.putString(getUnicodeData());
        } else {
            // Decoding as ISO-8859-1 turns bytes into codepoints literally,
            // because it's equal to Unicode for codepoints 0-255.
            terminalState.putString(getRawData());
        }
        terminalState.makeReadOnly();
        setDirty(true);
    }

    /**
     * Returns whether the decoded terminal state of this frame contains a
     * particular string or regex.
     * @param p The Pattern to search for in this frame.
     * @return Whether the Pattern specified was found in this frame.
     */
    public boolean containsPattern(Pattern p) {
        if (terminalState == null) return false;
        return terminalState.containsPattern(p);
    }

    private AttributedString attributedAnnotation(double relativeTime) {
        // The color depends on how long ago the annotation happened.
        // The value is 0 for now, 192 for infinity, 128 after 10 seconds.
        double timeSince = relativeTime - relativeTimestamp;
        if (timeSince < 0) timeSince = 0;
        int x = (int)((1.0-Math.pow(3.0, -timeSince/10.0))*192);
        Color printableColor = new Color(x,x,x);
        Color unprintableColor = new Color(255,x,x);
        // We construct the string in two passes; one adds the text, the
        // other adds the attributes.
        String data = getUnicodeData();
        if (data == null) data = getRawData();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (Character.isISOControl(c)) {
                // Unprintables are shown as a key sequence (with control
                // and meta), in red.
                sb.append("^");
                if (c > 128) sb.append("%");
                sb.append(Character.toChars((c % 128)+64));
            } else if(Character.isWhitespace(c)) {
                // Whitespace is shown as a red middot.
                sb.append("\u00b7");
            } else {
                // Other characters are shown as themselves, in black.
                sb.append(c);
            }
        }
        AttributedString as = new AttributedString(sb.toString());
        int j = 0;
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (Character.isISOControl(c)) {
                int k = 2;
                if (c > 128) k = 3;
                as.addAttribute(TextAttribute.FOREGROUND,
                        unprintableColor, j, j+k);
                j+=k;
            } else if(Character.isWhitespace(c)) {
                as.addAttribute(TextAttribute.FOREGROUND,
                        unprintableColor, j, j+1);
                j++;
            } else {
                as.addAttribute(TextAttribute.FOREGROUND,
                        printableColor, j, j+1);
                j++;
            }
        }
        return as;
    }

    private byte[] registerBytes(byte[] data) {
        if (data == null) return null;
        Integer hc = Charset.forName("ISO-8859-1").
                decode(ByteBuffer.wrap(data)).toString().hashCode();
        byte[] s = bytesRegistry.get(hc);
        if (s != null && Arrays.equals(s, data)) return s;
        bytesRegistry.put(hc, data);
        return data;
    }

    /**
     * Returns the data for this frame raw, encoding each byte of the input
     * string as one Latin-1 character.
     * @return the data
     */
    private String getRawData() {
        String latin1Data = Charset.forName("ISO-8859-1").
                decode(ByteBuffer.wrap(frameData)).toString();
        return latin1Data;
    }

    /**
     * Returns the data for this frame, as it would be translated into
     * Unicode; this may omit a few bytes of raw data at the end, or add extra
     * at the start, to allow for the situation where a frame boundary occurs
     * inside a Unicode character.
     * @return the unicodeData
     */
    private String getUnicodeData() {
        byte[] b;
        if (unicodePrefix.length != 0 || unicodeChopEnding != 0) {
            b = new byte[frameData.length + unicodePrefix.length - unicodeChopEnding];
            System.arraycopy(unicodePrefix,0,b,0,unicodePrefix.length);
            System.arraycopy(frameData,0,b,
                    unicodePrefix.length,frameData.length - unicodeChopEnding);
        } else b = frameData;
        String unicodeData;
        try {
            unicodeData = Charset.forName("UTF-8").newDecoder().
                    onMalformedInput(CodingErrorAction.REPORT).
                    decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException ex) {
            throw new RuntimeException("UTF-8 became invalid while we weren't looking at it");
        }
        return unicodeData;
    }

    private class AttributedStringAndNumber {
        private final AttributedString a;
        private final int n;
        AttributedStringAndNumber(AttributedString a, int n) {
            this.a = a;
            this.n = n;
        }
        public AttributedString getA() {
            return a;
        }
        public int getN() {
            return n;
        }
    }
    private boolean hasNextAnnotation(double relativeTime, int seqNumber, int stream) {
        if (seqNumber >= this.seqNumber && this.stream == stream) return true;
        if (previousInStream[stream] != null)
            return previousInStream[stream].
                    hasNextAnnotation(relativeTime, seqNumber, stream);
        return false;
    }
    private AttributedStringAndNumber nextAnnotation(
            double relativeTime, int seqNumber, int stream) {
        if (seqNumber >= this.seqNumber && this.stream == stream) {
            return new AttributedStringAndNumber(
                    attributedAnnotation(relativeTime),
                    this.seqNumber - 1);
        }
        if (previousInStream[stream] != null)
            return previousInStream[stream].
                    nextAnnotation(relativeTime, seqNumber, stream);
        return null;
    }
    /**
     * Gets a lazy list of raw data in frames with the given stream, starting at
     * this frame and extending backwards over previous frames. The data is
     * coloured according to time difference with a given time, and control
     * codes are coloured red and translated into a human-readable form.
     * @param relativeTime The time, relative to the start of the ttyrec, to
     * colour the data relative to.
     * @param stream The stream from which the raw data should be returned.
     * @return A lazy list of raw data from frames with the given stream.
     */
    public Iterable<AttributedString> getRawDataIterator(final double relativeTime, final int stream) {
        return new Iterable<AttributedString>() {
            public Iterator<AttributedString> iterator() {
                return new Iterator<AttributedString>() {
                    int lastSeqNumber = seqNumber;
                    public boolean hasNext() {
                        return hasNextAnnotation(
                                relativeTime, lastSeqNumber, stream);
                    }
                    public AttributedString next() {
                        AttributedStringAndNumber asan = nextAnnotation(
                                relativeTime, lastSeqNumber, stream);
                        lastSeqNumber = asan.getN();
                        return asan.getA();
                    }
                    public void remove() {
                        throw new UnsupportedOperationException(
                                "Attempt to modify an immutable list");
                    }
                };
            }
        };
    }
}
