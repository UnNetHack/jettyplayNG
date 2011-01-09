/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

/**
 * A class representing a terminal recording (ttyrec).
 * It is capable of analyzing the ttyrec in the background, if required.
 * @author ais523
 */
public class Ttyrec {

    private ArrayList<TtyrecFrame> frames;
    private double initialTimestamp;
    private double length;
    private double lengthOffset;

    /**
     * The formats that a Ttyrec is capable of using.
     */
    public enum FileType {
        /**
         * A traditional ttyrec with one stream (*.rec, *.ttyrec).
         */
        Ttyrec,
        /**
         * A ttyrec with two data streams (*.ttyrec2).
         */
        MultistreamTtyrec
    };
    private FileType fileType;

    /**
     * The encodings in which a ttyrec might be written.
     */
    public enum Encoding {
        /**
         * UTF-8, a common Unicode encoding.
         */
        UTF8,
        /**
         * One of the IBM-extended 8-bit character sets.
         */
        IBM,
        /**
         * Latin-1/ISO-8859-1, an 8-bit encoding that maps to the first 256
         * codepoints of Unicode.
         */
        Latin1,
        /**
         * A special value to specify that the encoding should be autodetected.
         */
        Autodetect
    };
    private Set<Encoding> encodings;
    private Encoding encoding = Encoding.Autodetect;
    private Set<Integer> containsAutoResizeRangeInformation;
    private Set<Integer> overrideAutoResizeRangeInformation;
    private int forcedWidth = -1;
    private int forcedHeight = -1;
    private int wantedFrame = -1;
    private boolean isStreaming;
    private Date lastActivity;
    private final Map<Integer,byte[]> bytesRegistry;
    
    /**
     * Creates a new ttyrec, without any information filled in
     * yet.
     */
    public Ttyrec() {
        frames = new ArrayList<TtyrecFrame>();
        initialTimestamp = 0;
        length = 0;
        lengthOffset = 0;
        isStreaming = false;
        // We don't know which formats the data could be in yet.
        encodings = new HashSet<Encoding>();
        encodings.add(Encoding.UTF8);
        encodings.add(Encoding.IBM);
        encodings.add(Encoding.Latin1);
        // It's easier to detect that a ttyrec isn't multistream, than that
        // it is. So set to multistream initially, and let the analyzer
        // disillusion us.
        fileType = FileType.MultistreamTtyrec;
        // This set contains all the analyzer sequence numbers for which
        // the file contains auto-resize range information.
        containsAutoResizeRangeInformation = new HashSet<Integer>();
        // ... and this set contains all the decoder sequence numbers for which
        // we should act as if there's info, because the decoding goes mad if
        // we don't.
        overrideAutoResizeRangeInformation = new HashSet<Integer>();
        // This set contains strings used in the frames, in the hope of
        // saving memory because strings are likely to be used more than
        // once.
        bytesRegistry = new HashMap<Integer,byte[]>();
    }

    /**
     * Gets the format of the file represented by this ttyrec.
     * @return The ttyrec format used.
     */
    public FileType getFileType() {
        return fileType;
    }
    /**
     * Sets the format that the data in this ttyrec should be interpreted as.
     * (It does not change the data, merely reinterprets it; thus, this function
     * should not be used to reformat a ttyrec.)
     * @param fileType The format to interpret this ttyrec's data in.
     */
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Forgets all the information about encoding autodetection discovered so
     * far for this ttyrec.
     */
    public void resetEncodings() {
        encodings = new HashSet<Encoding>();
        encodings.add(Encoding.UTF8);
        encodings.add(Encoding.IBM);
        encodings.add(Encoding.Latin1);
        encoding = Encoding.Autodetect;
    }
    /**
     * Flags this ttyrec as definitely not being in UTF-8, for autodetection
     * purposes.
     */
    public void setNotUTF8() {
        encodings.remove(Encoding.UTF8);
        if (encoding == Encoding.UTF8) {
            encoding = Encoding.Autodetect;
        }
    }
    /**
     * Flags this ttyrec as definitely not being in IBM-Extended format, for
     * autodetection purposes.
     */
    public void setNotIBM() {
        encodings.remove(Encoding.IBM);
        if (encoding == Encoding.IBM) {
            encoding = Encoding.Autodetect;
        }
    }
    /**
     * Flags this ttyrec as definitely not being in Latin-1 format, for
     * autodetection purposes.
     */
    public void setNotLatin1() {
        encodings.remove(Encoding.Latin1);
        if (encoding == Encoding.Latin1) {
            encoding = Encoding.Autodetect;
        }
    }
    /**
     * Queries whether a given encoding is compatible with the autodetect
     * information discovered so far.
     * @param e The encoding to check.
     * @return true if it has not been proved that the encoding specified is
     * not used by this ttyrec.
     */
    public boolean isEncodingPossible(Encoding e) {
        return encodings.contains(e);
    }
    /**
     * Returns the encoding currently specified for this ttyrec.
     * @return The encoding specified for this ttyrec. If the ttyrec is set to
     * autodetect, returns Autodetect rather than trying to actually autodetect
     * the encoding.
     */
    public Encoding getEncoding() {
        return encoding;
    }
    /**
     * Returns the encoding currently detected for this ttyrec; either the
     * encoding specified via setEncoding, or the encoding autodetected if set
     * to autodetect.
     * @return An encoding other than Autodetect.
     */
    public Encoding getActualEncoding() {
        if (encoding == Encoding.Autodetect) {
            if (encodings.contains(Encoding.UTF8)) return Encoding.UTF8;
            if (encodings.contains(Encoding.IBM)) return Encoding.IBM;
            return Encoding.Latin1;
        }
        return encoding;
    }
    /**
     * Changes the encoding that should be used by this ttyrec.
     * @param e The encoding to use; or Autodetect to autodetect an encoding.
     * @throws IllegalArgumentException if the encoding given is one that's
     * incompatible with data detected so far.
     */
    public void setEncoding(Encoding e) {
        if (!isEncodingPossible(e))
            throw new IllegalArgumentException();
        this.encoding = e;
    }
    /**
     * Sets the encoding used by this ttyrec back to its default value.
     */
    public void resetEncoding() {
        encoding = Encoding.Autodetect;
    }

    /**
     * Returns whether this ttyrec contains auto-resize information, from the
     * point of view of a particular analyzer and decoder.
     * @param analyzerSequenceNumber The analyzer's sequence number.
     * @param decoderSequenceNumber The decoder's sequence number, or -1 to
     * check from the point of view of the analyzer only.
     * @return Whether the auto-resize information exists.
     */
    public boolean containsAutoResizeRangeInformation(
            int analyzerSequenceNumber, int decoderSequenceNumber) {
        return containsAutoResizeRangeInformation.
                contains(analyzerSequenceNumber) ||
                overrideAutoResizeRangeInformation.
                contains(decoderSequenceNumber);
    }
    /**
     * Specifies that this ttyrec contains autoresize-range information, from
     * the point of view of the analysis worker with the given sequence number.
     * @param sequenceNumber An analysis worker's sequence number.
     */
    public void setContainsAutoResizeRangeInformation(int sequenceNumber) {
        containsAutoResizeRangeInformation.add(sequenceNumber);
    }
    /**
     * Specifies that this ttyrec should be interpreted as containing
     * autoresize-range information to avoid a pathological case in the
     * terminal size autodetection logic, from the point of view of the
     * decode worker with the given sequence number.
     * @param sequenceNumber A decode worker's sequence number.
     */
    public void overrideContainsAutoResizeRangeInformation(int sequenceNumber) {
        overrideAutoResizeRangeInformation.add(sequenceNumber);
    }

    /**
     * Specifies that this ttyrec's size should or should not be autodetected,
     * and if not autodetected, which size to force it to. Note that there will
     * not be sensible results if one of the width/height is set to autodetect,
     * but the other to a fixed value; thus, both should be set or unset at once.
     * @param width The terminal width, or -1 to autodetect.
     * @param height The terminal height, or -1 to autodetect.
     */
    public void setForcedSize(int width, int height) {
        forcedWidth = width;
        forcedHeight = height;
    }
    /**
     * Returns the height of the ttyrec, or -1 if it's being autodetected.
     * @return The height that the terminal is forced to be.
     */
    public int getForcedHeight() {
        return forcedHeight;
    }
    /**
     * Returns the width of the ttyrec, or -1 if it's being autodetected.
     * @return The width that the terminal is forced to be.
     */
    public int getForcedWidth() {
        return forcedWidth;
    }

    /**
     * Returns a Map from hashcodes of Strings that represent a literal
     * translation of bytes into codepoints of byte arrays to the arrays
     * themselves, used to cache byte arrays to prevent duplication.
     * @return The registry of byte arrays.
     */
    public Map<Integer, byte[]> getBytesRegistry() {
        return bytesRegistry;
    }

    /**
     * Gets the timestamp of the first frame of this ttyrec. Due to the way the
     * ttyrec format works, this might either be a time measured relative to
     * some epoch (1 January 1970 is a likely possibility, but not the only
     * one), or zero.
     * @return Zero, or the number of seconds since an arbitrary date.
     */
    public double getInitialTimestamp() {
        return initialTimestamp;
    }
    /**
     * Changes the time at which time in this ttyrec is measured relative to;
     * either 0 to measure relative to the first frame, or the number of seconds
     * since an arbitrary date to measure relative to that date. This must be
     * the same as the timestamp of the first frame in the ttyrec.
     * @param timestamp The timestamp of the first frame in the ttyrec.
     */
    public void setInitialTimestamp(double timestamp) {
        initialTimestamp = timestamp;
    }
    /**
     * Returns the length of the ttyrec, in seconds.
     * @return The length of the ttyrec in seconds.
     */
    public double getLength() {
        return length + lengthOffset;
    }
    /**
     * Sets the length of time between the ttyrec's first and last frames.
     * This does not update anything else in the ttyrec itself; it is the
     * responsibility of the ttyrec analyzer to keep this synched with the
     * actual ttyrec length.
     * @param length The length to set.
     */
    public void setLength(double length) {
        this.length = length;
    }
    /**
     * Sets the length of time after the ttyrec's last frame, but still within
     * the ttyrec; this is only useful for streaming.
     * @param lengthOffset The extra length to add at the end of the ttyrec.
     */
    public void setLengthOffset(double lengthOffset) {
        this.lengthOffset = lengthOffset;
    }
    /**
     * Returns whether this ttyrec is considered to be streaming (a ttyrec sent
     * in realtime, either with or without timestamps included in the flow to
     * allow for accurate recording despite network latency).
     * @return Whether the ttyrec is considered to be streaming.
     */
    public boolean isStreaming() {
        return isStreaming;
    }
    /**
     * Specifies whether or not this ttyrec is considered to be streaming.
     * @param isStreaming The new value of whether the ttyrec is being streamed
     * or not.
     */
    public void setIsStreaming(boolean isStreaming) {
        this.isStreaming = isStreaming;
    }
    /**
     * Returns the date on which the most recent update was made to the ttyrec
     * due to new data arriving on the input; this is mostly useful for ttyrecs
     * that are being streamed.
     * @return The date and time at which the ttyrec was last changed by its
     * input source.
     */
    public Date getLastActivity() {
        return lastActivity;
    }
    /**
     * Specifies that the ttyrec was changed due to data appearing on its input
     * source.
     * @param lastActivity The date and time at which the ttyrec was changed due
     * to new data on its input source.
     */
    public void setLastActivity(Date lastActivity) {
        this.lastActivity = lastActivity;
    }

    /**
     * Returns the number of columns that this ttyrec appears to have, based on
     * decode progress so far.
     * @return An estimated or exact number of columns. If it changes throughout
     * the course of the ttyrec, the value at the start is used.
     */
    public int getColumns() {
        if (frames.size() == 0 || frames.get(0).getTerminalState() == null)
            return 80;
        return frames.get(0).getTerminalState().getColumns();
    }
    /**
     * Returns the number of rows that this ttyrec appears to have, based on
     * decode progress so far.
     * @return An estimated or exact number of rows. If it changes throughout
     * the ttyrec, the value at the start is used.
     */
    public int getRows() {
        if (frames.size() == 0 || frames.get(0).getTerminalState() == null)
            return 24;
        return frames.get(0).getTerminalState().getRows();
    }

    /**
     * Alters one frame of the ttyrec, or adds one to the end.
     * @param ttyrecFrame The frame to add, or new value of the frame to alter.
     * @param index The index of the frame to alter, or the number of frames to
     * add one.
     */
    public synchronized void setFrame(TtyrecFrame ttyrecFrame, int index) {
        if (index != frames.size())
            frames.set(index, ttyrecFrame);
        else
            frames.add(ttyrecFrame);
        notifyAll(); // wake decoders waiting for new frames
    }
    /**
     * Gets the frame at (or before, if no frame is exactly at) the
     * given time, measured relative to the start of the recording.
     * @param time The time in seconds.
     * @return The frame at or before the given time.
     */
    public synchronized int getFrameIndexAtRelativeTime(double time) {
        // For efficiency, do a binary search on frame times.
        int searchDistance = frames.size();
        if (searchDistance == 0) return 0;
        int currentIndex = 0;
        while (searchDistance > 1) {
            double t = frames.get(currentIndex).getRelativeTimestamp();
            if (t == time) return currentIndex;
            searchDistance = (searchDistance + 1) / 2;
            if (t < time) currentIndex += searchDistance;
            else currentIndex -= searchDistance;
            if (currentIndex < 0) currentIndex = 0;
            if (currentIndex >= frames.size())
                currentIndex = frames.size() - 1;
        }
        if (frames.get(currentIndex).getRelativeTimestamp() > time)
            currentIndex--;
        return currentIndex;
    }
    /**
     * Returns the frame at the given index. Note that the first frame is 0 in
     * the numbering scheme Jettyplay uses internally, despite being 1 from the
     * user's point of view; thus, adding or subtracting 1 may be needed.
     * @param i The index of the frame returned.
     * @return The frame at the index requested.
     */
    public TtyrecFrame getFrameAtIndex(int i) {
        return frames.get(i);
    }
    /**
     * Returns the number of frames currently existing in the ttyrec. In
     * general, this will count the number of frames analyzed, even if they
     * haven't all been decoded yet.
     * @return The number of frames in the ttyrec.
     */
    public int getFrameCount() {
        return frames.size();
    }

    /**
     * Returns the frame number that this ttyrec should jump to as soon as it's
     * analyzed.
     * @return The frame number that should be jumped to, once it exists.
     */
    public int getWantedFrame() {
        return wantedFrame;
    }
    /**
     * Specifies that a particular frame in the ttyrec should be jumped to, once
     * it's been analyzed.
     * @param wantedFrame The frame number that should be jumped to, once it
     * exists.
     */
    public void setWantedFrame(int wantedFrame) {
        this.wantedFrame = wantedFrame;
    }
}
