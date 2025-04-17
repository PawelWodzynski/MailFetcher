package imap.mail.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Kontroler REST do obsługi uploadu emaili z załącznikami
 */
@RestController
@RequestMapping("/api/mails")
public class EmailUploadController {

    private static final Logger logger = LoggerFactory.getLogger(EmailUploadController.class);

    private final EmailUploadService emailUploadService;
    private final ObjectMapper objectMapper;

    @Autowired
    public EmailUploadController(EmailUploadService emailUploadService, ObjectMapper objectMapper) {
        this.emailUploadService = emailUploadService;
        this.objectMapper = objectMapper;
    }

    /**
     * Endpoint do przyjmowania emaili wraz z załącznikami z zewnętrznego źródła (np. N8N)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadEmail(
            @RequestParam(value = "emailContent", required = false) String emailContent,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to, // Dodane pole 'to'
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam(value = "attachment", required = false) MultipartFile[] attachments) {

        try { 
            // Logowanie informacji o żądaniu
            logger.info("Rozpoczęto przetwarzanie żądania uploadEmail");
            logger.debug("Parametry: subject={}, from={}, to={}, attachments={}",
                    subject,
                    from,
                    to,
                    attachments != null ? attachments.length : 0);

            // Sprawdź, czy mamy załączniki w parametrach HTTP czy w metadanych JSON
            MultipartFile[] effectiveAttachments = attachments;

            if ((attachments == null || attachments.length == 0) && metadataJson != null && !metadataJson.isEmpty()) {
                // Sprawdź czy metadane zawierają załączniki
                logger.info("Brak załączników w parametrach HTTP, sprawdzanie w metadanych JSON");

                try {
                    // Sprawdź czy metadane zawierają załączniki
                    JsonNode rootNode = objectMapper.readTree(metadataJson);
                    JsonNode attachmentsNode = rootNode.get("attachments");

                    if (attachmentsNode != null && attachmentsNode.isArray() && !attachmentsNode.isEmpty()) {
                        logger.info("Znaleziono załączniki w metadanych JSON, konwertowanie do MultipartFile");

                        // Konwertuj załączniki z JSON do MultipartFile[]
                        effectiveAttachments = JsonMultipartConverter.convertJsonToMultipartFiles(metadataJson);

                        if (effectiveAttachments != null) {
                            logger.info("Skonwertowano {} załączników z JSON", effectiveAttachments.length);

                            // Usuń załączniki z metadanych, aby uniknąć duplikacji
                            ObjectMapper cleanMapper = new ObjectMapper();
                            Map<String, Object> metadataMap = cleanMapper.readValue(metadataJson, Map.class);
                            metadataMap.remove("attachments");
                            metadataJson = cleanMapper.writeValueAsString(metadataMap);

                            logger.debug("Zaktualizowano metadane JSON, usunięto załączniki");
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Błąd podczas analizy załączników JSON: {}", e.getMessage());
                    logger.debug("Szczegóły błędu:", e);
                }
            }

            // Logowanie informacji o załącznikach
            if (effectiveAttachments != null && effectiveAttachments.length > 0) {
                for (int i = 0; i < effectiveAttachments.length; i++) {
                    MultipartFile file = effectiveAttachments[i];
                    if (file != null && !file.isEmpty()) {
                        logger.info("Załącznik {}: {} (rozmiar: {} bajtów, typ: {})",
                                i,
                                file.getOriginalFilename(),
                                file.getSize(),
                                file.getContentType());
                    } else {
                        logger.warn("Załącznik {} jest pusty lub NULL", i);
                    }
                }
            }

            // Delegowanie logiki do serwisu
            EmailUploadService.UploadResult result = emailUploadService.processEmailUpload(
                    emailContent, subject, from, to, metadataJson, effectiveAttachments);

            // Zwracanie odpowiedzi na podstawie wyniku
            logger.info("Zakończono przetwarzanie żądania: success={}, emailId={}, attachmentsCount={}",
                    result.isSuccess(),
                    result.getEmailId(),
                    result.getAttachmentsCount());

            return ResponseEntity
                    .status(result.getStatus())
                    .body(result.toResponseMap());

        } catch (MaxUploadSizeExceededException e) {
            // Obsługa przekroczenia rozmiaru pliku
            logger.warn("Przekroczono maksymalny rozmiar pliku: {}", e.getMessage());
            System.out.println("UWAGA: Przekroczono maksymalny rozmiar pliku: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(createErrorResponse("Przekroczono maksymalny rozmiar pliku", e));

        } catch (Exception e) {
            // Ogólna obsługa błędów
            logger.error("Błąd podczas przetwarzania emaila: {}", e.getMessage(), e);
            System.out.println("BŁĄD podczas przetwarzania emaila: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Błąd podczas przetwarzania emaila", e));
        }
    }

    /**
     * Endpoint testowy do sprawdzenia czy serwis działa
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "API działa poprawnie");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Tworzy standardową mapę odpowiedzi dla błędów
     */
    private Map<String, Object> createErrorResponse(String message, Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", e.getMessage());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}