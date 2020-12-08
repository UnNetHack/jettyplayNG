/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Timer;

/**
 * A source capable of providing a ttyrec, analyzing and decoding it.
 * Everything happens asynchronously, and can be halted at various
 * stages; there are up to two TtyrecWorkers doing a particular job
 * available at any given time, one which is the "leading edge" and always
 * continues going regardless (until it is overtaken by the second), and
 * the other of which is the "backport" process which re-analyzes/re-decodes
 * with information that the leading edge discovered. If the leading edge
 * process finds that it's operating on invalid assumptions (or the user
 * changes the assumptions), any current backport process is terminated,
 * and a new one starts with the new assumptions. If the backport process
 * overtakes the leading edge process (as measured in bytes parsed for
 * analyze workers and frames parsed for decode workers), the leading edge
 * process is ended at a convenient moment, and the backport process takes
 * its place.
 * <p>
 * A single Ttyrec object holds the results of both analyze workers and both
 * decode workers; as decode TtyrecWorkers proceed frame-by-frame, all frames
 * before the backport process's current frame are as determined by it, and
 * later frames are as determined by the leading edge process or by
 * now-terminated backport processes. The same logic applies to analyze
 * workers, which produce results frame-by-frame. The resulting Ttyrec
 * object may therefore be rather inconsistent, and things that use it need
 * to take that into account.
 * <p>
 * There's one other important piece of state here; the raw bytestream
 * from the source, which never changes but which might be appended to
 * at arbitrary moments. If the leading edge analyze process reaches a stage
 * where it needs more data to continue, it waits on the bytestream's monitor,
 * and is notified if and when more data appears on the bytestream. (This
 * cannot happen with the backport process, because it cannot reach that
 * stage without overtaking the leading edge process.) Likewise, the leading
 * edge decode process may reach the last frame that's been analyzed so far;
 * in such a case, it should wait on the ttyrec's monitor, and is notified
 * if and when more information is available on the ttyrec.
 * <p>
 * TtyrecWorkers may also need to be suspended, generally because another
 * ttyrec has been selected to view by the user. In this case, a flag is
 * set to tell them to stop; they should wait on their own monitor for the
 * flag to be cleared again. Likewise, they must be capable of stopping
 * altogether.
 *
 * @author ais523
 */
public abstract class TtyrecSource extends Thread {

    private volatile TtyrecAnalyzer leadingEdgeAnalyze;
    private volatile TtyrecAnalyzer backportAnalyze;
    private volatile TtyrecDecoder leadingEdgeDecode;
    private volatile TtyrecDecoder backportDecode;
    private final Ttyrec rec;
    private final ByteChunkList bytestream;
    private int nextSequenceNumber;
    private final Set<ProgressListener> analysisListeners;
    private final Set<ProgressListener> decodeListeners;
    private final Set<ProgressListener> readListeners;
    private final Timer listenerTimer;
    private volatile boolean analyzeEventHappened = false;
    private volatile boolean decodeEventHappened = false;
    private volatile boolean readEventHappened = false;
    final boolean debug = false;

    /**
     * Creates the ttyrec source. This should be overriden in implementing
     * classes to take arguments that specify things like the file or
     * network address to read the bytestream from. This also creates some
     * default analyzers and decoders, but leaves them paused by default.
     */
    public TtyrecSource() {
        super("Ttyrec Source");
        rec = new Ttyrec();
        bytestream = new ByteChunkList();
        backportAnalyze = null;
        backportDecode = null;
        analysisListeners = new HashSet<ProgressListener>();
        decodeListeners = new HashSet<ProgressListener>();
        readListeners = new HashSet<ProgressListener>();
        listenerTimer = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                firePendingEvents();
            }
        });
        listenerTimer.restart();
        // The analyzer must be created before the decoder.
        // The analyzer starts with the most obvious-to-reject format (GZIP),
        // then tries the other formats in turn.
        leadingEdgeAnalyze =
                new TtyrecAnalyzer(this, 1, TtyrecAnalyzer.InputFormat.GZIP);
        // The decoder starts by trying 24x80, expanding if necessary.
        leadingEdgeDecode = new TtyrecDecoder(this, 2, 24, 80);
        leadingEdgeAnalyze.start();
        leadingEdgeDecode.start();
        leadingEdgeDecode.setPriority(MIN_PRIORITY);
        nextSequenceNumber = 3;
    }

    /**
     * Unpauses all workers for this source.
     */
    public void completeUnpause() {
        try {
            backportAnalyze.resumeWorking();
        } catch (NullPointerException x) {}
        try {
            leadingEdgeAnalyze.resumeWorking();
        } catch (NullPointerException x) {}
        try {
            backportDecode.resumeWorking();
        } catch (NullPointerException x) {}
        try {
            leadingEdgeDecode.resumeWorking();
        } catch (NullPointerException x) {}
        if (debug) {
            System.err.println("Unpausing " + this);
        }
    }

    /**
     * Cancels all work on this source immediately. The source is unusable
     * after this, so it should generally only be called in preparation for
     * its destruction. This must be called from a thread that is neither
     * this source, nor any of its workers (e.g. you could call it from the
     * AWT event-handling thread). This method does not return until the
     * source, and all its workers, have stopped running. The TtyrecSource
     * cannot be deallocated until this method is called, and it should only
     * be called once on each source.
     */
    public void completeCancel() {
        if (debug) {
            System.err.println("Cancelling " + this + this.hashCode());
        }        listenerTimer.stop();
        listenerTimer.removeActionListener(listenerTimer.getActionListeners()[0]);
        try {
            backportAnalyze.stopWorking();
            try {
                backportAnalyze.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            backportAnalyze = null;
        }  catch (NullPointerException x) {}
        try {
            leadingEdgeAnalyze.stopWorking();
            try {
                leadingEdgeAnalyze.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            leadingEdgeAnalyze = null;
        } catch (NullPointerException x) {}
        try {
            backportDecode.stopWorking();
            try {
                backportDecode.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            backportDecode = null;
        }  catch (NullPointerException x) {}
        try {
            leadingEdgeDecode.stopWorking();
            try {
                leadingEdgeDecode.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            leadingEdgeDecode = null;
        } catch (NullPointerException x) {}
        interrupt();
        cancelIO();
        try {
            join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (debug) {
            System.err.println("Canceled " + this + this.hashCode());
        }
    }

    /**
     * Cancels the current leading-edge decode worker. This would typically
     * be called by an analysis worker who had changed things sufficiently
     * that the leading-edge decode worker would no longer be operating
     * under a set of assumptions that made sense. If there's an existing
     * backport decode worker, it becomes the new leading-edge decode
     * worker. Otherwise, nothing happens, under the assumption that an
     * existing backport decode worker became the leading-edge decoder while
     * or just before this method was called, and because leaving the source
     * with no working decode workers would cause the application to hang
     * unless the ttyrec was already fully decoded.
     */
    public void cancelLeadingEdgeDecode() {
        try {
            subsumeWorker(leadingEdgeDecode);
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Starts reading the bytestream. This method can terminate if the
     * bytestream has definitely finished, but should remain running but
     * suspended (e.g. in a blocking input operation) if more input could
     * arrive, but none has yet. It should terminate if interrupted (which
     * would typically happen just before the object was destroyed).
     * <p>
     * This method should call notifyAll() on the bytestream's monitor
     * after adding new bytes to the end of the bytestream, so that workers
     * waiting for more data can continue to run.
     * <p>
     * This method is also responsible for updating a few data on the
     * ttyrec itself: it must set the ttyrec's length offset (the length
     * of time between the last frame and the end of the ttyrec, which
     * should be 0 when not streaming), whether the ttyrec is streaming or
     * not, and the time at which the last input occurred.
     */
    @Override
    abstract public void run();

    /**
     * Specifies whether it is known that all the bytes of the input have
     * definitely been read.
     * @return True if the input is of known length, and has all been read.
     */
    abstract public boolean knownLength();

    /**
     * The URI of the input that this source is reading.
     * @return The URI.
     * @throws URISyntaxException If a URI cannot be constructed.
     */
    abstract public URI getURI() throws URISyntaxException;

    /**
     * Causes a leading-edge worker to be halted, with the matching backport
     * worker taking its place. Attempting to subsume a worker more than once
     * has no effect; this should only be called on a leading-edge worker, but
     * is safe even if a former leading-edge worker was cancelled or subsumed
     * before this method is called.
     * @param overtaken The leading-edge worker to subsume.
     */
    public void subsumeWorker(TtyrecWorker overtaken) {
        synchronized (this) {
            if (leadingEdgeDecode == backportDecode &&
                    overtaken == leadingEdgeDecode)
                {backportDecode = null; return;}
            if (leadingEdgeAnalyze == backportAnalyze &&
                    overtaken == leadingEdgeAnalyze)
                {backportAnalyze = null; return;}
        }
        synchronized (overtaken) {
            if (overtaken.isSubsumed()) return;
            overtaken.setSubsumed(true);
        }
        if (overtaken instanceof TtyrecAnalyzer && backportAnalyze != null &&
            overtaken != backportAnalyze) {
            if (debug) {
                System.err.println("Subsuming analyze " + leadingEdgeAnalyze +
                        leadingEdgeAnalyze.hashCode());
            }
            synchronized (this) {
                if (leadingEdgeAnalyze == overtaken || leadingEdgeAnalyze == null)
                    leadingEdgeAnalyze = backportAnalyze;
                else
                    return;
            }
            overtaken.stopWorking();
            synchronized(this) {
                if(backportAnalyze == leadingEdgeAnalyze)
                    backportAnalyze = null;
            }
/*            try {
                if(Thread.currentThread() != overtaken) overtaken.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }*/
        } else if (overtaken instanceof TtyrecDecoder && backportDecode != null &&
                   overtaken != backportDecode) {
            if (debug) {
                System.err.println("Subsuming decode " + leadingEdgeDecode +
                        leadingEdgeDecode.hashCode());
            }
            synchronized (this) {
                if (leadingEdgeDecode == overtaken || leadingEdgeDecode == null)
                    leadingEdgeDecode = backportDecode;
                else
                    return;
            }
            overtaken.stopWorking();
            synchronized(this) {
                if(backportDecode == leadingEdgeDecode)
                    backportDecode = null;
            }
            try {
                leadingEdgeDecode.setPriority(MIN_PRIORITY);
            } catch (NullPointerException x) {}
/*            try {
                if(Thread.currentThread() != overtaken) overtaken.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }*/
        } // otherwise two threads probably subsumed each other
    }

    /**
     * Adds a listener for progress made on analysis. The listener is called
     * at most ten times per second, and on the Swing event thread; however,
     * all progress will be notified within 100ms.
     * @param l The listener to add.
     */
    public synchronized void addAnalysisListener(ProgressListener l) {
        analysisListeners.add(l);
    }

    /**
     * Adds a listener for progress made on decoding. The listener is called
     * at most ten times per second, and on the Swing event thread; however,
     * all progress will be notified within 100ms.
     * @param l The listener to add.
     */
    public synchronized void addDecodeListener(ProgressListener l) {
        decodeListeners.add(l);
    }

    /**
     * Adds a listener for progress made on reading the file. The listener is
     * called at most ten times per second, and on the Swing event thread;
     * however, all progress will be notified within 100ms.
     * @param l The listener to add.
     */
    public synchronized void addReadListener(ProgressListener l) {
        readListeners.add(l);
    }

    /**
     * If called by a backport worker, subsumes the matching leading-edge worker
     * if the backport worker has matched or overtaken its progress. If called
     * by a leading-edge worker, notifies ProgressListeners of progress.
     * @param worker The worker who called this method.
     */
    public synchronized void progressMade(TtyrecWorker worker) {
        if (worker instanceof TtyrecAnalyzer) analyzeEventHappened = true;
        if (worker instanceof TtyrecDecoder) decodeEventHappened = true;
        try {
            long leap = leadingEdgeAnalyze.getProgress();
            long bap = backportAnalyze.getProgress();
            if (leap <= bap) subsumeWorker(leadingEdgeAnalyze);
        } catch (NullPointerException x) {}
        try {
            long ledp = leadingEdgeDecode.getProgress();
            long bdp = backportDecode.getProgress();
            if (ledp <= bdp) subsumeWorker(leadingEdgeDecode);
        } catch (NullPointerException x) {}
    }

    private synchronized void firePendingEvents() {
        if (analyzeEventHappened) {
            for (ProgressListener l : analysisListeners) {
                l.progressMade();
            }
        }
        if (decodeEventHappened) {
            for (ProgressListener l : decodeListeners) {
                l.progressMade();
            }
        }
        if (readEventHappened) {
            for (ProgressListener l : readListeners) {
                l.progressMade();
            }
        }
        analyzeEventHappened = false;
        decodeEventHappened = false;
        setReadEventHappened(false);
    }

    /**
     * Returns the amount of leading-edge progress on analysis.
     * @return The amount of progress, in bytes.
     */
    public long analysisProgress() {
        try {
            return leadingEdgeAnalyze.getProgress();
        } catch (NullPointerException x) {
            return 0;
        }
    }

    /**
     * Returns the amount of leading-edge progress on decoding.
     * @return The amount of progress, in frames.
     */
    public int decodeProgress() {
        try {
            return (int) leadingEdgeDecode.getProgress();
        } catch (NullPointerException x) {
            return 0;
        }
    }

    /**
     * Returns the amount of backport progress on analysis. The amount
     * of leading-edge progress is returned if there is no backport
     * worker.
     * @return The amount of progress, in bytes.
     */
    public long backportAnalysisProgress() {
        try {
            return backportAnalyze.getProgress();
        } catch (NullPointerException x) {
            return analysisProgress();
        }
    }

    /**
     * Returns the amount of backport progress on decoding. The amount
     * of leading-edge progress is returned if there is no backport
     * worker.
     * @return The amount of progress, in frames.
     */
    public int backportDecodeProgress() {
        try {
            return (int) backportDecode.getProgress();
        } catch (NullPointerException x) {
            return decodeProgress();
        }
    }

    /**
     * Creates a backport worker of the same type as an existing worker.
     * @param old A worker of the same type as the backport worker to create.
     * @param backport The backport worker to create.
     */
    public void newBackportWorkerOfType(TtyrecWorker old, TtyrecWorker backport) {
        if (old instanceof TtyrecAnalyzer) {
            newBackportAnalyzeWorker((TtyrecAnalyzer) backport);
        }
        if (old instanceof TtyrecDecoder) {
            newBackportDecodeWorker((TtyrecDecoder) backport);
        }
    }

    /**
     * Creates and starts running an analyze backport worker. Then
     * creates a backport decode worker to update the VDU buffers
     * with output from the new analyze backport worker. Unless called
     * by the old analyze backport worker itself, also waits for the old
     * analyze backport worker to die.
     * @param backport The analyze backport worker to create.
     */
    public void newBackportAnalyzeWorker(TtyrecAnalyzer backport) {
        if (debug) {
            System.err.println("Setting analyze backport " + backport + backport.hashCode());
        }
        TtyrecWorker oldBackport = backportAnalyze;
        if (oldBackport != null) {
            oldBackport.stopWorking();
        }
        backportAnalyze = backport;
        try {
            if (oldBackport != null && Thread.currentThread() != oldBackport)
                oldBackport.join();
        } catch(InterruptedException x) {Thread.currentThread().interrupt();}
        backport.start();
        backport.resumeWorking();
        repeatCurrentDecodeWorker();
    }

    /**
     * Creates and starts running a decode backport worker. Unless called
     * by the old decode backport worker itself, also waits for the old
     * decode backport worker to die.
     * @param backport The decode backport worker to create.
     */
    public void newBackportDecodeWorker(TtyrecDecoder backport) {
        if (debug) {
            System.err.println("Setting decode backport " + backport + backport.hashCode());
        }
        TtyrecWorker oldBackport = backportDecode;
        if (oldBackport != null) {
            oldBackport.stopWorking();
        }
        backportDecode = backport;
        try {
            if (oldBackport != null && Thread.currentThread() != oldBackport)
                oldBackport.join();
        } catch(InterruptedException x) {Thread.currentThread().interrupt();}
        backport.start();
        backport.resumeWorking();
    }

    /**
     * Starts a new backport decode worker that's identical to the current
     * backport decode worker (or leading edge decode worker if there isn't
     * a current backport decode worker), except that it's starting from the
     * start and thus may incorporate any new analyzer information or decoder
     * settings.
     */
    public void repeatCurrentDecodeWorker() {
        newBackportDecodeWorker(
                (backportDecode == null ? leadingEdgeDecode : backportDecode).
                cloneWithAnalyzerSequence(getAnalyzerSequenceNumber()));
    }

    /**
     * Starts a new backport decode worker from scratch.
     */
    public void resetDecodeWorker() {
        newBackportDecodeWorker(new TtyrecDecoder(this,
                getNextSequenceNumber(), 24, 80));
    }

    /**
     * Gets the ttyrec that this source is creating.
     * @return The ttyrec.
     */
    public Ttyrec getTtyrec() {
        return rec;
    }

    /**
     * Gets the bytestream that this source is creating.
     * @return The bytestream.
     */
    public ByteChunkList getBytestream() {
        return bytestream;
    }

    /**
     * Returns a sequence number higher than any used so far, suitable for
     * using as the sequence number of a new worker.
     * @return The next available sequence number.
     */
    synchronized public int getNextSequenceNumber() {
        return nextSequenceNumber++;
    }

    /**
     * Returns the sequence number of the currently newest analyzer; that
     * is, the backport analyzer if it exists, or otherwise the leading
     * edge analyzer. This is important, because decoders should try to
     * avoid overtaking frames created by that analyzer, in order to
     * ensure that corrections in the analysis extend to corrections in
     * the decoding.
     * @return The sequence number of the analyzer.
     */
    synchronized public int getAnalyzerSequenceNumber() {
        if (backportAnalyze != null) {
            return backportAnalyze.getSequenceNumber();
        }
        if (leadingEdgeAnalyze == null) return nextSequenceNumber++;
        return leadingEdgeAnalyze.getSequenceNumber();
    }

    /**
     * Returns whether another thread is doing the same job as the given
     * thread (e.g. the given thread is a leading-edge decode worker and
     * there's a backport decode worker in existence too)
     * @param worker A backport or decode worker to check.
     * @return True if there's another worker of the same type.
     */
    boolean anotherThread(TtyrecWorker worker) {
        if (worker == leadingEdgeAnalyze) {
            return backportAnalyze != null;
        }
        if (worker == leadingEdgeDecode) {
            return backportDecode != null;
        }
        return true;
    }

    /**
     * Gets the current backport analyzer.
     * @return The current backport analyzer.
     */
    public TtyrecAnalyzer getBackportAnalyze() {
        return backportAnalyze;
    }

    /**
     * Gets the current backport decoder.
     * @return The current backport decoder.
     */
    public TtyrecDecoder getBackportDecode() {
        return backportDecode;
    }

    /**
     * Gets the current leading-edge analyzer.
     * @return The current leading-edge analyzer.
     */
    public TtyrecAnalyzer getLeadingEdgeAnalyze() {
        return leadingEdgeAnalyze;
    }

    /**
     * Gets the current leading-edge decoder.
     * @return The current leading-edge decoder.
     */
    public TtyrecDecoder getLeadingEdgeDecode() {
        return leadingEdgeDecode;
    }

    void setWantedFrame(int i) {
        rec.setWantedFrame(i);
    }

    /**
     * @param readEventHappened the readEventHappened to set
     */
    protected void setReadEventHappened(boolean readEventHappened) {
        this.readEventHappened = readEventHappened;
    }

    /**
     * Tells this InputSource to forcibly stop any I/O actions it's currently
     * performing, in preparation to shut down. This can only sanely be called
     * from a different thread to the source itself.
     */
    protected abstract void cancelIO();
}
