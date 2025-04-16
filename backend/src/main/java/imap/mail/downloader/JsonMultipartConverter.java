package imap.mail.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Klasa konwertująca załączniki z formatu JSON (Base64) na obiekty MultipartFile
 */
public class JsonMultipartConverter {
    private static final Logger logger = LoggerFactory.getLogger(JsonMultipartConverter.class);

    /**
     * Konwertuje metadane JSON zawierające zakodowane załączniki na tablicę MultipartFile
     *
     * @param metadataJson JSON zawierający informacje o załącznikach
     * @return tablica obiektów MultipartFile lub null, jeśli nie ma załączników
     */
    public static MultipartFile[] convertJsonToMultipartFiles(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(metadataJson);
            JsonNode attachmentsNode = rootNode.get("attachments");

            if (attachmentsNode == null || !attachmentsNode.isArray() || attachmentsNode.isEmpty()) {
                logger.info("Brak załączników w metadanych JSON");
                return null;
            }

            List<MultipartFile> attachments = new ArrayList<>();

            for (JsonNode attachmentNode : attachmentsNode) {
                try {
                    String name = attachmentNode.get("name").asText();
                    String mimeType = attachmentNode.get("mimeType").asText();
                    String data = attachmentNode.get("data").asText();
                    String encoding = attachmentNode.has("encoding") ?
                            attachmentNode.get("encoding").asText() : "base64";

                    if (!"base64".equalsIgnoreCase(encoding)) {
                        logger.warn("Nieobsługiwane kodowanie załącznika: {}", encoding);
                        continue;
                    }

                    // Dekodowanie danych Base64
                    byte[] fileContent = Base64.getDecoder().decode(data);

                    // Tworzenie obiektu własnej implementacji MultipartFile
                    Base64MultipartFile file = new Base64MultipartFile(
                            fileContent,
                            "attachment",
                            name,
                            mimeType
                    );

                    attachments.add(file);
                    logger.info("Skonwertowano załącznik JSON na MultipartFile: {}, rozmiar: {} bajtów",
                            name, fileContent.length);

                } catch (Exception e) {
                    logger.error("Błąd podczas konwersji załącznika: {}", e.getMessage(), e);
                }
            }

            if (attachments.isEmpty()) {
                return null;
            }

            return attachments.toArray(new MultipartFile[0]);

        } catch (IOException e) {
            logger.error("Nie można sparsować JSON z załącznikami: {}", e.getMessage(), e);
            return null;
        }
    }
}