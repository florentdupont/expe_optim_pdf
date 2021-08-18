package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Slf4j
public class Converter {

    private static final boolean DEBUG_CONVERSION = false;

    public void convert() {

        Path executionPath = Paths.get("/home/flo/tmp/test_mutool/extract_jpg_from_orig");

        String fichierOrigine = "input.pdf";    // 550kb ->  1485 kb
        String fichierSortie = "out.pdf";


        // mutool draw -o out-%03d.png -r 144 input.pdf
        // les configuration Ghostscripts préconisent
        //   72dpi pour une lecture à l'écran
        //   150 pour un ebook
        //   300 pour impression
        // pour une feuille qui était en format A4 et qui affiche du texte parfois petit (chiffres de tableurs imprimés),
        // En ayant fait des tests, il est préférable de partir sur 150 dpi.
        // plus petit, ca devient trop flou et donc illisible.
        String[] pngCommands = { "./mutool", "draw", "-o", "out-%03d.png", "-r", "150", fichierOrigine};
        executeCommand(pngCommands, executionPath);

        // Utilisation de ImageMagick :
        // le batch mode 'mogrify' est bien plus rapide que le 'convert' classique.
        // mogrify -strip -interlace Plane -quality 30 -format jpg *.png
        // une qualité de 30% est largement suffisant (le bon compromis entre taille et lisibilité).
        // d'autres essais ont été effectués :
        // Passage en Grayscale : ne réduit pas significativement la taille du fichier de sortie
        // application d'un flou (blur-filter) : ca réduit significativement la taille du fichier de sortie mais dégrade également
        // beaucoup la lecture de texte sur l'image. Il est donc préférable de ne pas en mettre
        // a la sortie d'execution, les images seront aux alentours de 80-100ko (en moyenne).
        String[] jpgCommand = { "/usr/bin/mogrify", "-strip", "-interlace", "Plane", "-quality", "30", "-format", "jpg", "*.png" };
        executeCommand(jpgCommand, executionPath);

        if(!DEBUG_CONVERSION) {
            // Suppression de toutes les images PNG intermédiaires
            clearAllPNGInPath(executionPath);
        }

        List<Path> jpgs = getAllOfFileType(executionPath, ".jpg");
        try (PDDocument outDocument = new PDDocument()) {
            for (Path image : jpgs) {

                ////////////////////////////////////////////////
                // nécessité malgré tout de lire l'image, à minima pour récupérer sa taille.
                BufferedImage bufferedImage = ImageIO.read(image.toFile());
                PDPage page = new PDPage(new PDRectangle(bufferedImage.getWidth(), bufferedImage.getHeight()));
                outDocument.addPage(page);
                PDPageContentStream pageContentStream = new PDPageContentStream(outDocument, page);

                // il est par contre plus performant (en temps et en taille) de ne pas reprocesser l'image par Java,
                // et d'inclure l'image  sous forme d'inputStream directement depuis ce qui a été optimisé par imageMagick.
                FileInputStream fis = new FileInputStream(image.toFile());
                PDImageXObject imageXObject = JPEGFactory.createFromStream(outDocument, fis);
                pageContentStream.drawImage(imageXObject, 0, 0);
                pageContentStream.close();

            }
            outDocument.save(executionPath.resolve(fichierSortie).toFile());
            log.info("file converted !");

            // vérification de la taille du fichier de sortie
            log.info("Taille du fichier d'origine : {}kb", toKb(Files.size(executionPath.resolve(fichierOrigine))));
            log.info("Taille du fichier converti  : {}kb", toKb(Files.size(executionPath.resolve(fichierSortie))));

            // TODO Si la taille est toujours trop importante :
            //     2 approches :
            //       refait une itération avec une qualité encore plus réduite ?
            //       s'il y'a trop de pages (>15 par exemple), alors proposer aux utilisateurs de réduire le nombre de pages ?


        } catch (IOException ioe) {
            log.error("ERREUR lors de la constitution du PDF", ioe);
        }

        if(!DEBUG_CONVERSION) {
            clearAllJPGInPath(executionPath);
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
