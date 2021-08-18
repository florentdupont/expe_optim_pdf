package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.preflight.Format;
import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

/**
 * Compresse un PDF en haute compression avec Foxit PDF Compressor 
 * et complète avec des métadonnées via PDFBox.
 */
@Slf4j
public class ConverterFoxit {

    public void convert() throws IOException, BadFieldValueException, TransformerException {

        Path executionPath = Paths.get("/home/flo/tmp/test_pdf_compressor");


        String fichierOrigine = "input.pdf";
        String fichierSortie = "out.pdf";


        // pdf_comp -i image.png -o outimage.pdf -author CROCUS -subject "MON TITRE" -lic lic_1 lic_2
        // remplacer les numéro LICENCE_1 et LICENCE2 
        List<String> pngCommands = Arrays.asList("pdf_comp", "-i", fichierOrigine, "-o", fichierSortie, "-lic", "LICENCE_1", "LICENCE_2");

        executeCommand(pngCommands, executionPath);

        // ajoute mes metadata
        try (PDDocument document = PDDocument.load(new File(executionPath + "/" + fichierSortie))) {

            // Metadatas et schemas
            XMPMetadata xmpMetadata = XMPMetadata.createXMPMetadata();

            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("ABCDEFG");
            info.setCreationDate(GregorianCalendar.from(ZonedDateTime.now()));

            // Dublin Core
            DublinCoreSchema dcSchema = xmpMetadata.createAndAddDublinCoreSchema();
            dcSchema.setTitle(info.getTitle());

            // PDF/A conformance
            PDFAIdentificationSchema identificationSchema = xmpMetadata.createAndAddPFAIdentificationSchema();
            identificationSchema.setPart(2);
            identificationSchema.setConformance("U");

            // XMPBasicSchema
            XMPBasicSchema basicSchema = xmpMetadata.createAndAddXMPBasicSchema();
            basicSchema.setCreateDate(info.getCreationDate());
            basicSchema.setMetadataDate(info.getCreationDate());

            document.setDocumentInformation(info);

            // On pousse les données xmp dans le document
            XmpSerializer serializer = new XmpSerializer();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serializer.serialize(xmpMetadata, baos, true);

            PDMetadata metadata = new PDMetadata(document);
            metadata.importXMPMetadata(baos.toByteArray());
            document.getDocumentCatalog().setMetadata(metadata);

            document.save(executionPath + "/" + fichierSortie);
        }
        
        // marche pas abec les PDF/A-2
        checkPDFACompliance(new File(executionPath + "/" + fichierSortie), Format.PDF_A1B);
    }


    public static void executeCommand(List<String> command, Path executionPath) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(command);

        Map<String, String> env = builder.environment();
        // set environment variable u
        env.put("LD_LIBRARY_PATH", "/usr/local/abbyy/fre/Bin/");
        env.put("FRE_ROOT", "/usr/local/abbyy/fre");
        
        builder.directory(executionPath.toFile());
        log.debug("Execution du process depuis {}", executionPath);

        int exitCode = 0;

        try {
            Process process = builder.start();

            if (log.isDebugEnabled()) {
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


    public static boolean checkPDFACompliance(File file, Format formatPdf) throws IOException {

        
        PreflightParser parser = new PreflightParser(file);
        ValidationResult result;
        try {
            parser.parse(formatPdf);
            PreflightDocument preflightDocument = parser.getPreflightDocument();
            preflightDocument.validate();
            result = preflightDocument.getResult();
        } catch (SyntaxValidationException e) {
            log.error("Erreur dans le isPdfA {}", e.getMessage());
            result = e.getResult();
            // le bloc finally est générateur d'erreur dans ce cas.
            // le return est donc réalisé directement.
            return false;
        } catch (IllegalArgumentException e) {
            log.error("Erreur dans le isPdfA {}", e.getMessage());
            result = new ValidationResult(false);
        } catch (NullPointerException e) {
            log.error("Erreur lors du parsing du fichier PDF", e);
            // cas d'erreur pour certains PDF identifiés lors des tests qui comportent des métadonnées XMP sans
            // entête namespace et qui posent problème lors du parsing pour validation de preflight.
            // on gère ce cas spécifiquement via un Catch NullPointerException.
            // Il s'agit d'une resolution temporaire : ce problème ne devrait pas arriver en faisant un refactoring
            // et en ne s'appuyant pas juste sur le PDF pour savoir si on doit ajouter des métadonnées ou non....
            return false;
        } finally {
            try {
                parser.getPDDocument().close();
            } catch (Exception ex) {
                log.error("Impossible de fermer le document proprement", ex);
            }
        }
        log.debug("Validité Pdf/A {} : {}", file.getAbsolutePath(), result.isValid());
        if(log.isInfoEnabled()) {
            for (ValidationResult.ValidationError error : result.getErrorsList()) {
                log.info(" error {} ", error.getDetails());
            }
        }
        return result.isValid();
       
       
    }


}
