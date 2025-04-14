package imap.mail.downloader;

import imap.mail.downloader.model.Mail;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MailService {
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final ImapMailService imapMailService;
    private final MailRepository mailRepository;
    private final AttachmentRepository attachmentRepository;

    @Autowired
    public MailService(ImapMailService imapMailService,
                       MailRepository mailRepository,
                       AttachmentRepository attachmentRepository) {
        this.imapMailService = imapMailService;
        this.mailRepository = mailRepository;
        this.attachmentRepository = attachmentRepository;

        logger.info("MailService utworzony z zależnościami: ImapMailService, MailRepository, AttachmentRepository");
        System.out.println("MailService utworzony z zależnościami: ImapMailService, MailRepository, AttachmentRepository");
    }

    /**
     * Pobiera wiadomości z serwera IMAP i zapisuje je w MongoDB, unikając duplikatów
     */
    public List<Mail> fetchAndSaveEmails(String host, int port, String username, String password, String folder) {
        logger.info("Rozpoczynam pobieranie maili z {}:{} dla użytkownika {}", host, port, username);
        System.out.println("Rozpoczynam pobieranie maili z " + host + ":" + port + " dla użytkownika " + username);

        // Pobierz maile z serwera IMAP
        List<Mail> fetchedMails = imapMailService.fetchAllMails(host, port, username, password, folder);
        logger.info("ImapMailService zwrócił {} wiadomości", fetchedMails.size());
        System.out.println("ImapMailService zwrócił " + fetchedMails.size() + " wiadomości");

        List<Mail> savedMails = new ArrayList<>();
        int newMailsCount = 0;
        int duplicatesCount = 0;

        for (Mail mail : fetchedMails) {
            logger.debug("Przetwarzanie maila: {}", mail.getTitle());
            System.out.println("Przetwarzanie maila: " + mail.getTitle());

            // Sprawdzenie czy mail to duplikat
            if (isDuplicate(mail)) {
                logger.info("Mail '{}' już istnieje w bazie danych - pomijam", mail.getTitle());
                System.out.println("Mail '" + mail.getTitle() + "' już istnieje w bazie danych - pomijam");
                duplicatesCount++;
                continue;
            }

            try {
                // Konwersja modelu Mail na dokument MongoDB
                MailDocument mailDocument = convertToMailDocument(mail);
                logger.debug("Skonwertowano mail do dokumentu MongoDB");

                // Zapisz dokument maila
                MailDocument savedMail;
                try {
                    savedMail = mailRepository.save(mailDocument);
                    logger.info("Zapisano mail w bazie MongoDB z ID: {}", savedMail.getId());
                    System.out.println("Zapisano mail w bazie MongoDB z ID: " + savedMail.getId());
                    newMailsCount++;
                } catch (DuplicateKeyException e) {
                    logger.warn("Wykryto duplikat podczas próby zapisu (konflikt unikalnego indeksu): {}", e.getMessage());
                    System.out.println("Wykryto duplikat podczas próby zapisu: " + e.getMessage());
                    duplicatesCount++;
                    continue;
                }

                // Pobierz załączniki i zapisz je jako osobne dokumenty
                if (mail.getAttachments() != null && !mail.getAttachments().isEmpty()) {
                    logger.info("Mail ma {} załączników do zapisania", mail.getAttachments().size());
                    System.out.println("Mail ma " + mail.getAttachments().size() + " załączników do zapisania");

                    for (MailAttachment attachment : mail.getAttachments()) {
                        logger.debug("Przetwarzanie załącznika: {}", attachment.getFileName());

                        // Sprawdź czy załącznik już istnieje
                        if (attachmentExists(savedMail.getId(), attachment.getFileName())) {
                            logger.info("Załącznik '{}' już istnieje dla maila {} - pomijam",
                                    attachment.getFileName(), savedMail.getId());
                            System.out.println("Załącznik '" + attachment.getFileName() +
                                    "' już istnieje dla maila " + savedMail.getId() + " - pomijam");
                            continue;
                        }

                        AttachmentDocument attachmentDocument = convertToAttachmentDocument(attachment, savedMail.getId());
                        logger.debug("Skonwertowano załącznik do dokumentu MongoDB");

                        // Zapisz dokument załącznika
                        AttachmentDocument savedAttachment = attachmentRepository.save(attachmentDocument);
                        logger.info("Zapisano załącznik w bazie MongoDB z ID: {}", savedAttachment.getId());
                        System.out.println("Zapisano załącznik w bazie MongoDB z ID: " + savedAttachment.getId());

                        // Dodaj referencję do załącznika w dokumencie maila
                        savedMail.addAttachmentId(savedAttachment.getId());
                        logger.debug("Dodano referencję do załącznika w dokumencie maila");
                    }

                    // Aktualizuj dokument maila z referencjami do załączników
                    mailRepository.save(savedMail);
                    logger.debug("Zaktualizowano dokument maila z referencjami do załączników");
                }

                // Konwersja dokumentu z powrotem na model Mail (bez danych binarnych załączników)
                Mail savedMailModel = convertToMailModel(savedMail);
                savedMails.add(savedMailModel);
                logger.debug("Dodano zapisany mail do listy wynikowej");
            } catch (Exception e) {
                logger.error("Błąd podczas zapisywania maila: {}", e.getMessage(), e);
                System.out.println("BŁĄD podczas zapisywania maila: " + e.getMessage());
                e.printStackTrace();
            }
        }

        logger.info("Zakończono przetwarzanie. Zapisano {} nowych maili, pominięto {} duplikatów",
                newMailsCount, duplicatesCount);
        System.out.println("Zakończono przetwarzanie. Zapisano " + newMailsCount +
                " nowych maili, pominięto " + duplicatesCount + " duplikatów");
        return savedMails;
    }

    /**
     * Sprawdza, czy mail już istnieje w bazie danych
     */
    private boolean isDuplicate(Mail mail) {
        // Najpierw sprawdź po Message-ID (najbardziej wiarygodny sposób)
        if (mail.getMessageId() != null && !mail.getMessageId().isEmpty()) {
            boolean exists = mailRepository.existsByMessageId(mail.getMessageId());
            if (exists) {
                logger.debug("Znaleziono duplikat przez Message-ID: {}", mail.getMessageId());
                return true;
            }
        }

        // Jeśli brak Message-ID, sprawdź po kombinacji pól
        if (mail.getSenderEmail() != null && mail.getTitle() != null && mail.getSentDate() != null) {
            List<MailDocument> existingMails = mailRepository.findBySenderEmailAndRecipientEmailAndTitleAndSentDate(
                    mail.getSenderEmail(),
                    mail.getRecipientEmail(),
                    mail.getTitle(),
                    mail.getSentDate()
            );

            if (!existingMails.isEmpty()) {
                logger.debug("Znaleziono duplikat przez kombinację pól: nadawca={}, tytuł={}, data={}",
                        mail.getSenderEmail(), mail.getTitle(), mail.getSentDate());
                return true;
            }
        }

        return false;
    }

    /**
     * Sprawdza, czy załącznik o podanej nazwie już istnieje dla danego maila
     */
    private boolean attachmentExists(String mailId, String fileName) {
        List<AttachmentDocument> existingAttachments = attachmentRepository.findByMailId(mailId);
        return existingAttachments.stream()
                .anyMatch(attachment -> fileName.equals(attachment.getFileName()));
    }

    /**
     * Pobiera wszystkie zapisane maile z bazy danych
     */
    public List<Mail> getAllSavedEmails() {
        logger.info("Pobieranie wszystkich zapisanych maili z bazy danych");
        List<MailDocument> mailDocuments = mailRepository.findAll();
        logger.info("Znaleziono {} maili w bazie danych", mailDocuments.size());
        return mailDocuments.stream()
                .map(this::convertToMailModel)
                .collect(Collectors.toList());
    }

    /**
     * Pobiera mail po ID
     */
    public Optional<Mail> getMailById(String id) {
        logger.info("Pobieranie maila o ID: {}", id);
        return mailRepository.findById(id)
                .map(this::convertToMailModel);
    }

    /**
     * Wyszukuje maile po tytule
     */
    public List<Mail> searchMailsByTitle(String title) {
        logger.info("Wyszukiwanie maili po tytule: {}", title);
        List<MailDocument> mailDocuments = mailRepository.findByTitleContainingIgnoreCase(title);
        logger.info("Znaleziono {} maili pasujących do tytułu", mailDocuments.size());
        return mailDocuments.stream()
                .map(this::convertToMailModel)
                .collect(Collectors.toList());
    }

    /**
     * Wyszukuje maile po adresie nadawcy
     */
    public List<Mail> searchMailsBySender(String senderEmail) {
        logger.info("Wyszukiwanie maili po nadawcy: {}", senderEmail);
        List<MailDocument> mailDocuments = mailRepository.findBySenderEmailContainingIgnoreCase(senderEmail);
        logger.info("Znaleziono {} maili od podanego nadawcy", mailDocuments.size());
        return mailDocuments.stream()
                .map(this::convertToMailModel)
                .collect(Collectors.toList());
    }

    /**
     * Pobiera załącznik po ID
     */
    public Optional<AttachmentDocument> getAttachmentById(String id) {
        logger.info("Pobieranie załącznika o ID: {}", id);
        return attachmentRepository.findById(id);
    }

    /**
     * Pobiera wszystkie załączniki dla danego maila
     */
    public List<AttachmentDocument> getAttachmentsForMail(String mailId) {
        logger.info("Pobieranie załączników dla maila o ID: {}", mailId);
        List<AttachmentDocument> attachments = attachmentRepository.findByMailId(mailId);
        logger.info("Znaleziono {} załączników dla maila", attachments.size());
        return attachments;
    }

    /**
     * Konwertuje model Mail na dokument MongoDB
     */
    private MailDocument convertToMailDocument(Mail mail) {
        logger.debug("Konwertowanie Mail -> MailDocument");

        MailDocument document = new MailDocument();
        document.setTitle(mail.getTitle());
        document.setSenderEmail(mail.getSenderEmail());
        document.setRecipientEmail(mail.getRecipientEmail());
        document.setContent(mail.getContent());
        document.setSentDate(mail.getSentDate());
        document.setMessageId(mail.getMessageId());

        logger.debug("Konwersja Mail -> MailDocument zakończona");
        return document;
    }

    /**
     * Konwertuje model MailAttachment na dokument MongoDB
     */
    private AttachmentDocument convertToAttachmentDocument(MailAttachment attachment, String mailId) {
        logger.debug("Konwertowanie MailAttachment -> AttachmentDocument");

        AttachmentDocument document = new AttachmentDocument();
        document.setMailId(mailId);
        document.setFileName(attachment.getFileName());
        document.setContentType(attachment.getContentType());
        document.setSize(attachment.getSize());

        if (attachment.getData() != null) {
            document.setFileData(new Binary(attachment.getData()));
            logger.debug("Ustawiono dane binarne załącznika, rozmiar: {} bajtów", attachment.getData().length);
        } else {
            logger.warn("Dane załącznika są NULL!");
            System.out.println("UWAGA: Dane załącznika są NULL!");
        }

        logger.debug("Konwersja MailAttachment -> AttachmentDocument zakończona");
        return document;
    }

    /**
     * Konwertuje dokument MongoDB na model Mail (bez danych binarnych załączników)
     */
    private Mail convertToMailModel(MailDocument document) {
        logger.debug("Konwertowanie MailDocument -> Mail");

        Mail mail = new Mail();
        mail.setTitle(document.getTitle());
        mail.setSenderEmail(document.getSenderEmail());
        mail.setRecipientEmail(document.getRecipientEmail());
        mail.setContent(document.getContent());
        mail.setSentDate(document.getSentDate());
        mail.setMessageId(document.getMessageId());

        // Dodaj informacje o załącznikach (bez danych binarnych)
        if (document.getAttachmentIds() != null && !document.getAttachmentIds().isEmpty()) {
            logger.debug("Dokument ma {} referencji do załączników", document.getAttachmentIds().size());

            List<AttachmentDocument> attachments = attachmentRepository.findAllById(document.getAttachmentIds());
            logger.debug("Pobrano {} załączników z bazy danych", attachments.size());

            for (AttachmentDocument attachmentDoc : attachments) {
                MailAttachment attachment = new MailAttachment();
                attachment.setFileName(attachmentDoc.getFileName());
                attachment.setContentType(attachmentDoc.getContentType());
                attachment.setSize(attachmentDoc.getSize());
                // Nie ustawiamy danych binarnych załącznika

                try {
                    mail.addAttachment(attachment);
                } catch (Exception e) {
                    logger.error("Błąd podczas dodawania załącznika do modelu Mail: {}", e.getMessage(), e);
                    System.out.println("BŁĄD podczas dodawania załącznika do modelu Mail: " + e.getMessage());
                }
            }

            logger.debug("Dodano {} załączników do modelu Mail", mail.getAttachments().size());
        }

        logger.debug("Konwersja MailDocument -> Mail zakończona");
        return mail;
    }
}