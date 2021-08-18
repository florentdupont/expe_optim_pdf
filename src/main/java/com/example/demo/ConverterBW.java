package com.example.demo;

import com.jhlabs.image.RotateFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.example.demo.ThresholdOptimized.applyInPlace;
import static com.example.demo.ThresholdOptimized.otsuTreshold;
import static java.util.stream.Collectors.toList;

@Slf4j
public class ConverterBW {

    private static final boolean DEBUG_CONVERSION = true;

    public void convert() {

        Path executionPath = Paths.get("/home/flo/tmp/test_mutool/extract_jpg_from_orig");
        String fichierOrigine = "input.pdf";
        String fichierSortie = "out.pdf";


        // mutool draw -o out-%03d.png -r 144 input.pdf
        // les configuration Ghostscripts préconisent
        //   72dpi pour une lecture à l'écran
        //   150 pour un ebook
        //   300 pour impression
        // pour une feuille qui était en format A4 et qui affiche du texte parfois petit (chiffres de tableurs imprimés),
        // En ayant fait des tests, il est préférable de partir sur 300 lors d'un export en CCITTFAxDecode
//        String[] pngCommands = { "./mutool", "draw", "-o", "out-%03d.png", "-r", "300", fichierOrigine};
//        executeCommand(pngCommands, executionPath);
        
        String[] pngCommands = { "gs", "-sDEVICE=png16m", "-dSAFER", "-dBATCH", "-dNOPAUSE", "-o", "out-%03d.png", "-r200", fichierOrigine};
        executeCommand(pngCommands, executionPath);

        // gs -sDEVICE=pngalpha -o file-%03d.png -r144 cover.pdf

        log.info("mutool export done");
        
        AtscGrayscaleFilter filter = new AtscGrayscaleFilter();
        List<Path> jpgs = getAllOfFileType(executionPath, ".png");
        try (PDDocument outDocument = new PDDocument()) {
            for (Path image : jpgs) {

                ////////////////////////////////////////////////
                // nécessité malgré tout de lire l'image, à minima pour récupérer sa taille.
                BufferedImage bufferedImage = ImageIO.read(image.toFile());
                int width = bufferedImage.getWidth();
                int height = bufferedImage.getHeight();

                BufferedImage bwImage;
                boolean optimizeRepresentation = false;
                BufferedImage rotatedImage = null;
                if(optimizeRepresentation) {
                    log.info("optimizing image to BW");
                    int[] img = bufferedImage.getRGB(0,0,width, height, null, 0, width);
                    // BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

                    // conversion initialie en niveau de gris    
                    // le grayscale ATSC (utilisé pour les TV HD) est un bon compromis entre le LUMA et le HDR.
                    // voir https://en.wikipedia.org/wiki/Grayscale#Converting_color_to_grayscale
                  
                    // filter.filter(bufferedImage, grayscaleImage);
                    for(int i=0; i< img.length; i++) {
                        img[i] = filter.filterRGB(0,0, img[i]);
                    }

                    // Thresholding utilisation un algorithme de binarisation par la méthode d'Otsu
                    // https://fr.wikipedia.org/wiki/M%C3%A9thode_d%27Otsu
                    // il en résulte une BufferedImage au format TYPE_BYTE_BINARY qui est attendue par
                    // PDFBox pour les images CCITTFaxDecode.
                        log.info("calculating threshold !");
                       int  threshold = otsuTreshold(img, width, height);
                    applyInPlace(threshold, img, width, height);
                    bwImage =  new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
                    bwImage.setRGB(0,0, width, height, img, 0, width);

                    log.info("...done");
                } else {
                    log.info("optimizing image to BW");
                    // rotate
                    rotatedImage =  new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);   // Set initial BufferedImage
                    RotateFilter rotateFilter = new RotateFilter((float)Math.toRadians(0));
                    rotateFilter.filter(bufferedImage, rotatedImage);
                    
                    ImageIO.write(rotatedImage, "png", new File("out_rotated.png"));
                    log.info("...done");
                }
                        
                
                PDPage page = new PDPage(new PDRectangle(rotatedImage.getWidth(), rotatedImage.getHeight()));
                outDocument.addPage(page);
                PDPageContentStream pageContentStream = new PDPageContentStream(outDocument, page);
                log.info("applying CCITTFaxDecode filter");
                PDImageXObject imageXObject = CCITTFactory.createFromImage(outDocument, rotatedImage);
                
                // normallement doit injecter le PNG tel quel
                log.info("...done");
                
                pageContentStream.drawImage(imageXObject, 0, 0);
                pageContentStream.close();

            }
            log.info("document prepared. About to write PDF to disk");
            outDocument.save(executionPath.resolve(fichierSortie).toFile());
            log.info("file converted !");

            // vérification de la taille du fichier de sortie
            log.info("Taille du fichier d'origine : {}kb", toKb(Files.size(executionPath.resolve(fichierOrigine))));
            log.info("Taille du fichier converti  : {}kb", toKb(Files.size(executionPath.resolve(fichierSortie))));


        } catch (IOException ioe) {
            log.error("ERREUR lors de la constitution du PDF", ioe);
        }

        if(!DEBUG_CONVERSION) {
            clearAllPNGInPath(executionPath);
        }

    }

    public static long toKb(long size) {
        return size/1024;
    }
    public static void clearAllJPGInPath(Path path) {
        clearAllFileTypeInPath(path, ".jpg");
    }

    public static void clearAllPNGInPath(Path path) {
        clearAllFileTypeInPath(path, ".png");
    }

    public static void clearAllFileTypeInPath(Path path, String fileType) {
        List<Path> files = getAllOfFileType(path, fileType);
        for(Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // TODO : gérer l'erreur
                e.printStackTrace();
            }
        }
    }

    public static List<Path> getAllOfFileType(Path rootPath, String fileType) {
        List<Path> images = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(rootPath)) {
            images = walk.filter(f-> f.toString().endsWith(fileType)).sorted().collect(toList());
        } catch (IOException e) {
            // TODO revoir l'exception ici
            e.printStackTrace();
        }
        return images;
    }


    public static void executeCommand(String[] command, Path executionPath) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(command);

        builder.directory(executionPath.toFile());
        log.debug("Execution du process depuis {}", executionPath);

        int exitCode = 0;

        try {
            Process process = builder.start();

            if (log.isDebugEnabled()) {
//                StreamGobbler inputStreamGobbler = new StreamGobbler(process.getInputStream(), System.out::println); //NOSONAR
//                StreamGobbler errorStreamGobbler = new StreamGobbler(process.getErrorStream(), System.out::println); //NOSONAR
//                Executors.newSingleThreadExecutor().submit(inputStreamGobbler);
//                Executors.newSingleThreadExecutor().submit(errorStreamGobbler);
                // il est préférable de ne pas passer par une gestion de stream qui va lancer un SingleThreadExecutor
                // qui peut ne pas être correctement libéré.
                // Dans mon exemple ici, un thread n'était pas correctement libéré (et l'application ne se termine donc jamais
                // a cause des thread qui ne sont pas correctement géré (ou stream pas close?).
                // Bref : il n'est pas nécessaire de faire une affichage en temps réel ici. Un affichage 'en bloc'
                // une fois que c'est c'est fini est suffisant : Passer pas des string est moins risqué.
                String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
                if(!output.isEmpty())
                    log.debug(output);
                String errorOutput = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);
                if(!errorOutput.isEmpty())
                    log.error(errorOutput);
            }

            exitCode = process.waitFor();

        } catch (IOException e) {   // pour le start
            // TODO a revoir la gestion de l'exception
            e.printStackTrace();
        }
        catch (InterruptedException e) { // pour le waitFor
            Thread.currentThread().interrupt();
        }

        if (exitCode != 0) {
            log.error("Erreur d'execution de commande système. Code retour {}", exitCode);
        }

    }


}
