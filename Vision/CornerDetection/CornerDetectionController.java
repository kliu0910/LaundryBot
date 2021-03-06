package Vision.CornerDetection;

import DataTypes.*;

import java.io.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.awt.geom.*;

import april.jcam.ImageSource;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jcam.ImageSourceFile;
import april.jmat.Matrix;



public class CornerDetectionController {
   
	// const
	private static final int threshold = 20;
 
    // args
    private ImageSource		    selectedImageSource;
    private CornerDetectionFrame      frame;
    private String		    selectedCameraURL;
    private Thread		    imageThread;
    private BufferedImage 	    selectedImage;
    
    
    // CONSTRUCTOR
    public CornerDetectionController(CornerDetectionFrame frame) {
        
        this.frame = frame;
        frame.setSize(1024, 768);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        // add action event listeners
        frame.getChooseCameraSourceButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				CornerDetectionController.this.chooseCameraSourceAction();
			}
		});
        
        
        frame.getChooseImageButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
			    FileNameExtensionFilter filter = new FileNameExtensionFilter(
                                                                             "Images", "jpg", "gif", "png");
			    chooser.setFileFilter(filter);
			    int returnVal = chooser.showOpenDialog(CornerDetectionController.this.frame);
			    if(returnVal == JFileChooser.APPROVE_OPTION) {
			    	selectedImage = imageFromFile(chooser.getSelectedFile());
			    	selectedImageSource = null;
			    	if ( selectedImage != null ) {
			    		startImage();
			    	}
			    }
			}
		});
        
        
        // toggle click action
        frame.getCenterImage().addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me) {
				CornerDetectionController.this.didClickMouse(me);
			}
		});
        
        
    }
    
    // PROTECTED CLASS METHODS
    protected static BufferedImage imageFromFile(File file) {
		BufferedImage in;
		try {
			in = ImageIO.read(file);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Error Reading from File");
			return null;
		}
		BufferedImage newImage = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = newImage.createGraphics();
		g.drawImage(in, 0, 0, null);
		g.dispose();
		return newImage;
	}
    
    protected void didClickMouse(MouseEvent me) {
        // toggle click action
        
	}
    
	protected void chooseCameraSourceAction() {
		// retrieve camera URLS
		List<String> URLS = ImageSource.getCameraURLs();
        
		// test for no cameras availablImageSourceFormate
		if (URLS.size() == 0) {
			JOptionPane.showMessageDialog(
                                          this.getFrame(),
                                          "There are no camera urls available",
                                          "Error encountered",
                                          JOptionPane.OK_OPTION
                                          );
			return;
		}
        
		final String initial = (this.selectedCameraURL == null) ?
        URLS.get(0) : this.selectedCameraURL;
        
		final String option = (String)JOptionPane.showInputDialog(
                                                                  this.getFrame(),
                                                                  "Select a camera source from the available URLs below:",
                                                                  "Select Source",
                                                                  JOptionPane.PLAIN_MESSAGE,
                                                                  null,
                                                                  URLS.toArray(),
                                                                  initial
                                                                  );
        
		if ( option != null ) {
			// the selected URL
			this.selectedCameraURL = option;
			this.startCamera();
		}
	}
    
    protected void startImage() {
		if ( this.imageThread != null ) {
			System.err.println("Warning, camera already running");
			return;
		}
        
		this.imageThread = new Thread(new Runnable() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						BufferedImage out = CornerDetectionController.this.processImage(selectedImage);
                        CornerDetectionController.this.getFrame().getCenterImage().setImage(out);
					}
				});
			}
		});
		this.imageThread.start();
	}
    
    protected void startCamera() {
		
		if ( this.imageThread != null ) {
			System.err.println("Warning, camera already running");
			return;
		}
        
		try {
			this.selectedImageSource = ImageSource.make(this.selectedCameraURL);
		}
		catch(IOException e) {
			// do nothing
			System.err.println(e);
			this.selectedImageSource = null;
			return;
		}
        
		// BUILD NEW THREAD
		this.selectedImageSource.start();
		this.imageThread = new Thread(new Runnable() {

			private long timeOfLastFrame = 0;

			@Override
            public void run() {
                ImageSourceFormat fmt = CornerDetectionController.this.selectedImageSource.getCurrentFormat();
                while (true) {
                    // get buffer with image data from next frame
                    byte buf[] = CornerDetectionController.this.selectedImageSource.getFrame().data;
                    
                    // if next frame is not ready, buffer will be null
                    // continue and keep trying
                    if (buf == null) {
                        System.err.println("Buffer is null");
                        continue;
                    }
                    
                    // created buffered image from image data
                    final BufferedImage im = ImageConvert.convertToImage(
                                                                         fmt.format,
                                                                         fmt.width,
                                                                         fmt.height,
                                                                         buf
                                                                         );
                    
                    // set image on main window
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {

							System.out.println("Time between frames: " + (System.currentTimeMillis() - timeOfLastFrame));
							timeOfLastFrame = System.currentTimeMillis();

                            BufferedImage out = CornerDetectionController.this.processImage(im);
                            CornerDetectionController.this.getFrame().getCenterImage().setImage(out);
                        }
                    });
                }
            }
        });
        this.imageThread.start();
    }
    
    // Image Processing
    protected BufferedImage processImage(BufferedImage im) {

        // run corner detection
        CornerDetectionDetector cdd = new CornerDetectionDetector();
        ArrayList<FeaturePoint> c = new ArrayList<FeaturePoint>();
        c = cdd.FASTDetection(im, threshold);
        
        // plot corners on image
        BufferedImage out = im;
		//System.out.println("New Image:");
        for (int i = 0; i < c.size(); i++) {
            FeaturePoint corner = c.get(i);
            //System.out.println("Corner: " + corner.x() + " " + corner.y());
            if (corner.x()-1 > 0 && corner.x()+1 < out.getWidth() && corner.y()-1 > 0 && corner.y()+1 < out.getHeight()) {
                for (int t = -1; t < 1; t++) {
					for (int r = -1; r < 1; r++) {
                    	out.setRGB(corner.x() + t, corner.y() + r, 0xff0000ff);
					}
                }
            }
        }
	
        
        return out;
    }
    
    
    // PUBLIC CLASS METHODS
    public CornerDetectionFrame getFrame() {
        return frame;
    }
    
}
