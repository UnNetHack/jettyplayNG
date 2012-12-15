/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jettyplay;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeListener;

/**
 * A class that aids in the construction of user interfaces, by reducing the
 * boilerplate necessary.
 * @author ais523
 */
public class UIBuilder {
    private final boolean focusable;
    private List<Component> enablableComponents;
    
    /**
     * Construct a new UI builder object.
     * @param focusable If false, nothing constructed via this UI builder will
     * be focusable. (Otherwise, default focusability is used.)
     */
    public UIBuilder(boolean focusable) {
        this.focusable = focusable;
        enablableComponents = new ArrayList<>();
    }
    
    private void connectComponents(Component c,
            Container parent, String position) {
        if (!focusable) c.setFocusable(false);
        if (parent != null) {
            if (position != null)
                parent.add(c,position);
            else
                parent.add(c);
        }
    }
    
    /**
     * Adds a new JPanel to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param position The position to attach the component on the parent. Can
     * be null.
     * @return The new component.
     */
    public JPanel addJPanel(Container parent, String position) {
        JPanel rv = new JPanel();
        rv.setLayout(new BorderLayout());
        connectComponents(rv, parent, position);
        return rv;
    }

    /**
     * Adds a new JSeparator to the UI hierarchy. For a toolbar, adds a
     * JToolBar.Separator instead.
     * @param parent The parent to attach the component to. Can be null.
     */
    public void addJSeparator(Container parent) {
        Component rv = (parent instanceof JToolBar ?
                new JToolBar.Separator() : new JSeparator());
        connectComponents(rv, parent, null);
    }

    /**
     * Adds a new JMenu to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param mnemonic The underlined character in the menu name.
     * @param text The user-visible name of the menu.
     * @return The new component.
     */
    public JMenu addJMenu(Container parent, char mnemonic, String text) {
        JMenu rv = new JMenu();
        rv.setMnemonic(mnemonic);
        rv.setText(text);
        connectComponents(rv, parent, null);
        return rv;
    }

    /**
     * Adds a new JMenuItem to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param mnemonic The underlined character in the menu item name.
     * @param text The user-visible name of the menu item.
     * @param accelerator A string representing the keystroke that accesses
     * this menu item even if the menu isn't open, in the format used by
     * javax.swing.KeyStroke.getKeyStroke(String). Can be null.
     * @param enablable True if this menu item should be mass-enabled /
     * mass-disabled via massSetEnabled.
     * @param listener The listener for selection of the menu item.
     * @return The new component.
     * @see javax.swing.KeyStroke#getKeyStroke(java.lang.String) getKeyStroke
     */
    public JMenuItem addJMenuItem(Container parent, char mnemonic, String text,
            String accelerator, boolean enablable, ActionListener listener) {
        JMenuItem rv = new JMenuItem();
        rv.setMnemonic(mnemonic);
        rv.setText(text);
        if (accelerator != null)
            rv.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        rv.addActionListener(listener);
        connectComponents(rv, parent, null);
        if (enablable)
            enablableComponents.add(rv);
        return rv;
    }

    /**
     * Adds a new JCheckBoxMenuItem to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param mnemonic The underlined character in the menu item name.
     * @param text The user-visible name of the menu item.
     * @param accelerator A string representing the keystroke that accesses
     * this menu item even if the menu isn't open, in the format used by
     * javax.swing.KeyStroke.getKeyStroke(String). Can be null.
     * @param enablable True if this menu item should be mass-enabled /
     * mass-disabled via massSetEnabled.
     * @param listener The listener for changes to the menu item state.
     * @return The new component.
     * @see javax.swing.KeyStroke#getKeyStroke(java.lang.String) getKeyStroke
     */
    public JCheckBoxMenuItem addJCheckBoxMenuItem(Container parent,
            char mnemonic, String text,
            String accelerator, boolean enablable, ChangeListener listener) {
        JCheckBoxMenuItem rv = new JCheckBoxMenuItem();
        rv.setMnemonic(mnemonic);
        rv.setText(text);
        if (accelerator != null)
            rv.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        rv.addChangeListener(listener);
        connectComponents(rv, parent, null);
        if (enablable)
            enablableComponents.add(rv);
        return rv;
    }

    /**
     * Adds a new JRadioButtonMenuItem to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param mnemonic The underlined character in the menu item name.
     * @param text The user-visible name of the menu item.
     * @param accelerator A string representing the keystroke that accesses
     * this menu item even if the menu isn't open, in the format used by
     * javax.swing.KeyStroke.getKeyStroke(String). Can be null.
     * @param enablable True if this menu item should be mass-enabled /
     * mass-disabled via massSetEnabled.
     * @param listener The listener for changes to the menu item state.
     * @return The new component.
     * @see javax.swing.KeyStroke#getKeyStroke(java.lang.String) getKeyStroke
     */
    public JRadioButtonMenuItem addJRadioButtonMenuItem(Container parent,
            char mnemonic, String text,
            String accelerator, boolean enablable, ChangeListener listener) {
        JRadioButtonMenuItem rv = new JRadioButtonMenuItem();
        rv.setMnemonic(mnemonic);
        rv.setText(text);
        if (accelerator != null)
            rv.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        rv.addChangeListener(listener);
        connectComponents(rv, parent, null);
        if (enablable)
            enablableComponents.add(rv);
        return rv;
    }

    
    /**
     * Adds a new JToolBar to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param position The position to attach the component on the parent. Can
     * be null.
     * @return The new component.
     */
    public JToolBar addJToolBar(Container parent, String position) {
        JToolBar rv = new JToolBar();
        rv.setRollover(true);
        connectComponents(rv, parent, position);
        return rv;
    }
    
    /**
     * Adds a new JSlider to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param position The position to attach the component on the parent. Can
     * be null.
     * @param tooltip The tooltip for the slider.
     * @param listener The listener for changes to the slider.
     * @return The new component.
     */
    public JSlider addJSlider(Container parent, String position,
            String tooltip, ChangeListener listener) {
        JSlider rv = new JSlider();
        rv.addChangeListener(listener);
        rv.setToolTipText(tooltip);
        connectComponents(rv, parent, position);
        enablableComponents.add(rv);
        return rv;
    }

    /**
     * Adds a new JButton to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param tooltip The tooltip for the button.
     * @param iconFilename The filename of the icon (which will be searched
     * for in /jettyplay/resources/).
     * @param listener The listener for clicks on the button.
     * @return The new component.
     */
    public JButton addJButton(Container parent, String tooltip,
            String iconFilename, ActionListener listener) {
        JButton rv = new JButton();
        rv.addActionListener(listener);
        rv.setToolTipText(tooltip);
        rv.setHorizontalTextPosition(SwingConstants.CENTER);
        rv.setVerticalTextPosition(SwingConstants.BOTTOM);
        rv.setIcon(new ImageIcon(
                getClass().getResource("/jettyplay/resources/"+iconFilename)));
        connectComponents(rv, parent, null);
        enablableComponents.add(rv);
        return rv;
    }

    /**
     * Adds a new JToggleButton to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param tooltip The tooltip for the button.
     * @param iconFilename The filename of the icon (which will be searched
     * for in /jettyplay/resources/).
     * @param enablable True if this button should be mass-enabled /
     * mass-disabled via massSetEnabled.
     * @param listener The listener for changes to the button.
     * @return The new component.
     */
    public JToggleButton addJToggleButton(Container parent, String tooltip,
            String iconFilename, boolean enablable, ChangeListener listener) {
        JToggleButton rv = new JToggleButton();
        rv.addChangeListener(listener);
        rv.setToolTipText(tooltip);
        rv.setHorizontalTextPosition(SwingConstants.CENTER);
        rv.setVerticalTextPosition(SwingConstants.BOTTOM);
        rv.setIcon(new ImageIcon(
                getClass().getResource("/jettyplay/resources/"+iconFilename)));
        connectComponents(rv, parent, null);
        if (enablable)
            enablableComponents.add(rv);
        return rv;
    }
    
    /**
     * Adds a new JLabel to the UI hierarchy.
     * @param parent The parent to attach the component to. Can be null.
     * @param position The position to attach the component on the parent. Can
     * be null.
     * @param text The text of the label.
     * @param alignment The horizontal alignment of the label (one of the
     * SwingConstants.direction constants).
     * @return The new component.
     */
    public JLabel addJLabel(Container parent, String position,
                     String text, int alignment) {
        JLabel rv = new JLabel();
        rv.setText(text);
        rv.setHorizontalAlignment(alignment);
        connectComponents(rv, parent, position);
        enablableComponents.add(rv);
        return rv;
    }

    /**
     * Sets the enabled property of all the components this uiBuilder created
     * that have one (except for menu items set to not take part in mass sets
     * of enabled values).
     * @param enabled Whether to enable the components.
     */
    void massSetEnabled(boolean enabled) {
        for (Component c : enablableComponents)
            c.setEnabled(enabled);
    }

}
