package pkg_PlugInNucleus;

// I would have thought that imageJ would provide something like this..
public class Pixel {
    private int x = 0;
    private int y = 0;
    
    void setPixel(int in_x, int in_y)
    {
    	x = in_x;
    	y = in_y;
    }
    
    void setx(int in_x)
    {
    	x = in_x;

    }
    
    void sety(int in_y)
    {
    	y = in_y;
    }
    
    int getx()
    {
    	return x;

    }
    
    int gety()
    {
    	return y;
    }
    
    void print()
    {
    	System.out.println("(" + x + ", " + y + ")");
    }
    

    
 
}
