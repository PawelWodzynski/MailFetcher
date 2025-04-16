package imap.mail.downloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Globalny handler wyjątków dla operacji związanych z uploadem emaili
 * Używa adnotacji RestControllerAdvice zamiast ControllerAdvice
 */
public class EmailUploadExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(EmailUploadExceptionHandler.class);

    /**
     * Obsługa wyjątku DuplicateKeyException (duplikat w bazie danych)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleDuplicateKeyException(DuplicateKeyException ex) {
        logger.warn("Wykryto duplikat w bazie danych: {}", ex.getMessage());
        System.out.println("UWAGA: Wykryto duplikat w bazie danych: " + ex.getMessage());

        return createErrorResponse("Email już istnieje w bazie danych", ex);
    }

    /**
     * Obsługa wyjątku MaxUploadSizeExceededException (zbyt duży załącznik)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        logger.warn("Przekroczono maksymalny rozmiar pliku: {}", ex.getMessage());
        System.out.println("UWAGA: Przekroczono maksymalny rozmiar pliku: " + ex.getMessage());

        return createErrorResponse("Przekroczono maksymalny rozmiar pliku", ex);
    }

    /**
     * Obsługa ogólnych wyjątków
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneralException(Exception ex) {
        logger.error("Wystąpił nieoczekiwany błąd: {}", ex.getMessage(), ex);
        System.out.println("BŁĄD: Wystąpił nieoczekiwany błąd: " + ex.getMessage());
        ex.printStackTrace();

        return createErrorResponse("Wystąpił nieoczekiwany błąd podczas przetwarzania żądania", ex);
    }

    /**
     * Tworzy standardową mapę odpowiedzi dla błędów
     */
    public static Map<String, Object> createErrorResponse(String message, Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", ex.getMessage());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}