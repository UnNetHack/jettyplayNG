/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package jettyplay;

/**
 * An interface that specifies that an implementing class can be
 * notified that progress was made with some operation.
 * @author ais523
 */
public interface ProgressListener {
    /**
     * Called whenever progress is made.
     */
    public void progressMade();
}
