/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SaveAsVideoDialog.java
 *
 * Created on 17-Dec-2012, 05:21:28
 */
package jettyplay;

import java.awt.RenderingHints;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 *
 * @author ais523
 */
@SuppressWarnings("serial")
public class SaveAsVideoDialog extends JDialog implements ProgressListener {
    private final MainFrame parent;
    private final Ttyrec ttyrec;

    private VideoContainer encodingContainer;
    
    /** Creates a new form to save a ttyrec as video
     * @param parent The MainFrame that created this dialog box.
     * @param ttyrec The ttyrec to save as a video. 
     */
    public SaveAsVideoDialog(MainFrame parent, Ttyrec ttyrec) {
        super(parent, true);
        this.parent = parent;
        this.ttyrec = ttyrec;
        this.encodingContainer = null;
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        speedButtonGroup = new javax.swing.ButtonGroup();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        containerComboBox = new javax.swing.JComboBox();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        codecComboBox = new javax.swing.JComboBox();
        linearSpeedButton = new javax.swing.JRadioButton();
        logSpeedButton = new javax.swing.JRadioButton();
        fixedSpeedButton = new javax.swing.JRadioButton();
        fixedSpeedSpinner = new javax.swing.JSpinner();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        antialiasingCheckBox = new javax.swing.JCheckBox();
        javax.swing.JPanel jPanel1 = new javax.swing.JPanel();
        javax.swing.JButton cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        progressBar = new javax.swing.JProgressBar();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        sizeComboBox = new javax.swing.JComboBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Save as Video");
        getContentPane().setLayout(new java.awt.GridBagLayout());

        jLabel1.setLabelFor(containerComboBox);
        jLabel1.setText("Container:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        getContentPane().add(jLabel1, gridBagConstraints);

        containerComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "AVI" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(containerComboBox, gridBagConstraints);

        jLabel2.setLabelFor(codecComboBox);
        jLabel2.setText("Codec:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        getContentPane().add(jLabel2, gridBagConstraints);

        codecComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "ZMBV", "Uncompressed" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(codecComboBox, gridBagConstraints);

        speedButtonGroup.add(linearSpeedButton);
        linearSpeedButton.setSelected(true);
        linearSpeedButton.setText("Copy timings from ttyrec");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(linearSpeedButton, gridBagConstraints);

        speedButtonGroup.add(logSpeedButton);
        logSpeedButton.setText("Copy timings from ttyrec, except inactivity");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(logSpeedButton, gridBagConstraints);

        speedButtonGroup.add(fixedSpeedButton);
        fixedSpeedButton.setText("Use fixed framerate:");
        fixedSpeedButton.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                fixedSpeedButtonStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(fixedSpeedButton, gridBagConstraints);

        fixedSpeedSpinner.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), Double.valueOf(1.0d), null, Double.valueOf(1.0d)));
        fixedSpeedSpinner.setEnabled(false);
        fixedSpeedSpinner.setPreferredSize(new java.awt.Dimension(60, 26));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(fixedSpeedSpinner, gridBagConstraints);

        jLabel3.setLabelFor(fixedSpeedSpinner);
        jLabel3.setText("fps");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(jLabel3, gridBagConstraints);

        antialiasingCheckBox.setSelected(true);
        antialiasingCheckBox.setText("Antialiasing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(antialiasingCheckBox, gridBagConstraints);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        jPanel1.add(cancelButton);

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        jPanel1.add(okButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        getContentPane().add(jPanel1, gridBagConstraints);

        progressBar.setMaximum(ttyrec.getFrameCount());
        progressBar.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(progressBar, gridBagConstraints);

        jLabel4.setLabelFor(sizeComboBox);
        jLabel4.setText("Height:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        getContentPane().add(jLabel4, gridBagConstraints);

        sizeComboBox.setEditable(true);
        sizeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "480", "720", "1080" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        getContentPane().add(sizeComboBox, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void fixedSpeedButtonStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_fixedSpeedButtonStateChanged
        fixedSpeedSpinner.setEnabled(fixedSpeedButton.isSelected());
    }//GEN-LAST:event_fixedSpeedButtonStateChanged

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (encodingContainer != null) encodingContainer.cancelEncode();
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        int height;
        try {
            String s = (String)sizeComboBox.getModel().getSelectedItem();
            height = Integer.valueOf(s);
            if (height < 1) throw new NumberFormatException();
        } catch(NumberFormatException ex) {
            // Insist on having a numerical height...
            sizeComboBox.getModel().setSelectedItem("480");
            sizeComboBox.requestFocusInWindow();
            return;
        }
        containerComboBox.setEnabled(false);
        codecComboBox.setEnabled(false);
        linearSpeedButton.setEnabled(false);
        logSpeedButton.setEnabled(false);
        fixedSpeedButton.setEnabled(false);
        fixedSpeedSpinner.setEnabled(false);
        sizeComboBox.setEnabled(false);
        antialiasingCheckBox.setEnabled(false);
        okButton.setEnabled(false);
        progressBar.setEnabled(true);
        final VideoCodec[] codecs = {null,
            new RawVideoCodec(height, parent.getTerminalFont(),
                    antialiasingCheckBox.isSelected() ?
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON :
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)};
        final int fixedFramerate = (int)(double)(fixedSpeedSpinner.getModel()).getValue();
        
        encodingContainer = new AVIVideoContainer();
        encodingContainer.addProgressListener(this);

        final JDialog finalThis = this;
        
        Runnable videoEncodeThread = new Runnable() {

            public void run() {
                try {
                    ttyrec.encodeVideo(encodingContainer,
                            codecs[codecComboBox.getSelectedIndex()],
                            linearSpeedButton.isSelected()
                            ? new FrameTimeConvertor() {
                        
                        public double getFrameRate() {
                            return 60.0;
                        }
                        
                        public void resetConvertor() {
                        }
                        
                        public int convertFrameTime(double frameTime) {
                            return (int) (frameTime * 60);
                        }
                    } : logSpeedButton.isSelected()
                            ? new FrameTimeConvertor() {
                        
                        private double lastFrameTime = 0;
                        private double adjustedLastFrameTime = 0;
                        
                        public double getFrameRate() {
                            return 60.0;
                        }
                        
                        public void resetConvertor() {
                            lastFrameTime = 0;
                            adjustedLastFrameTime = 0;
                        }
                        
                        public int convertFrameTime(double frameTime) {
                            if (frameTime - lastFrameTime > 1) {
                                adjustedLastFrameTime +=
                                        1 + Math.log(frameTime - lastFrameTime);
                            } else {
                                adjustedLastFrameTime += frameTime - lastFrameTime;
                            }
                            lastFrameTime = frameTime;
                            return (int) (adjustedLastFrameTime * 60);
                            
                        }
                    } : new FrameTimeConvertor() {
                        
                        int frameNumber = 0;
                        
                        public double getFrameRate() {
                            return fixedFramerate;
                        }
                        
                        public void resetConvertor() {
                            frameNumber = 0;
                        }
                        
                        public int convertFrameTime(double frameTime) {
                            return frameNumber++;
                        }
                    });
                    JFileChooser jfc = new JFileChooser();
                    int rv = jfc.showSaveDialog(finalThis);
                    if (rv == JFileChooser.APPROVE_OPTION) {
                        File f = jfc.getSelectedFile();
                        try (OutputStream os = new FileOutputStream(f)) {
                            encodingContainer.outputEncode(os);
                        } catch(IOException ex) {
                            JOptionPane.showMessageDialog(finalThis,
                                    "Could not save file:" + ex.getLocalizedMessage(),
                                    "Save as Video", JOptionPane.ERROR_MESSAGE);
                        }
                        finalThis.dispose();
                    }
                } catch (CancellationException e) {
                    System.err.println("Cancelled!");
                    return;
                }
            }
        };
        new Thread(videoEncodeThread).start();
    }//GEN-LAST:event_okButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox antialiasingCheckBox;
    private javax.swing.JComboBox codecComboBox;
    private javax.swing.JComboBox containerComboBox;
    private javax.swing.JRadioButton fixedSpeedButton;
    private javax.swing.JSpinner fixedSpeedSpinner;
    private javax.swing.JRadioButton linearSpeedButton;
    private javax.swing.JRadioButton logSpeedButton;
    private javax.swing.JButton okButton;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JComboBox sizeComboBox;
    private javax.swing.ButtonGroup speedButtonGroup;
    // End of variables declaration//GEN-END:variables

    @Override
    public void progressMade() {
        /* This might be called from a weird thread (in fact, probably will
         * be), so we need to use invokeLater to get back to the Swing thread. */
        Runnable runnable = new Runnable() {
            public void run() {
                progressBar.setValue(encodingContainer.getFramesEncoded());
            }
        };
        SwingUtilities.invokeLater(runnable);
    }
}
