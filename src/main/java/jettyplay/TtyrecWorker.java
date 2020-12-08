/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

import java.util.Date;

/**
 * Something capable of analyzing a Ttyrec in the background. This
 * needs to be capable of reporting its progress in bytes, suspending
 * and stopping. They should each be associated with a particular
 * Ttyrec object, which they modify. This interface is for all workers
 * which parse or decode ttyrecs, but not for classes that simply put
 * data into a bytestream (which in Jettyplay, run to completion or
 * are terminated if they are ever started, to prevent things like
 * network timeouts). In order to accomplish this, the run() method
 * should check for isPaused and isHalted each time round its main
 * loop, terminating if isHalted is set, and otherwise calling
 * hibernate() if isPaused is set. Note that TtyrecWorkers are
 * created paused, and so should check for this before running a
 * single iteration of their main loop.
 * <p>
 * Nothing but the TtyrecWorker itself should wait on its monitor,
 * as otherwise there may be deadlocks on the worker, and spurious
 * wakeups on whatever else was waiting there.
 * @author ais523
 */
public abstract class TtyrecWorker extends Thread {

    /**
     * The TtyrecSource this TtyrecWorker is working for. The value of this
     * can be inspected to find the ttyrec to work on.
     */
    final protected TtyrecSource workingFor;
    /**
     * Creates this TtyrecWorker in a non-running state, marked as paused
     * and not halted.
     * @param workingFor The TtyrecSource this Worker should be attached to.
     * @param sequenceNumber A number greater than that assigned to all previous TtyrecWorkers performing the same job.
     * @param name The name of the thread that represents this worker..
     */
    public TtyrecWorker(TtyrecSource workingFor, int sequenceNumber, String name) {
        super(name);
        this.workingFor = workingFor;
        isPaused = true;
        isHalted = false;
        this.sequenceNumber = sequenceNumber;
        progress = 0;
        this.setPriority(MAX_PRIORITY);
    }

    /**
     * A boolean that specifies whether this worker is currently paused; if
     * this is ever set, the worker should, at a convenient moment, block and
     * wait on its own monitor for this boolean to be cleared again.
     */
    volatile protected boolean isPaused;
    /**
     * A boolean that specifies whether this worker is currently halted; if
     * this is ever set, the worker should exit its main loop after its current
     * iteration.
     */
    volatile protected boolean isHalted;
    /**
     * Tells this TtyrecWorker to pause its work at a convenient
     * moment. A TtyrecWorker should not pause itself like this;
     * if it needs to stop due to insufficient data, or whatever,
     * it should instead wait on the monitor of a different object.
     */
    public synchronized void pauseWorking() {
        isPaused = true;
    }
    /**
     * Tells this TtyrecWorker to stop its work at a convenient
     * moment; after calling this method, it should stop its work
     * at the end of the main loop. Unlike pauseWorking(), a
     * TtyrecWorker can stop itself by calling this method if it
     * wishes (say, in the case of an analyzer which read a token
     * in an input format that definitively marked the end of the
     * recording, or a leading-edge analyzer which realises its
     * assumptions are sufficiently wrong that it cannot continue
     * working), although it can also do so simply by breaking
     * out of its main loop. This also interrupts this worker,
     * just in case it's waiting on something else's monitor.
     */
    public synchronized void stopWorking() {
        isHalted = true;
        if (isPaused) {
            isPaused = false;
            notify();
        } else
            this.interrupt();
    }
    /**
     * Tells this TtyrecWorker it can resume its work after being
     * paused. This should obviously not be called by the worker
     * itself, as doing so could not sensibly have any effect.
     */
    public synchronized void resumeWorking() {
        if (!isPaused) return;
        isPaused = false;
        notify();
    }
    /**
     * This method should be called by the TtyrecWorker itself at
     * a convenient moment (say, after an iteration of the main loop)
     * if isPaused is set, either directly or via continueMainLoop().
     * It does not return until the worker is unpaused or halted.
     */
    protected synchronized void hibernate() {
        while (isPaused && !isHalted) {
            try {
                wait();
            } catch (InterruptedException ex) {
                // Just check again. Interrupting us is kind-of pointless
                // because we should just be notified instead.
            }
        }
    }
    private long timeAtLastContinue = 0;
    /**
     * A convenience method. If the main loop of a TtyrecWorker is written
     * as while(continueMainLoop()) { ... }, it will automatically obey the
     * part of the TtyrecWorker concerned with pausing and halting; it
     * does not return if the TtyrecWorker is paused until it is unpaused
     * again (or halted, which unpauses it), and its return value indicates
     * whether to halt. It also delays a bit if the thread is set to run at
     * minimum priority and there's another worker doing the same job.
     * @return false if the TtyrecWorker has been halted, false otherwise.
     */
    protected boolean continueMainLoop() {
        hibernate();
        long talc = timeAtLastContinue;
        timeAtLastContinue = new Date().getTime();
        if (getPriority() == MIN_PRIORITY && workingFor.anotherThread(this)) {
            try {
                if (talc > 0)
                    Thread.sleep(timeAtLastContinue-talc);
                timeAtLastContinue = new Date().getTime();
            } catch(InterruptedException ex) {
            }
        }
        return !isHalted;
    }

    /**
     * An arbitrary number used to distinguish this worker from other workers.
     * Should probably not be changed after it is set the first time.
     */
    protected int sequenceNumber;
    private volatile long progress;
    /**
     * Gets this worker's sequence number. Each frame of the ttyrec has two
     * sequence numbers associated with it; the number of the TtyrecWorker
     * who analyzed it, and the number of the TtyrecWorker who decoded it.
     * TtyrecWorkers should not replace information in a frame if that
     * information's already been determined by a newer worker; instead,
     * they should call subsume() to tell their TtyrecSource to replace
     * them with the backport worker who overtook them. (A TtyrecWorker is
     * generally unaware as to whether it's a leading-edge or backport
     * worker, but in that situation it's kind-of obvious.)
     * @return This worker's sequence number.
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }
    
    private boolean subsumed = false;
    /**
     * Queries whether this worker has been replaced by another worker with
     * better assumptions.
     * @return Whether this worker is now obsolete.
     */
    public boolean isSubsumed() {
        return subsumed;
    }
    /**
     * Sets whether this worker has been replaced by another worker with better
     * assumptions.
     * @param subsumed Whether this worker should now be considered obsolete.
     */
    public void setSubsumed(boolean subsumed) {
        this.subsumed = subsumed;
    }
    /**
     * Tells the TtyrecSource that this worker is working for that it
     * should be replaced with the backport worker that overtook it. This
     * method should be called if, and only if, this TtyrecWorker realises
     * it has been overtaken, and only by the TtyrecWorker itself (which
     * will be halted as a side-effect).
     */
    protected void subsume() {
        workingFor.subsumeWorker(this);
    }
    /**
     * Tells the TtyrecSource that this worker has made a specified amount
     * of progress; this is in bytes for analyze workers, and frames for
     * decode workers. This worker will subsume a leading-edge worker that
     * has made no more progress than it, if a backport worker; a leading-
     * edge worker will need to make constant progress notifications to
     * avoid being subsumed.
     * <p>
     * Implementing classes should call this method to set progress, rather
     * than setting a field directly.
     * @param progress Progress made, in bytes or frames.
     */
    protected void setProgress(long progress) {
        this.progress = progress;
        workingFor.progressMade(this);
    }
    /**
     * Returns the amount of progress this worker has made, in bytes for
     * analyze workers or frames for decode workers.
     * @return Progress made, in bytes or frames.
     */
    public long getProgress() {
        return progress;
    }

    /**
     * Creates a backport worker doing the same job as this worker. This
     * should be called by a worker that realises it's operating under
     * incorrect assumptions, such as an analzyer that finds a sequence
     * of bytes that's impossible in the format it's meant to analyze.
     * @param worker The backport worker to create.
     */
    protected void backport(TtyrecWorker worker) {
        workingFor.newBackportWorkerOfType(this,worker);
    }

    /**
     * Compares this worker to an arbitrary object.
     * @param obj The object to compare to.
     * @return True if the other object is this worker; false if it's a
     * different worker, or not a TtyrecWorker at all.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TtyrecWorker other = (TtyrecWorker) obj;
        if (this.sequenceNumber != other.sequenceNumber) {
            return false;
        }
        return true;
    }
    /**
     * Returns this worker's sequence number.
     * @return This worker's sequence number.
     */
    @Override
    public int hashCode() {
        return this.sequenceNumber;
    }

}
