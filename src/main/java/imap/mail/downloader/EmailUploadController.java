package imap.mail.downloader;

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

    @Autowired
    public EmailUploadController(EmailUploadService emailUploadService) {
        this.emailUploadService = emailUploadService;
    }

    /**
     * Endpoint do przyjmowania emaili wraz z załącznikami z zewnętrznego źródła (np. N8N)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadEmail(
            @RequestParam(value = "emailContent", required = false) String emailContent,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam(value = "attachment", required = false) MultipartFile[] attachments) {

        try {
            // Logowanie informacji o żądaniu
            logger.info("Rozpoczęto przetwarzanie żądania uploadEmail");
            logger.debug("Parametry: subject={}, from={}, attachments={}",
                    subject,
                    from,
                    attachments != null ? attachments.length : 0);

            if (attachments != null && attachments.length > 0) {
                for (int i = 0; i < attachments.length; i++) {
                    MultipartFile file = attachments[i];
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
                    emailContent, subject, from, metadataJson, attachments);

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