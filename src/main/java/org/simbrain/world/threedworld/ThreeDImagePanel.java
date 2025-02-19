package org.simbrain.world.threedworld;

import org.simbrain.world.imageworld.ImageSource;

import javax.swing.*;
import java.awt.*;

/**
 * ImagePanel is a resizable canvas for displaying images from an ImageSource.
 *
 * @author Tim Shea
 */
public class ThreeDImagePanel extends JPanel {
    private static final long serialVersionUID = 2582113543119990412L;

    private ImageSource nextSource;
    private ImageSource currentSource;
    private boolean destroyNeeded = false;

    /**
     * Construct a new ImagePanel.
     */
    public ThreeDImagePanel() {
        super();
        setFocusable(true);
        repaint();
    }

    /**
     * @return Get the source of images for this panel.
     */
    public ImageSource getImageSource() {
        return currentSource;
    }

    /**
     * Assign the source of the images for this panel.
     *
     * @param value The new source to use.
     */
    public void setImageSource(ImageSource value) {
        if (currentSource == null) {
            currentSource = value;
            nextSource = value;
            // currentSource.getEvents().onResize(this::onResize);
            currentSource.getEvents().getImageUpdate().on(this::onImageUpdate);
        } else {
            nextSource = value;
        }
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentSource == null) {
            return;
        }
        if (currentSource.getCurrentImage() != null) {
            g.drawImage(currentSource.getCurrentImage(), 0, 0, getWidth(), getHeight(), this);
        }
    }

    /**
     * Set a flag to destroy this ImagePanel on the next update.
     */
    public void destroy() {
        if (currentSource != null) {
            destroyNeeded = true;
        }
    }

    public void onImageUpdate() {
        if (destroyNeeded) {
            // currentSource.removeListener(this);
            currentSource = null;
            return;
        }
        if (nextSource != currentSource) {
            // currentSource.removeListener(this);
            currentSource = nextSource;
            //currentSource.getEvents().onResize(this::onResize);
            currentSource.getEvents().getImageUpdate().on(this::onImageUpdate);
        }
        if (currentSource.isEnabled()) {
            repaint();
        }
    }


}
