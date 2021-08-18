package com.example.demo;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.apache.commons.lang3.StringUtils.leftPad;

@Slf4j
public class PalettePNG {

    public static void main(String[] args) throws IOException {
        new PalettePNG().run();
    }
    
    
    
    void run() throws IOException {
    
        // définit une image de 3*2 couleur
        int width = 5;
        int height = 2;
        
        int p1 = 0xffffff;   // blanc
        int p2 = 0xff0000;   // rouge
        int p3 = 0x00ff00;   // vert
        int p4 = 0x0000ff;   // bleu
        int p5 = 0x555555;
        int p6 = 0x000000;
        int p7 = 0xc0c0c0;
        int p8 = 0x888888;
        
        int[] palette = new int[] {p1, p2, p3, p4/*, p5, p6, p7, p8 */};
        //                    blan rouge vert bleu bleu vert
        int[] img = new int[] {p1, p2, p3, p4, p4, p3};
        
        displaySampleImage(img, "palettePNG_img.png", 3,2);
        displaySampleImage(img, "palettePNG_palette.png");
       
        BufferedImage bi = optimiseToPngImage(img, palette, width, height);
        ImageIO.write( bi, "PNG", new File("out.png") );
        
    }
    
    public static BufferedImage optimiseToPngImage(int[] source, int[] palette, int width, int height) {
        IndexColorModel cm = createColorModel(palette);
        byte[] paletizedImage = paletizeImage(source, palette);

        
        // TODO : est-ce que le BufferedImage.setRGB() ne fait pas exactement ça ??
        //        j'ai à moitié l'impression que si !
        
        // from http://www.libpng.org/pub/png/book/chapter08.html#png.ch08.div.5.1
        // Palette-based images, also known as colormapped or index-color images, use the PLTE chunk and are supported in four pixel depths: 1, 2, 4, and 8 bits, 
        // corresponding to a maximum of 2, 4, 16, or 256 palette entries. 
        // Unlike GIF images, however, fewer than the maximum number of entries may be present. 
        int nbBitsPerPixel =  numberOfBitsForColorsRaster(palette.length);
        log.info("nbBitsPerPixels : {}", nbBitsPerPixel);
        WritableRaster raster;
        if(nbBitsPerPixel < 8) {
            // palettes de 2, 4 et 16 entrées
            byte[] reducedPaletizedImage = new byte[nbOfBytesForRaster(nbBitsPerPixel, width, height)];
            reduceRasterToOptimalBytes(paletizedImage, reducedPaletizedImage, nbBitsPerPixel, width, height);
            DataBuffer dataBuffer = new DataBufferByte(reducedPaletizedImage, reducedPaletizedImage.length);
            SampleModel sampleModel = cm.createCompatibleSampleModel(width, height);
            raster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
        } else {
            // pour les palettes de 256 entrées ( 1Byte par pixel)
            // l'image paletisée est laissée telle quelle.
            DataBuffer dataBuffer = new DataBufferByte(paletizedImage, paletizedImage.length);
            SampleModel sampleModel = new SinglePixelPackedSampleModel(DataBuffer.TYPE_BYTE, width, height, new int[] {(byte)0xff});
            raster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
        }
        return new BufferedImage(cm, raster, false, null);
    }

    /** réduit la l'image paletisée en réduisant au maximum le nombre de pixels par Byte*/
    public static void reduceRasterToOptimalBytes(byte[] source, byte[] destination, int nbOfBits, int width, int height) {
        // si nbOfBits = 1 => 8 pixels par Byte
        // si nbOfBits = 2 => 4 pixels par Byte
        // si nbOfBits = 4 => 2 pixels par Byte
        // s'il y'a 1 pixel par Byte, c'est une autre représentation qui est utilisées (SingleByte)
        
        int nbPixelsPerByte = 8 / nbOfBits;
        byte mask = 0x00;  // masque par défaut pour 8 pixels 
        
        // TODO le masque peut être calculé via du bitshift;
        switch (nbOfBits) {
            case 4: mask = (byte)0x0f; break; // 0x1111
            case 2: mask = (byte)0x03; break; // 0x0011
            case 1 : 
            default: mask = (byte)0x01; break; // 0x0001
        }
        log.info("width {}, height {}  source : {}   dest : {}  nbbits {} ", width, height, source.length, destination.length, nbOfBits);
     
        int indexDst=0;
        byte buffer = 0x0;
        int nbEltInBuffer = 0;
        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                int indexSource = (i*width)+j;
                buffer = (byte)(buffer << nbOfBits);
                buffer += (byte)(source[indexSource] & mask);
                nbEltInBuffer++;
                if(nbEltInBuffer == nbPixelsPerByte) {
                    destination[indexDst] = buffer;
                    indexDst++;
                    buffer = 0x0;
                    nbEltInBuffer = 0;
                }
            }
            // Si le nombre de pixel n'est pas rempli en fin de ligne, alors le reste du byte
            // est rempli avec des 0.
            if(nbEltInBuffer > 0) {
                buffer = (byte)(buffer << ((nbPixelsPerByte-nbEltInBuffer)*nbOfBits));
                destination[indexDst] = buffer;
                indexDst++;
                buffer = 0x0;
                nbEltInBuffer = 0;
            }
        }
        
    }
    
    /** combien d'index faut il prévoir */
    public static int nbOfBytesForRaster(int nbOfBitsPerPixel, int width, int height) {
        int nbPixelsPerByte = 8 / nbOfBitsPerPixel; 
        // see example https://java.databasedevelop.com/article/11269575/raster+dimensions+overflow
        return height*(width/nbPixelsPerByte + (width%nbPixelsPerByte==0?0:1));    
    }
    
    public static String toBinary(byte b) {
        return leftPad(Integer.toBinaryString(b & 0xff), 8, '0');
    }
    
    public static byte[] paletizeImage(int[] source, int[] palette) {
        // inverse la palette
        Map<Integer, Integer> inversedPalette = new HashMap<>(palette.length);
        for (int i=0; i<palette.length; i++) {
            inversedPalette.put(palette[i], i);
        }
        byte[] dest = new byte[source.length];
        for(int i=0; i<source.length; i++) {
            int rgb = source[i];
            Integer indexPalette = inversedPalette.get(rgb);
           dest[i] = indexPalette.byteValue();
        }
        return dest;
    }
    
    public static IndexColorModel createColorModel(int[] palette) {
        byte r[] = new byte[palette.length];
        byte g[] = new byte[palette.length];
        byte b[] = new byte[palette.length];

        for (int i = 0; i<palette.length; i++) {
            int rgb = palette[i];
            r[i] = (byte)(((rgb >> 16) & 0xFF));
            g[i] = (byte)(((rgb >> 8) & 0xFF));
            b[i] = (byte)((rgb & 0xFF));
        }

        // création du IndexColorModel
        int nbBits = numberOfBitsForColorsRaster(palette.length);
        IndexColorModel cm = new IndexColorModel(
                nbBits,       // nombre de bits nécessaire pour représenter toutes les couleurs de la palette
                palette.length,  // nombre de couleurs de la palette
                r, g, b);
        return cm;
    }

    /**
     * Retourne 
     */
    public static int numberOfBitsForColorsRaster(int numberOfColors) {
        int n = (int)(Math.ceil(Math.log(numberOfColors * 1.0)/Math.log(2d)));
        if(n <= 2 )
            return n;   // 2 ou 4 valeurs
        if( n <= 4)
            return 4;   // 16 valeurs
        return 8;    // 256 valeurs
        // au dela, la palette n'est plus possible
    }

    void displaySampleImage(int[] pixels, String name) throws IOException {
        displaySampleImage(pixels, name, -1, -1);
    }

    void displaySampleImage(int[] pixels, String name, int width, int height) throws IOException {
        log.info("length : {}", pixels.length);

        if(width == -1) {
            // affiche sous forme de carré (à peu près)
            width = (int) Math.sqrt(pixels.length);
        }
        if(height == -1) {
            height = pixels.length / width;
            log.info("width / height : {} / {}", width, height);
            int rest = pixels.length - (width * height);   // il peut y avoir du rab
            if(rest > 0) {
                height++;
            }
        }

        log.info("creating sample debug image of size : {} x {}", width, height);
        BufferedImage img = new BufferedImage(width, height, TYPE_INT_RGB);
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                // on the last line there may be some pixels not affected.
                int index = ((width) * y) + x;
                if(index >= pixels.length) {
                    img.setRGB(x, y, 0x01ff70);    // couleur flashy "de remplissage" pour être sûr de bien distinguer
                } else {
                    img.setRGB(x, y, pixels[index]);
                }

            }
        }
        ImageIO.write(img, "png", new File(name));
    }
}
