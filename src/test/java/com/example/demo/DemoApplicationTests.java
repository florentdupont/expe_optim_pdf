package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


class DemoApplicationTests {

	@Test
    public void test2Bits() {
	                              //      01100011     |     10110100 
	    byte[] source = new byte[] { 0x1, 0x2, 0x0, 0x3, 0x2, 0x3, 0x1, 0x0}; 
	    byte[] dest = new byte[2];
	    int nbOfBits = 2;
	    PalettePNG.reduceRasterToOptimalBytes(source, dest, nbOfBits, 100, 100);
        for(int i = 0; i < dest.length; i++) {
            System.out.println(PalettePNG.toBinary(dest[i]));
            // should print 01100011  10110100
        }
    }

    @Test
    public void test4Bits() {
                                   //      1111'0011     |     1011'0111    | 1010'0010   
        byte[] source = new byte[] { 0xf, 0x3,             0xb, 0x7 ,          0xa, 0x2};
        byte[] dest = new byte[3];
        int nbOfBits = 4;
        PalettePNG.reduceRasterToOptimalBytes(source, dest, nbOfBits, 100, 100);
        for(int i = 0; i < dest.length; i++) {
            System.out.println(PalettePNG.toBinary(dest[i]));
            // should print 11110011  10110111 10100010
        }
    }

    @Test
    public void test1Bits() {
                                //        1010'1110                     00011010
        byte[] source = new byte[] { 1, 0, 1, 0,  1, 1, 1, 0,   0, 0, 0, 1, 1, 0, 1, 0};
        byte[] dest = new byte[2];
        int nbOfBits = 1;
        PalettePNG.reduceRasterToOptimalBytes(source, dest, nbOfBits, 100, 100);
        for(int i = 0; i < dest.length; i++) {
            System.out.println(PalettePNG.toBinary(dest[i]));
            // should print 10101110  00011010
        }
    }
    
    @Test 
    void testcomplexe() {
        byte[] source = new byte[] { 0x0, 0x1, 0x2, 0x3, 0x1, 0x2};
        
        int nbOfBits = 4;
        int nbOfDest = PalettePNG.nbOfBytesForRaster(nbOfBits, 3, 2);
        byte[] dest = new byte[nbOfDest];
        PalettePNG.reduceRasterToOptimalBytes(source, dest, nbOfBits, 100, 100);
        for(int i = 0; i < dest.length; i++) {
            System.out.println(PalettePNG.toBinary(dest[i]));
            // should print 10101110  00011010
        }
    }

    @Test
    void testcomplexePriseEnCompteFinDeLigne() {
	    
	    // Chaque Byte doit être complet en fin de ligne!!
       // le width est donc important 
	    
        byte[] source = new byte[] { 0x0, 0x1, 0x2, 
                                     0x3, 0x1, 0x2};

        int nbOfBits = 4;
        int nbOfDest = PalettePNG.nbOfBytesForRaster(nbOfBits, 3, 2);
        byte[] dest = new byte[nbOfDest];
        PalettePNG.reduceRasterToOptimalBytes(source, dest, nbOfBits, 3, 2);
        for(int i = 0; i < dest.length; i++) {
            System.out.println(PalettePNG.toBinary(dest[i]));
            // il y'a un découpage 3x2
            // on doit donc avoir :
            //  0000 0001 0010 (0000)    les 2 derniers sont des remplissages de fin de ligne
            //  0011 0001 0010 (0000)    
            // Le nombre de case dans le Raster donc donc être de 8
            
            
            
        }
    }
    
    @Test
    void shiftBits() {
	    // comment calculer le masque pour un nombre de bits
	    int nbbits = 4;
	    byte mask = (byte)((1<<nbbits) - 1);
	    System.out.println(PalettePNG.toBinary(mask));

        nbbits = 2;
        mask = (byte)((1<<nbbits) - 1);
        System.out.println(PalettePNG.toBinary(mask));

        nbbits = 1;
        mask = (byte)((1<<nbbits) - 1);
        System.out.println(PalettePNG.toBinary(mask));
    }
    
    @Test 
    void nbBitsPerPixel() {
        int nb = PalettePNG.nbOfBytesForRaster(4, 1240, 1754);
        System.out.println(nb);
        
    }
    
    @Test
    void bitshift() {
	    int p = 0;
        p = (p << 16) | (p << 8) | (p);

        System.out.println(Integer.toHexString(p));
    }
}
