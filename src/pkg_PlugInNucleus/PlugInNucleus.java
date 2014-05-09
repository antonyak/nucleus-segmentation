package pkg_PlugInNucleus;

/*
 * Nucleus Classification PlugIn
 * CS74401 Image Processing
 * Dr. C.C. Lu
 * Spring 2014
 * 
 * Purpose:
 * 	Segment nuclei and nucleoli in a given stack of images
 * 
 * Authors:
 * 	Anton Yakymenko
 * 	Daniel Angelis
 */


import ij.*;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.ParticleAnalyzer;

public class PlugInNucleus implements PlugIn
{
	@Override
	public void run(String arg)
	{
		// Global settings
		double seed_min_cutoff;
		double seed_min_radius;
		double seed_gb_sigma; // Gaussian blur sigma
		double seed_max_noise;
		
		double segm_min_radius;
		double segm_fm_grey;	// fast marching grey threshold
		double segm_fm_dist; // fast marching distance threshold
		
		boolean segm_gauss;
		
		double bin_min;
		double bin_max;
		
		// Show dialog for settings
		GenericDialog gd = new GenericDialog("Nucleus Classification Settings");
		gd.addMessage("Finding Seed Points:");
		gd.addNumericField("Min intensity allowed", 128, 0);
		//gd.addMessage("(increase to lessen impact of very dark areas)");
		gd.addNumericField("Minimum filter radius", 10, 0);
		//gd.addMessage("(increase to avoid selecting smaller objects)");
		gd.addNumericField("Gaussian blur _sigma", 20.0, 1);
		//gd.addMessage("(increase to avoid finding minima in smaller objects)");
		gd.addNumericField("Find Maxima noise tolerance", 10.0, 1);
		//gd.addMessage("(decrease to find more seed points)");
		
		gd.addMessage("Nuclei Segmentation:");
		gd.addNumericField("Minimum filter radius", 3, 0);
		//gd.addMessage("(increase to thicken edges and avoid spilling but reduce accuracy)");
		gd.addNumericField("Fast marching _grey threshold", 50.00, 2);
		//gd.addMessage("(increase to select more area but increase chance of spilling)");
		gd.addNumericField("Fast marching _distance threshold", 0.01, 2);
		//gd.addMessage("(increase to allow only larger parts to be selected)");
		
		gd.addCheckbox("Perform 3D Gaussian Blur", true);
		
		gd.addMessage("Nucleoli Segmentation:");
		gd.addNumericField("Intensity threshold minimum", 60, 0);
		gd.addNumericField("Intensity threshold maximum", 100, 0);
		
		gd.showDialog();
		
		// Abort if user pressed Cancel
		if (gd.wasCanceled())
		{
			return;
		}
		
		// Get settings from the Dialog
		seed_min_cutoff = gd.getNextNumber();
		seed_min_radius = gd.getNextNumber();
		seed_gb_sigma = gd.getNextNumber();
		seed_max_noise = gd.getNextNumber();
		
		segm_min_radius = gd.getNextNumber();
		segm_fm_grey = gd.getNextNumber();
		segm_fm_dist = gd.getNextNumber();
		
		segm_gauss = gd.getNextBoolean();
		
		bin_min = gd.getNextNumber();
		bin_max = gd.getNextNumber();
		
		double pixelsPerSlice = 1;
		
		// Segmentation
		
		// Given ImagePlus (image or stack)
		ImagePlus img = WindowManager.getCurrentImage();
		// Temporary ImagePlus used to close images
		ImagePlus imgtmp;
		
		// Final results ImageStacks (must be same dimensions as starting image)
		// Made into ImagePlus in the end
		ImageStack stksegbin = new ImageStack(img.getWidth(), img.getHeight()); // Nucleus segmentation mask
		ImageStack stksegfin = new ImageStack(img.getWidth(), img.getHeight()); // Nucleus segmentation applied to original
		ImageStack stknucbin = new ImageStack(img.getWidth(), img.getHeight()); // Nucleoli segmentation mask
		ImageStack stknucfin = new ImageStack(img.getWidth(), img.getHeight()); // Nucleoli segmentation applied to original
		ImagePlus imgsegbin;
		ImagePlus imgsegfin;
		ImagePlus imgnucbin;
		ImagePlus imgnucfin;
		
		// Process slice-by-slice
		// Slices start at 1, not 0
		for(int s=1; s<=img.getStackSize(); s++)
		{
			IJ.log("Processing Slice " + s + " of " + img.getStackSize());
			
			// Just 1 Slice to operate on
			img.setSlice(s);
			ImagePlus imgslc = new ImagePlus(img.getTitle()+" - Slice "+s, img.getProcessor());
						
			// Find seed points for nuclei
			IJ.log("Finding Seed Points...");
			ImagePlus imgslcpts = imgslc.duplicate();
			IJ.run(imgslcpts, "Min...", "value="+seed_min_cutoff);
			IJ.run(imgslcpts, "Enhance Contrast...", "saturated=0 equalize");
			IJ.run(imgslcpts, "Minimum...", "radius="+seed_min_radius);
			IJ.run(imgslcpts, "Gaussian Blur...", "sigma="+seed_gb_sigma);
			IJ.run(imgslcpts, "Find Maxima...", "noise="+seed_max_noise+" output=[Point Selection]");		
			Roi roisp = imgslcpts.getRoi();
			
			ImagePlus imgslcseg = imgslc.duplicate();
			imgslcseg.setRoi(roisp, true);
			
			// Segment nuclei
			IJ.log("Finding Nuclei...");
			IJ.run(imgslcseg, "Enhance Contrast...", "saturated=0 equalize");
			IJ.run(imgslcseg, "Minimum...", "radius="+segm_min_radius);	
			IJ.run(imgslcseg, "Level Sets", "method=[Active Contours]"+
				" use_fast_marching"+
				" grey_value_threshold="+segm_fm_grey+
				" distance_threshold="+segm_fm_dist+
				" advection=2.20 propagation=1 curvature=1 grayscale=30 convergence=0.0050 region=outside");
			
			// Get the result of Level Sets
			// Window has a specific name
			ImagePlus imgslcsegbin = WindowManager.getImage("Segmentation of "+imgslcseg.getTitle());
			
			// Add segmentation binary mask image to the stack
			IJ.log("Done with Slice " + s + " of " + img.getStackSize());
			stksegbin.addSlice(imgslcsegbin.getProcessor());
			
			// Close both Level Sets images without saving
			imgtmp = WindowManager.getImage("Segmentation of "+imgslcseg.getTitle());
			imgtmp.changes = false;
			imgtmp.close();
			imgtmp = WindowManager.getImage("Segmentation progress of "+imgslcseg.getTitle());	
			imgtmp.changes = false;
			imgtmp.close();		
		}
		
		// Create nucleus segmentation binary mask image
		imgsegbin = new ImagePlus("Nucleus Segmentation Mask for " + img.getTitle(), stksegbin);
		
		// Clean up and get rid of overflows
		IJ.run(imgsegbin, "Invert", "stack");
		IJ.run(imgsegbin, "Options...", "black"); // set black background
		
		for(int i=0; i<segm_min_radius; i++)
			IJ.run(imgsegbin, "Dilate", "stack");
		
		IJ.run(imgsegbin, "Fill Holes", "stack");
		if (segm_gauss)
		{
			IJ.run(imgsegbin, "Gaussian Blur 3D...", "x=2 y=2 z=2");
			IJ.setThreshold(imgsegbin, 128, 255, "No Update");
			IJ.run(imgsegbin, "Convert to Mask", "method=Default background=Dark black");
		}
		
		imgsegbin.changes = false; // prevents save dialog on closing
		
		// Use the nucleus segmentation binary mask to get
		// - Nuclei segmented from original image
		// - Nucleoli segmentation binary mask
		// - Nucleoli segmented from original image
		for(int s=1; s<=img.getStackSize(); s++)
		{
			// Just 1 Slice to operate on
			img.setSlice(s);
			ImagePlus imgslc = new ImagePlus(img.getTitle()+" - Slice "+s, img.getProcessor());
			imgsegbin.setSlice(s);
			ImagePlus imgslcsegbin = new ImagePlus(imgsegbin.getTitle()+" - Slice "+s, imgsegbin.getProcessor());
			
			// Apply nucleus segmentation binary mask to original image
			ImagePlus imgslcsegfin = imgslc.duplicate();
			ApplyMask(imgslcsegfin, imgslcsegbin);
			// Add nucleus segmentation to stack
			stksegfin.addSlice(imgslcsegfin.getProcessor());
			
			// Segment nucleoli
			ImagePlus imgslcnucbin = imgslcsegfin.duplicate();
			IJ.run(imgslcnucbin, "Enhance Contrast...", "saturated=0 equalize");
			IJ.run(imgslcnucbin, "Gaussian Blur...", "sigma=2");
			IJ.setThreshold(imgslcnucbin, bin_min, bin_max, "No Update");
			IJ.run(imgslcnucbin, "Convert to Mask", "method=Default background=Dark black");
			IJ.run(imgslcnucbin, "Erode", "stack");
			IJ.run(imgslcnucbin, "Dilate", "stack");
			IJ.run(imgslcnucbin, "Fill Holes", "stack");
			stknucbin.addSlice(imgslcnucbin.getProcessor());
			
			// Apply nucleolus segmentation binary mask to original image
			ImagePlus imgslcnucfin = imgslc.duplicate();		
			ApplyMask(imgslcnucfin, imgslcnucbin);
			stknucfin.addSlice(imgslcnucfin.getProcessor());
		}
		
		imgsegfin = new ImagePlus("Nuclei of " + img.getTitle(), stksegfin);
		imgnucbin = new ImagePlus("Nucleolus Segmentation Mask for " + img.getTitle(), stknucbin);
		imgnucfin = new ImagePlus("Nucleoli of " + img.getTitle(), stknucfin);
		
		imgsegbin.show();
		imgsegfin.show();
		imgnucbin.show();
		imgnucfin.show();
		
		// Classification
		int measures = Measurements.AREA + Measurements.CENTROID + Measurements.CIRCULARITY;
		int opts = ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES +
				   ParticleAnalyzer.SHOW_OUTLINES + ParticleAnalyzer.LABELS;
		
    	String vol_lbl = "Volume";
    	String max_area_lbl = "Max. Area";
    	String avg_circ_lbl = "Avg. Circularity";
    	String max_circ_lbl = "Max. Circularity";

		ResultsTable nucResults= new ResultsTable();

		double min_particle_circ = 0.5;
		double max_particle_circ = 1.0;
		double min_particle_size = 50;
		double max_particle_size = 999999; // maybe set to image size..
		
		ParticleAnalyzer nucAnalyzer = new ParticleAnalyzer(opts, measures, nucResults,
				                                            min_particle_size, max_particle_size,
				                                            min_particle_circ, max_particle_circ);
		nucAnalyzer.analyze(imgsegbin);
		
	    // Do extra calculations on each nucleus
		for(int n = 0; n < nucResults.getCounter(); ++n)
		{
			Cell_Calculator cell_calc = new Cell_Calculator(imgsegbin.getStack().getProcessor(1), imgsegbin,
					(int)nucResults.getValue("X", n), (int)nucResults.getValue("Y", n));
			
			cell_calc.calc_cell_geom(pixelsPerSlice );
			cell_calc.disp_cell_data();
			ResultsTable calc_results = cell_calc.get_results();
			
			// Update the analysis results table.
			nucResults.setValue(vol_lbl, n, calc_results.getValue(vol_lbl, 0));
			nucResults.setValue(max_area_lbl, n, calc_results.getValue(max_area_lbl, 0));
			nucResults.setValue(avg_circ_lbl, n, calc_results.getValue(avg_circ_lbl, 0));
			nucResults.setValue(max_circ_lbl, n, calc_results.getValue(max_circ_lbl, 0));
		}
		
		nucResults.show("Nuclei stats");	
	}
	
	void ApplyMask(ImagePlus imgsrc, ImagePlus imgbin)
	{
		byte[] pixsrc = (byte[]) imgsrc.getProcessor().getPixels();
		byte[] pixbin = (byte[]) imgbin.getProcessor().getPixels();
		
		for(int i=0; i<pixsrc.length; i++)
			pixsrc[i] = (byte) (pixsrc[i] & pixbin[i]);
				
		imgsrc.getProcessor().setPixels(pixsrc);
	}
}
