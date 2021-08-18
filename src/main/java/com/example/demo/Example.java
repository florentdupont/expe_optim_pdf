package com.example.demo;

import javax.swing.*;
import java.awt.image.*;

/* https://java.databasedevelop.com/article/11269575/raster+dimensions+overflow */
public class Example {
    public static void main(String[] args) {
        int w = 499; //to be tough
        int h = 100; //for example
        byte[] pixelByte = new byte[h*(w/8 + (w%8==0?0:1))];
        byte [] b = {0,(byte)255};

        System.out.println(499 * 100);
        System.out.println(pixelByte.length);
        pixelByte[1] = (byte)0xff;
        IndexColorModel cm = new IndexColorModel(1,2,b,b,b);
        DataBufferByte db = new DataBufferByte(pixelByte, pixelByte.length);
        WritableRaster raster = Raster.createPackedRaster(db, w, h, 1, null);
        
        BufferedImage image = new BufferedImage(cm,raster,false,null);
        
//        Graphics2D g = image.createGraphics();
//        g.setFont(new Font("Lucida Sans", Font.PLAIN, 72));
//        g.setColor(Color.WHITE);
//        g.drawString("Hello, World", 10,80);
//        g.dispose();
        
        JFrame frame = new JFrame ("Complex Function");
        frame.getContentPane().add(new JLabel(new ImageIcon(image)));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}