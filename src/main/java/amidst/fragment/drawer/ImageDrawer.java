package amidst.fragment.drawer;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.VolatileImage;

import amidst.documentation.AmidstThread;
import amidst.documentation.CalledOnlyBy;
import amidst.documentation.NotThreadSafe;
import amidst.fragment.Fragment;
import amidst.fragment.layer.LayerDeclaration;
import amidst.gui.main.viewer.Graphics2DAccelerationCounter;
import amidst.mojangapi.world.coordinates.Resolution;

@NotThreadSafe
public class ImageDrawer extends FragmentDrawer {
	private final Resolution resolution;
	private final Graphics2DAccelerationCounter accelerationCounter;
	
	private static final GraphicsConfiguration GC = GraphicsEnvironment.getLocalGraphicsEnvironment()
																	   .getDefaultScreenDevice()
																	   .getDefaultConfiguration();
	
	private VolatileImage tempImage;

	public ImageDrawer(
			LayerDeclaration declaration,
			Resolution resolution,
			Graphics2DAccelerationCounter accelerationCounter) {
		super(declaration);
		this.resolution = resolution;
		this.accelerationCounter = accelerationCounter;
	}

	
	/**
	 * When drawing with this class, we scale the image with a hybrid of
	 * both nearest neighbor and bilinear. As an example, lets say that
	 * the scale of the g2d is 3.2. We want to split the 3.2 into 3.0 and
	 * 0.2. The nearest neighbor algorithm then scales it up by 3.0 and
	 * stores it into a temporary image. Then, the bilinear algorithm
	 * scales up the temporary image by whatever it needs to get 3.0 to
	 * 3.2. In this case, it would be 1.066 repeating.
	 * 
	 */
	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	public void draw(Fragment fragment, Graphics2D g2d, float time) {
		int scale = resolution.getStep();
		g2d.scale(scale, scale);
		
		Object oldHint = g2d.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		
		Image image = fragment.getImage(declaration.getLayerId());
		
		double scaleX = g2d.getTransform().getScaleX(); // This could be the Y value, but it shouldn't matter because they should be equal
		if (scaleX > 2.0) { // this algorithm has no benefit if the scale is less than 2
			double nearestScale = scaleX - (scaleX % 1.0d); // scale value for nearest neighbor pass
			double bilinearScale = scaleX / nearestScale; // scale value for bilinear pass
			
			int nearestSize = (int) (image.getWidth(null) * nearestScale);
			// recreate volatile image if it's been messed up in some way
			if (tempImage == null || tempImage.getWidth() != nearestSize || tempImage.validate(GC) == VolatileImage.IMAGE_INCOMPATIBLE) {
				tempImage = GC.createCompatibleVolatileImage(nearestSize, nearestSize, Transparency.TRANSLUCENT);
			}
			
			// create a g2d for the temporary image and scales the original with nearest neighbor into it
			Graphics2D g2dTemp = tempImage.createGraphics();
			g2dTemp.setComposite(AlphaComposite.Src); // fixes the transparency being wrong
			g2dTemp.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g2dTemp.scale(nearestScale, nearestScale);
			accelerationCounter.log(image);
			g2dTemp.drawImage(image, 0, 0, null); 
			g2dTemp.dispose();
			
			// set them main g2d to be ready to apply the bilinear scaling
			// for some reason, g2d doesn't let us directly change the transform's variables easily
			// this means that we have to multiply our bilinear scale by the inverse of the current scale to cancel out the current scale
			double bilinearScaleModified = (1 / scaleX) * bilinearScale;
			g2d.scale(bilinearScaleModified, bilinearScaleModified);
			
			image = tempImage;
		}
		
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				
		accelerationCounter.log(image);
		g2d.drawImage(image, 0, 0, null);
		
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
	}
	
}
