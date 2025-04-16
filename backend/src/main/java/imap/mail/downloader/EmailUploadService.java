package imap.mail.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Serwis do obsługi uploadu emaili z zewnętrznych źródeł (np. N8N)
 */
@Service
public class EmailUploadService {

    private static final Logger logger = LoggerFactory.getLogger(EmailUploadService.class);

    private final MailRepository mailRepository;
    private final AttachmentRepository attachmentRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public EmailUploadService(
            MailRepository mailRepository,
            AttachmentRepository attachmentRepository,
            ObjectMapper objectMapper) {
        this.mailRepository = mailRepository;
        this.attachmentRepository = attachmentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Główna metoda do przetwarzania emaila z załącznikami
     * @return Mapa z wynikami operacji
     * @throws DuplicateKeyException jeśli email jest duplikatem
     */
    public UploadResult processEmailUpload(
            String emailContent,
            String subject,
            String from,
            String to, // Dodane pole 'to'
            String metadataJson,
            MultipartFile[] attachments) throws DuplicateKeyException {

        logReceivedEmail(from, subject);

        // Parsowanie metadanych
        Map<String, Object> metadata = parseMetadataJson(metadataJson);

        // Ekstrakcja danych z emaila
        EmailData emailData = extractEmailData(from, to, metadata);

        // Sprawdzenie czy mail już istnieje w bazie
        MailDocument mailDocument = null;
        boolean isNewEmail = true;
        boolean hasDuplicate = false;

        if (emailData.getMessageId() != null && !emailData.getMessageId().isEmpty()) {
            mailDocument = findExistingEmail(emailData.getMessageId());
            if (mailDocument != null) {
                isNewEmail = false;
                hasDuplicate = true;
                logger.info("Znaleziono istniejący email z Message-ID: {}, będę używać istniejącego ID",
                        emailData.getMessageId());
                System.out.println("Znaleziono istniejący email z Message-ID: " + emailData.getMessageId() +
                        ", będę używać istniejącego ID");
            }
        }

        // Jeśli mail nie istnieje, stwórz nowy
        if (isNewEmail) {
            mailDocument = createAndSaveEmailDocument(subject, emailData, emailContent);
            logger.info("Utworzono nowy email w bazie MongoDB z ID: {}", mailDocument.getId());
            System.out.println("Utworzono nowy email w bazie MongoDB z ID: " + mailDocument.getId());
        }

        // Przetwarzanie załączników
        int attachmentsCount = processAttachments(attachments, mailDocument);

        // Zwracanie odpowiedniego komunikatu
        String message = isNewEmail ?
                "Email zapisany pomyślnie" :
                "Załączniki dodane do istniejącego emaila";

        return new UploadResult(true, message,
                mailDocument.getId(), attachmentsCount, HttpStatus.OK);
    }

    /**
     * Logowanie informacji o otrzymanym emailu
     */
    private void logReceivedEmail(String from, String subject) {
        logger.info("Otrzymano nowy email od: {}, temat: {}", from, subject);
        System.out.println("Otrzymano nowy email od: " + from + ", temat: " + subject);
    }

    /**
     * Parsowanie metadanych JSON
     */
    private Map<String, Object> parseMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
            logger.debug("Sparsowano metadane: {}", metadata.keySet());
            return metadata;
        } catch (IOException e) {
            logger.warn("Nie można sparsować metadanych JSON: {}", e.getMessage());
            System.out.println("UWAGA: Nie można sparsować metadanych JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Klasa pomocnicza do przechowywania danych emaila
     */
    public static class EmailData {
        private final String senderEmail;
        private final String recipientEmail;
        private final String messageId;
        private final Date sentDate;

        public EmailData(String senderEmail, String recipientEmail, String messageId, Date sentDate) {
            this.senderEmail = senderEmail;
            this.recipientEmail = recipientEmail;
            this.messageId = messageId;
            this.sentDate = sentDate;
        }

        public String getSenderEmail() {
            return senderEmail;
        }

        public String getRecipientEmail() {
            return recipientEmail;
        }

        public String getMessageId() {
            return messageId;
        }

        public Date getSentDate() {
            return sentDate;
        }
    }

    /**
     * Ekstrakcja danych z emaila i metadanych
     */
    private EmailData extractEmailData(String from, String to, Map<String, Object> metadata) {
        String senderEmail = extractEmailAddress(from);
        String recipientEmail = to != null && !to.isEmpty() ?
                extractEmailAddress(to) : extractRecipientFromMetadata(metadata);
        String messageId = extractMessageIdFromMetadata(metadata);
        Date sentDate = extractDateFromMetadata(metadata);

        return new EmailData(senderEmail, recipientEmail, messageId, sentDate);
    }

    /**
     * Znajduje istniejący email na podstawie Message-ID
     * @return Dokument emaila lub null jeśli nie znaleziono
     */
    private MailDocument findExistingEmail(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return null;
        }

        return mailRepository.findByMessageId(messageId);
    }

    /**
     * Tworzenie i zapisywanie dokumentu email
     */
    private MailDocument createAndSaveEmailDocument(
            String subject, EmailData emailData, String emailContent) {

        MailDocument mailDocument = new MailDocument();
        mailDocument.setTitle(subject);
        mailDocument.setSenderEmail(emailData.getSenderEmail());
        mailDocument.setRecipientEmail(emailData.getRecipientEmail());
        mailDocument.setContent(emailContent);
        mailDocument.setMessageId(emailData.getMessageId());

        if (emailData.getSentDate() != null) {
            mailDocument.setSentDate(emailData.getSentDate());
        } else {
            mailDocument.setSentDate(new Date());
        }

        mailDocument.setSavedDate(new Date());
        mailDocument.setAttachmentIds(new ArrayList<>());

        MailDocument savedMail = mailRepository.save(mailDocument);

        return savedMail;
    }

    /**
     * Przetwarzanie załączników emaila
     * @return liczba przetworzonych załączników
     */
    private int processAttachments(MultipartFile[] attachments, MailDocument mailDocument) {
        if (attachments == null || attachments.length == 0) {
            logger.info("Brak załączników do przetworzenia");
            return 0;
        }

        logger.info("Przetwarzanie {} załączników dla emaila {}",
                attachments.length, mailDocument.getId());
        System.out.println("Przetwarzanie " + attachments.length + " załączników dla emaila " +
                mailDocument.getId());

        int attachmentsCount = 0;
        for (MultipartFile file : attachments) {
            if (processAttachment(file, mailDocument)) {
                attachmentsCount++;
            }
        }

        // Aktualizacja dokumentu emaila z referencjami do załączników
        if (attachmentsCount > 0) {
            mailRepository.save(mailDocument);
            logger.info("Zaktualizowano email {} z {} nowymi referencjami do załączników",
                    mailDocument.getId(), attachmentsCount);
            System.out.println("Zaktualizowano email " + mailDocument.getId() +
                    " z " + attachmentsCount + " nowymi referencjami do załączników");
        }

        return attachmentsCount;
    }

    /**
     * Przetwarzanie pojedynczego załącznika
     * @return true jeśli załącznik został pomyślnie przetworzony
     */
    private boolean processAttachment(MultipartFile file, MailDocument mailDocument) {
        if (file == null || file.isEmpty()) {
            logger.warn("Pominięto pusty załącznik");
            return false;
        }

        logger.info("Przetwarzanie załącznika: {} (rozmiar: {} bajtów)",
                file.getOriginalFilename(), file.getSize());

        // Sprawdź rozmiar pliku
        if (file.getSize() > 10 * 1024 * 1024) { // limit 10MB
            logger.warn("Załącznik {} jest zbyt duży: {} bajtów",
                    file.getOriginalFilename(), file.getSize());
            System.out.println("UWAGA: Załącznik " + file.getOriginalFilename() +
                    " jest zbyt duży: " + file.getSize() + " bajtów");
            return false;
        }

        try {
            // Sprawdzenie czy załącznik już istnieje dla tego maila
            if (isAttachmentDuplicate(mailDocument.getId(), file.getOriginalFilename())) {
                logger.info("Załącznik {} już istnieje dla emaila {}, pomijam",
                        file.getOriginalFilename(), mailDocument.getId());
                System.out.println("Załącznik " + file.getOriginalFilename() +
                        " już istnieje dla emaila " + mailDocument.getId() + ", pomijam");
                return false;
            }

            // Tworzenie i zapisywanie dokumentu załącznika
            AttachmentDocument attachmentDocument = createAttachmentDocument(file, mailDocument.getId());
            AttachmentDocument savedAttachment = saveAttachmentDocument(attachmentDocument);

            // Dodanie referencji do załącznika w dokumencie email
            mailDocument.addAttachmentId(savedAttachment.getId());
            return true;

        } catch (IOException e) {
            logger.error("Błąd podczas przetwarzania załącznika {}: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            System.out.println("BŁĄD podczas przetwarzania załącznika " +
                    file.getOriginalFilename() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sprawdzenie czy załącznik jest duplikatem
     */
    private boolean isAttachmentDuplicate(String mailId, String fileName) {
        boolean isDuplicate = attachmentRepository.existsByMailIdAndFileName(mailId, fileName);
        if (isDuplicate) {
            logger.warn("Załącznik o nazwie {} już istnieje dla tego maila", fileName);
            System.out.println("UWAGA: Załącznik o nazwie " + fileName + " już istnieje dla tego maila");
        }
        return isDuplicate;
    }

    /**
     * Tworzenie dokumentu załącznika z ulepszoną obsługą danych
     */
    private AttachmentDocument createAttachmentDocument(MultipartFile file, String mailId)
            throws IOException {
        AttachmentDocument attachmentDocument = new AttachmentDocument();
        attachmentDocument.setMailId(mailId);
        attachmentDocument.setFileName(file.getOriginalFilename());
        attachmentDocument.setContentType(file.getContentType());

        // Odczytywanie danych bezpośrednio ze strumienia
        try (InputStream is = file.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384]; // 16KB bufor

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] byteArray = buffer.toByteArray();

            logger.info("Odczytano {} bajtów z pliku {}", byteArray.length, file.getOriginalFilename());
            System.out.println("Odczytano " + byteArray.length + " bajtów z pliku " + file.getOriginalFilename());

            attachmentDocument.setSize(byteArray.length);

            if (byteArray.length == 0) {
                logger.warn("UWAGA: Plik {} ma 0 bajtów!", file.getOriginalFilename());
                System.out.println("UWAGA: Plik " + file.getOriginalFilename() + " ma 0 bajtów!");
            }

            attachmentDocument.setFileData(new Binary(byteArray));
        }

        return attachmentDocument;
    }

    /**
     * Zapisywanie dokumentu załącznika z dodatkowym logowaniem
     */
    private AttachmentDocument saveAttachmentDocument(AttachmentDocument attachmentDocument) {
        // Sprawdź dane przed zapisem
        if (attachmentDocument.getFileData() == null) {
            logger.error("Błąd: Próba zapisu załącznika z NULL w polu fileData");
            System.out.println("BŁĄD: Próba zapisu załącznika z NULL w polu fileData");
        } else {
            logger.info("Zapisywanie załącznika: {} (rozmiar danych: {} bajtów)",
                    attachmentDocument.getFileName(),
                    attachmentDocument.getFileData().getData().length);
            System.out.println("Zapisywanie załącznika: " + attachmentDocument.getFileName() +
                    " (rozmiar danych: " + attachmentDocument.getFileData().getData().length + " bajtów)");
        }

        AttachmentDocument savedAttachment = attachmentRepository.save(attachmentDocument);

        // Sprawdź dane po zapisie
        if (savedAttachment.getFileData() == null) {
            logger.error("Błąd: Po zapisie załącznika pole fileData jest NULL");
            System.out.println("BŁĄD: Po zapisie załącznika pole fileData jest NULL");
        } else {
            logger.info("Załącznik zapisany: {} (rozmiar danych w bazie: {} bajtów)",
                    savedAttachment.getFileName(),
                    savedAttachment.getFileData().getData().length);
            System.out.println("Załącznik zapisany: " + savedAttachment.getFileName() +
                    " (rozmiar danych w bazie: " + savedAttachment.getFileData().getData().length + " bajtów)");
        }

        return savedAttachment;
    }

    /**
     * Wyciągnięcie adresu email z pola "From" lub "To"
     */
    private String extractEmailAddress(String emailField) {
        if (emailField == null || emailField.isEmpty()) {
            return "";
        }

        int startPos = emailField.indexOf('<');
        int endPos = emailField.indexOf('>');

        if (startPos >= 0 && endPos > startPos) {
            return emailField.substring(startPos + 1, endPos);
        }

        return emailField;
    }

    /**
     * Wyciągnięcie odbiorcy z metadanych
     */
    private String extractRecipientFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return "";
        }

        if (metadata.containsKey("delivered-to")) {
            return metadata.get("delivered-to").toString();
        } else if (metadata.containsKey("envelope-to")) {
            return metadata.get("envelope-to").toString();
        } else if (metadata.containsKey("to")) {
            Object toValue = metadata.get("to");
            if (toValue != null) {
                return extractEmailAddress(toValue.toString());
            }
        }

        return "";
    }

    /**
     * Wyciągnięcie Message-ID z metadanych
     */
    private String extractMessageIdFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }

        if (metadata.containsKey("message-id") || metadata.containsKey("messageId")) {
            String messageId = metadata.containsKey("message-id") ?
                    metadata.get("message-id").toString() :
                    metadata.get("messageId").toString();
            return messageId.replaceAll("[<>]", "");
        }

        return null;
    }

    /**
     * Wyciągnięcie daty z metadanych
     */
    private Date extractDateFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }

        try {
            if (metadata.containsKey("date")) {
                String dateStr = metadata.get("date").toString();
                // Tutaj można dodać parser daty, na przykład:
                // SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
                // return sdf.parse(dateStr);

                // Prosty parser daty z formatu ISO
                try {
                    return new Date(dateStr);
                } catch (Exception e) {
                    logger.warn("Nie można sparsować daty z metadanych: {}", e.getMessage());
                    return null;
                }
            }
        } catch (Exception e) {
            logger.warn("Nie można sparsować daty z metadanych: {}", e.getMessage());
            System.out.println("UWAGA: Nie można sparsować daty z metadanych: " + e.getMessage());
        }

        return null;
    }

    /**
     * Klasa wynikowa do zwracania wyników operacji upload
     */
    public static class UploadResult {
        private final boolean success;
        private final String message;
        private final String emailId;
        private final int attachmentsCount;
        private final HttpStatus status;

        public UploadResult(boolean success, String message, String emailId,
                            int attachmentsCount, HttpStatus status) {
            this.success = success;
            this.message = message;
            this.emailId = emailId;
            this.attachmentsCount = attachmentsCount;
            this.status = status;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getEmailId() {
            return emailId;
        }

        public int getAttachmentsCount() {
            return attachmentsCount;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public Map<String, Object> toResponseMap() {
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", message);

            if (emailId != null) {
                response.put("emailId", emailId);
            }

            response.put("attachmentsCount", attachmentsCount);
            return response;
        }
    }
}