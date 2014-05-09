package pkg_PlugInNucleus;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

// Class to do calculations on processed images of cells to calculate
// various attributes such as volume, size, density, etc..
public class Cell_Calculator {
    
	int x_center = 0;
	int y_center = 0;
	
	ImageProcessor iProc;
	ImagePlus iPlus;
	
	//http://imagejdocu.tudor.lu/doku.php?id=macro:multiple_points
	// Containers for cell parameter points.
	int [] xpoints;
	int [] ypoints;
	
	double volume = 0;
	double avg_circularity = 0;
	double max_circularity = 0;
	double max_area = 0;
	double avg_density = 0;
	
	int p_idx = 0;
	int in_cell_val = 255;
	int out_cell_val = 0;
	 
	//TODO: if possible, don't init with image processor, just image plus
	// Constructor
	// Takes ImagePlus of the image containing cell(s) and an x,y for the center of the cell to be measured
	//NOTE: At some point the xy, might have to be an array of coords of the center of the cell for each slice
	Cell_Calculator(ImageProcessor imageProcessor, ImagePlus imagePlus, int center_x, int center_y)
	{
		this.iProc = imageProcessor;
		this.iPlus = imagePlus;
		
		x_center = center_x;
		y_center = center_y;
		
		xpoints = new int[iProc.getWidth()];
		ypoints = new int[iProc.getHeight()];
		
		//System.out.println("width: " + iProc.getWidth());
		//System.out.println("height: " + iProc.getHeight());
		//System.out.println("image area: " + iProc.getWidth() * iProc.getHeight());
		
		iProc.setLineWidth(3);
    	java.awt.Color color = Color.red;
    	iProc.setColor(color);
		
		IJ.setTool("multipoint");
	}
	
	
	// Calculate the volume based on the area of each slice of sliceMeters thickness.
    void calc_cell_geom(double pixelsPerSlice)
    {	
    	double vol = 0;
    	double circ_sum = 0;
    	int num_slices = iPlus.getStackSize();

		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= num_slices; i++)
		{
			// Might consider storing the stats for each slice. For now leave temp.	
			Cell_Statistics sliceStats = calc_slice_data(x_center, y_center, iPlus.getStack().getProcessor(i));
			
			// Circularity calculations
			if(sliceStats.circularity > max_circularity)
			{
				max_circularity = sliceStats.circularity;
			}
			
			circ_sum += sliceStats.circularity;
			
			// Area calculations
			double area  = sliceStats.area;
			
			if(area > max_area)
			{
				max_area = area;
			}
			
			vol += (area * pixelsPerSlice);
			//System.out.println("area[" + i + "] = " + area + ", vol now = " + vol);
		}
		
		avg_circularity = circ_sum / num_slices;
		
		volume = vol;
    }
    
    
    // Get the area of a cell with an approx center at (cx, cy) from the given ImageProcessor.
    // This is for use with image slices, pass the imageProcessor of each slice.
    Cell_Statistics calc_slice_data(int cx, int cy, ImageProcessor imageProcessor)
    {
    	Cell_Statistics returnStats = new Cell_Statistics();
    	// Use the wand tool to get the points needed to create the poly ROI.
    	ij.gui.Wand wand = new ij.gui.Wand(imageProcessor);
    	wand.autoOutline(cx, cy);
    	
    	PolygonRoi pRoi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.TRACED_ROI);
    	//imagePlus.setRoi(pRoi);
    	imageProcessor.setRoi(pRoi);
    	
    	// Enable this to see the ROI outlined for debugging, but note it might will break the image stat
    	// calculations
    	//pRoi.drawPixels(iProc);
    	
    	// Get the statistics.
    	ImageStatistics stats = imageProcessor.getStatistics();
    	
    	// I'm having trouble getting the measured circ to work. I found this equation at
    	// http://rsb.info.nih.gov/ij/plugins/circularity.html
    	returnStats.circularity = 4 * Math.PI * (stats.area / (pRoi.getLength() * pRoi.getLength()));
    	
    	returnStats.area = stats.area;
    	
    	return returnStats;
    }
    
    
    // Log data, return a result table
    ResultsTable get_results()
    {
    	// TODO: move externally to this class. Use addResults to add to it for each nucleus.
    	String vol_lbl = "Volume";
    	String max_area_lbl = "Max. Area";
    	String avg_circ_lbl = "Avg. Circularity";
    	String max_circ_lbl = "Max. Circularity";
    	String dens_lbl = "Density";
    	
    	int row = 0;
    	
    	ResultsTable results = new ResultsTable();
    	
    	results.setValue(vol_lbl, row, volume);
    	results.setValue(max_area_lbl, row, max_area);
    	results.setValue(avg_circ_lbl, row, avg_circularity);
    	results.setValue(max_circ_lbl, row, max_circularity);
    	results.setValue(dens_lbl, row, "TODO");

    	//results.show("Nucleus characteristics");
    	
    	// To log to the Console
//    	System.out.println("LOG DATA");
//    	System.out.println(vol_lbl + " " + volume);
//    	System.out.println(max_area_lbl + " " + max_area);
//    	System.out.println(avg_circ_lbl + " " + avg_circularity);
//    	System.out.println(max_circ_lbl + " " + max_circularity);
//    	System.out.println(dens_lbl + " TODO");
    	
    	return results;
    }
    
    
    // Display data for this cell
    // TODO: why does this only work the first time?
    void disp_cell_data()
    {
    	// Draw a point at the center
    	IJ.makePoint(x_center, y_center);
    	
    	DecimalFormat dForm = new DecimalFormat("#,###,###,##0.00");
    	
		int fontSize = 10;
		int fontStyle = Font.PLAIN;
		TextRoi.setFont("SansSerif", fontSize, fontStyle);
		TextRoi.setGlobalJustification(TextRoi.LEFT);
		
		// Delta for next line position. Is there an easier way to do this?
		int line_dist_coef = fontSize + 2;
		int line_dist = 0;
    	
		// Create text ROIs
		// Volume
		String vol_text = "Volume: " + dForm.format(volume).toString() + " voxels";
		TextRoi vol_textRoi = new TextRoi(x_center, y_center, vol_text);
		line_dist = 1 * line_dist_coef;
		
	    // AVG Circularity	
		String avg_circ_text = "Avg Circularity: " + dForm.format(avg_circularity).toString();
		TextRoi avg_circ_textRoi = new TextRoi(x_center, y_center + line_dist, avg_circ_text);
		line_dist = 2 * line_dist_coef;
		
	    // Max Circularity	
		String max_circ_text = "Max Circularity: " + dForm.format(max_circularity).toString();
		TextRoi max_circ_textRoi = new TextRoi(x_center, y_center + line_dist, max_circ_text);
		line_dist = 3 * line_dist_coef;
		
	    // Max Area	
		String max_area_text = "Max Area: " + dForm.format(max_area).toString();
		TextRoi max_area_textRoi = new TextRoi(x_center, y_center + line_dist, max_area_text);
		line_dist = 4 * line_dist_coef;
		
		// Add text ROIs to overlay
		Overlay textOverlay = new Overlay(vol_textRoi);
		textOverlay.add(avg_circ_textRoi);
		textOverlay.add(max_circ_textRoi);
		textOverlay.add(max_area_textRoi);
		
		//TODO: density data
		
		textOverlay.setStrokeColor(Color.yellow);
		//textOverlay.setLabelColor(Color.yellow);
		textOverlay.setFillColor(Color.gray);
		//textOverlay.drawLabels(true);
		//textOverlay.drawNames(true);
		
		System.out.println("Calling setOverlay");
		iPlus.setOverlay(textOverlay);
    }    

}
