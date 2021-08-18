package com.example.demo;

import org.apache.xmpbox.type.BadFieldValueException;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.xml.transform.TransformerException;
import java.io.IOException;


@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) throws IOException, BadFieldValueException, TransformerException {


		new ConverterFoxit().convert();
		// new ConverterPNGAdaptatif().convert();
        // new ConverterBW().convert();
        // new NoteShrink2().run();
        // new ConverterPNG().convert();

		/*

		mutool clean in.pdf out.pdf

        pour sortir une image en PNG
        ./mutool draw -o out.png -r 144 input.pdf 2


        Pour sortir toutes les images :
        mutool draw -o out-%d.png -r 144 input.pdf
        ou
        mutool draw -o out-%03d.png -r 144 input.pdf


        pour convertir une image en JPG
        https://stackoverflow.com/questions/7261855/recommendation-for-compressing-jpg-files-with-imagemagick


		https://stackoverflow.com/questions/13317753/convert-rgb-to-grayscale-in-imagemagick-command-line

        convert -strip -interlace Plane -gaussian-blur 0.05 -quality 60 out.png out.jpg

        pour plusieurs fichiers :

        mogrify -strip -interlace Plane -quality 60 -format jpg *.png

        en N/B - change rien en fait
        mogrify -strip -interlace JPEG -quality 60 -colorspace Gray -format jpg *.png

        1.4 Mo
        mogrify -strip -interlace JPEG -quality 60 -colorspace Gray -gaussian-blur 0.02 -format jpg *.png


        mogrify -strip -interlace Plane -quality 60 -colorspace Gray -gaussian-blur 0.02 -format jpg *.png


        // j'arrive a atteindre la même taille, mais avec une qualité bien dégradée. C'est lisible, mais bien moins !
        mogrify -strip -interlace Plane -quality 10-colorspace sRGB -gaussian-blur 0.02 -format jpg *.png


        Le juste milieu
        mogrify -strip -interlace Plane -quality 30 -format jpg *.png




		=> petit flou qui peut être adapté pour des photos , mais pas terrible pour des documents à lire.

		https://developers.google.com/speed/docs/insights/OptimizeImages


		convert INPUT.jpg -sampling-factor 4:2:0 -strip [-resize WxH] [-quality N] [-interlace JPEG] [-colorspace Gray/sRGB] OUTPUT.jpg



		 */


		// https://compress-or-die.com/Understanding-JPG
		// https://stackoverflow.com/questions/60204999/pdfbox-pdf-increase-size-after-converting-to-grayscale



	}




}
