/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

/**
 * An exception thrown while trying to parse a ttyrec.
 * Identical to Exception in every way, except that it can be
 * distinguished from Exception.
 * @author ais523
 */
class TtyrecException extends Exception {
    /**
     * Creates a new ttyrec exception.
     * @param Message Why the exception was thrown
     */
    public TtyrecException(String Message) {
        super(Message);
    }
}
