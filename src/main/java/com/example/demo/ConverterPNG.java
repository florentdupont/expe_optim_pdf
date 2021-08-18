package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Conversion non pas en JPEG mais en PNG avec noteshrink
 */
@Slf4j
public class ConverterPNG {

    private static final boolean DEBUG_CONVERSION = true;
    private static final boolean SKIP_MUTOOL= false;
    private static final boolean SKIP_NOTESHRINK= false;

    public void convert() {

        Path executionPath = Paths.get("/home/flo/tmp/test_noteshrink");
                             
        // 668kb -> 1816kb
        // avec optimisation byte[] et zopfli : 1063ko
        // avec quantization à 8 couleurs : 887ko
        
        String fichierOrigine = "input.pdf";
        // 140kb -> 42kb (8couleurs)
                
//      String fichierOrigine = "other_input.pdf";
      //   550kb --> 645 kb ( 4coul.) 
      //   550kb --> 895kb (8 couleurs)  aucun optimizer, zopfli, palette globale ?
      //   550kb --> 1100Kb (16coul.)
            
        // la taille peu varier d'une fois sur l'autre c'est normal : il s'agit d'un approche d'échantillonage aléatoire.
        
      String fichierSortie = "out.pdf";


        
        clearAllPNGInPath(executionPath);
        
        
        // les configuration Ghostscripts préconisent
        //   72dpi pour une lecture à l'écran
        //   150 pour un ebook
        //   300 pour impression
        // pour une feuille qui était en format A4 et qui affiche du texte parfois petit (chiffres de tableurs imprimés),
        // En ayant fait des tests, il est préférable de partir sur 150 
        // plus petit, ca devient trop flou et donc illisible.
        List<String> pngCommands = Arrays.asList("./mutool", "draw", "-o", "out-%03d.png", "-r", "150", "in/" + fichierOrigine);

        if(!SKIP_MUTOOL) {
            executeCommand(pngCommands, executionPath);
        }

        List<Path> pngs = getAllOfFileType(executionPath, ".png");
        List<String> files = pngs.stream().map(x -> x.toFile().getName()).collect(Collectors.toList());
        // ./noteshrink.py in03.png -Q -O -C -w -n 12
        // optimize avec pngcrush optipng et pngquant
        // insiste sur le fait que le fond est blanc
        // limite à 12 couleurs

        // https://news.ycombinator.com/item?id=16567275
        // https://github.com/tesseract-ocr/tesseract ??
       
        // la commande noteshrink a été adaptée pour ne pas sortir en PDF
        
        List<String> shrinkBaseCommand = Arrays.asList( "./noteshrink.py", 
// mieux vaut éviter l'ajout d'optimisateur en plus. Ils ont tendance a augmenter la taille!
//                                    "-Q",   // pngquant    -- réduit un peu mais pas grand chose. l'optim étant déjà portée par noteshrink 
//                                    "-O",  // optipng      -- ne servent à rien
//                                    "-C",  // pngcrush     -- ne servent à rien
                                    "-w",          // force le fond blanc
                                    "-n", "4",     // nd de couleurs
                                  //  "-p", "5",    // % d'échantillonnage (plus y'en a et plus c'est long..) 5 par défaut, c'est suffisant
//                                    "-g",        // palette globale
                                    "filenames");

        ArrayList<String> completedCommand = new ArrayList<>(shrinkBaseCommand);
        completedCommand.addAll(files);
        if(!SKIP_NOTESHRINK) {
            executeCommand(completedCommand, executionPath);
        }
        // va sortir des images sous la forme page0000.png


        /******************
         * A priori PDFBox est capable de prendre certains format de PNG tels quels.
         *  et préconise l'encodage ZOPFLI pour éviter qu'il passe par une BufferedImage
         *  lors de l'utilisation du createFromFileByContent().
         * 
         * https://blog.codinghorror.com/zopfli-optimization-literally-free-bandwidth/
         * https://ariya.io/2016/06/using-zopfli-to-optimize-png-images
         * 
         * ZOPFLI
         */
//        List<Path> postprocessedPngs = getAllOfFileMatching(executionPath, "page.*\\.png");
//        for (Path image : postprocessedPngs) {
//            List<String> zopfliCommand = Arrays.asList( "./zopflipng", image.toFile().getName(), "zopfli_" + image.toFile().getName());
//            executeCommand(zopfliCommand, executionPath);
//        }

        List<Path> shrinkedPngs = getAllOfFileMatching(executionPath, "page.*\\.png");
        files = shrinkedPngs.stream().map(x -> x.toFile().getName()).collect(Collectors.toList());
        // plus rapide de le lancer 1 fois pour tous les fichiers
        List<String> zopfliCommand = Arrays.asList( "./zopflipng", 
                                                    "--lossy_8bit",
                                                    "--prefix");
        completedCommand = new ArrayList<>(zopfliCommand);
        completedCommand.addAll(files);
         executeCommand(completedCommand, executionPath);
        
        
        // oxipng inclut zopfli -- mais il est bien bien plus long !! et en plus, il n'optimise quasiment pas plus...
        // https://github.com/shssoichiro/oxipng
//        List<Path> postprocessedPngs = getAllOfFileMatching(executionPath, "page.*.png");
//        for (Path image : postprocessedPngs) {
//            List<String> zopfliCommand = Arrays.asList( "./oxipng", 
//                                                        "-o", "1",     // optim 2
//                                                        "-i", "0",    // interlace 0
//                                                        "-s",         // strip
//                                                        "-Z",         // zopfli
//                                                        image.toFile().getName());
//            executeCommand(zopfliCommand, executionPath);
//        }


        List<Path> postprocessedPngs = getAllOfFileMatching(executionPath, "zopfli_page.*.png");
//        List<Path> postprocessedPngs = getAllOfFileMatching(executionPath, "page.*\\.png");
        try (PDDocument outDocument = new PDDocument()) {
            for (Path image : postprocessedPngs) {

                log.info("including image " + image + " into PDF.");
                ////////////////////////////////////////////////
                // nécessité malgré tout de lire l'image, à minima pour récupérer sa taille.
                BufferedImage bufferedImage = ImageIO.read(image.toFile());
                PDPage page = new PDPage(new PDRectangle(bufferedImage.getWidth(), bufferedImage.getHeight()));
                outDocument.addPage(page);
                PDPageContentStream pageContentStream = new PDPageContentStream(outDocument, page);

                // il est par contre plus performant (en temps et en taille) de ne pas reprocesser l'image par Java,
                // et d'inclure l'image  sous forme d'inputStream directement depuis ce qui a été optimisé par imageMagick.
                byte[] bytes = FileUtils.readFileToByteArray(image.toFile());
                // en passant par un tableau de bytes, PDFbox ne converti pas en passant par une BufferedImage.
                // il prend le PNG tel quel (s'il le peut), ce qui permet de tirer parti de l'optimisation préalable
                // par Zopfli.
                PDImageXObject imageXObject = PDImageXObject.createFromByteArray(outDocument, bytes, image.toFile().getName());
                pageContentStream.drawImage(imageXObject, 0, 0);
                pageContentStream.close();

            }
            outDocument.save(executionPath.resolve(fichierSortie).toFile());
            log.info("file converted !");

            // vérification de la taille du fichier de sortie
            log.info("Taille du fichier d'origine : {}kb", toKb(Files.size(executionPath.resolve("in/" + fichierOrigine))));
            log.info("Taille du fichier converti  : {}kb", toKb(Files.size(executionPath.resolve(fichierSortie))));

        } catch (IOException ioe) {
            ioe.printStackTrace();
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
        // Files.walk traverse tous les répertoires. Ici, on veut rester dans le reépertoire courant
        try (Stream<Path> walk = Files.list(rootPath)) {
            images = walk.filter(f-> f.toString().endsWith(fileType)).sorted().collect(toList());
        } catch (IOException e) {
            // TODO revoir l'exception ici
            e.printStackTrace();
        }
        return images;
    }

    public static List<Path> getAllOfFileMatching(Path rootPath, String filePattern) {
        List<Path> images = new ArrayList<>();
        // Files.walk traverse tous les répertoires. Ici, on veut rester dans le reépertoire courant
        try (Stream<Path> walk = Files.list(rootPath)) {
            images = walk.filter(f-> f.toFile().getName().matches(filePattern)).sorted().collect(toList());
        } catch (IOException e) {
            // TODO revoir l'exception ici
            e.printStackTrace();
        }
        return images;
    }


    public static void executeCommand(List<String> command, Path executionPath) {

        if(DEBUG_CONVERSION) {
            log.info("executing : " + String.join(" ", command)); 
        }
        
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
