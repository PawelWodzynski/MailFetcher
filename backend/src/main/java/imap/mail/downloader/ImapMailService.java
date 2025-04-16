package imap.mail.downloader;

import imap.mail.downloader.model.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class ImapMailService {
    private static final Logger logger = LoggerFactory.getLogger(ImapMailService.class);

    public List<Mail> fetchAllMails(String host, int port, String username, String password, String folderName) {
        logger.info("Próba połączenia z serwerem IMAP: {}:{} użytkownik: {}", host, port, username);
        System.out.println("Próba połączenia z serwerem IMAP: " + host + ":" + port + " użytkownik: " + username);

        List<Mail> mails = new ArrayList<>();

        try {
            // Konfiguracja właściwości
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "imaps");
            properties.put("mail.imaps.host", host);
            properties.put("mail.imaps.port", port);
            properties.put("mail.imaps.ssl.enable", "true");
            properties.put("mail.debug", "true");  // Włącz szczegółowe logowanie JavaMail

            // Tworzenie sesji
            Session session = Session.getDefaultInstance(properties);

            // Łączenie ze skrzynką pocztową
            logger.info("Łączenie ze skrzynką pocztową...");
            System.out.println("Łączenie ze skrzynką pocztową...");
            Store store = session.getStore("imaps");
            store.connect(host, username, password);
            logger.info("Połączono z serwerem IMAP");
            System.out.println("Połączono z serwerem IMAP");

            // Otwieranie folderu
            logger.info("Otwieranie folderu: {}", folderName);
            System.out.println("Otwieranie folderu: " + folderName);
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            // Pobieranie wiadomości
            Message[] messages = folder.getMessages();
            logger.info("Znaleziono {} wiadomości w folderze {}", messages.length, folderName);
            System.out.println("Znaleziono " + messages.length + " wiadomości w folderze " + folderName);

            // Przetwarzanie wiadomości
            for (int i = 0; i < messages.length; i++) {
                Message message = messages[i];
                logger.info("Przetwarzanie wiadomości {}/{}: {}", (i+1), messages.length, message.getSubject());
                System.out.println("Przetwarzanie wiadomości " + (i+1) + "/" + messages.length + ": " + message.getSubject());

                Mail mail = new Mail();
                mail.setTitle(message.getSubject());
                mail.setSentDate(message.getSentDate());

                // Pobieranie Message-ID
                String[] messageIdHeader = message.getHeader("Message-ID");
                if (messageIdHeader != null && messageIdHeader.length > 0) {
                    mail.setMessageId(messageIdHeader[0]);
                    logger.debug("Ustawiono Message-ID: {}", messageIdHeader[0]);
                    System.out.println("Ustawiono Message-ID: " + messageIdHeader[0]);
                } else {
                    logger.warn("Brak Message-ID dla wiadomości: {}", message.getSubject());
                    System.out.println("Brak Message-ID dla wiadomości: " + message.getSubject());
                }

                // Pobieranie nadawcy
                if (message.getFrom() != null && message.getFrom().length > 0) {
                    InternetAddress sender = (InternetAddress) message.getFrom()[0];
                    mail.setSenderEmail(sender.getAddress());
                    logger.debug("Nadawca: {}", sender.getAddress());
                }

                // Pobieranie odbiorcy
                if (message.getRecipients(Message.RecipientType.TO) != null &&
                        message.getRecipients(Message.RecipientType.TO).length > 0) {
                    InternetAddress recipient = (InternetAddress) message.getRecipients(Message.RecipientType.TO)[0];
                    mail.setRecipientEmail(recipient.getAddress());
                    logger.debug("Odbiorca: {}", recipient.getAddress());
                }

                // Pobieranie treści i załączników
                try {
                    processContent(message, mail);
                } catch (Exception e) {
                    logger.error("Błąd podczas przetwarzania zawartości wiadomości: {}", e.getMessage());
                    System.out.println("BŁĄD podczas przetwarzania zawartości wiadomości: " + e.getMessage());
                    e.printStackTrace();
                }

                mails.add(mail);
                logger.debug("Dodano wiadomość do listy. Obecna liczba: {}", mails.size());
            }

            // Zamykanie połączenia
            folder.close(false);
            store.close();
            logger.info("Zamknięto połączenie z IMAP. Pobrano {} wiadomości", mails.size());
            System.out.println("Zamknięto połączenie z IMAP. Pobrano " + mails.size() + " wiadomości");

        } catch (MessagingException e) {
            logger.error("Błąd podczas pobierania maili: {}", e.getMessage(), e);
            System.out.println("BŁĄD podczas pobierania maili: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Błąd podczas pobierania maili: " + e.getMessage(), e);
        }

        return mails;
    }

    private void processContent(Message message, Mail mail) throws MessagingException, IOException {
        logger.debug("Przetwarzanie zawartości wiadomości: {}", message.getSubject());
        Object content = message.getContent();

        if (content instanceof String) {
            // Prosta wiadomość tekstowa
            mail.setContent((String) content);
            logger.debug("Ustawiono prostą treść tekstową");
        } else if (content instanceof MimeMultipart) {
            // Wiadomość wieloczęściowa (treść i załączniki)
            processMultipart((MimeMultipart) content, mail);
        } else {
            logger.warn("Nieznany typ zawartości: {}", content.getClass().getName());
            System.out.println("Nieznany typ zawartości: " + content.getClass().getName());
        }
    }

    private void processMultipart(MimeMultipart multipart, Mail mail) throws MessagingException, IOException {
        logger.debug("Przetwarzanie wieloczęściowej wiadomości, liczba części: {}", multipart.getCount());
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            logger.debug("Przetwarzanie części {}/{}, typ zawartości: {}", (i+1), multipart.getCount(), part.getContentType());

            // Sprawdzenie czy część jest załącznikiem
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
                    (part.getFileName() != null && !part.getFileName().isEmpty())) {
                // To jest załącznik
                logger.debug("Część {} jest załącznikiem: {}", (i+1), part.getFileName());
                processAttachment(part, mail);
            } else {
                // To może być treść wiadomości lub zagnieżdżona część
                String contentType = part.getContentType().toLowerCase();
                logger.debug("Część {} nie jest załącznikiem, typ: {}", (i+1), contentType);

                if (part.isMimeType("text/plain")) {
                    String text = (String) part.getContent();
                    textContent.append(text).append("\n");
                    logger.debug("Dodano treść tekstową, długość: {}", text.length());
                } else if (part.isMimeType("text/html")) {
                    // Usuwanie tagów HTML
                    String html = (String) part.getContent();
                    String plainText = html.replaceAll("<[^>]*>", "");
                    textContent.append(plainText).append("\n");
                    logger.debug("Dodano treść HTML (po oczyszczeniu), długość: {}", plainText.length());
                } else if (part.getContent() instanceof MimeMultipart) {
                    // Rekurencyjne przetwarzanie zagnieżdżonych części
                    logger.debug("Znaleziono zagnieżdżoną część wieloczęściową, przetwarzanie rekurencyjne");
                    processMultipart((MimeMultipart) part.getContent(), mail);
                } else {
                    logger.warn("Nieobsługiwany typ części: {}", contentType);
                }
            }
        }

        // Ustawianie treści wiadomości
        String content = textContent.toString().trim();
        if (mail.getContent() == null) {
            mail.setContent(content);
            logger.debug("Ustawiono treść wiadomości, długość: {}", content.length());
        } else {
            String newContent = mail.getContent() + "\n" + content;
            mail.setContent(newContent);
            logger.debug("Dodano do istniejącej treści wiadomości, nowa długość: {}", newContent.length());
        }
    }

    private void processAttachment(Part part, Mail mail) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (fileName == null) {
            fileName = "attachment-" + System.currentTimeMillis();
            logger.debug("Brak nazwy załącznika, wygenerowano tymczasową: {}", fileName);
        } else {
            logger.debug("Przetwarzanie załącznika: {}", fileName);
        }

        // Tworzenie obiektu załącznika
        MailAttachment attachment = new MailAttachment();
        attachment.setFileName(fileName);
        attachment.setContentType(part.getContentType());
        logger.debug("Ustawiono typ zawartości załącznika: {}", part.getContentType());

        // Pobieranie danych załącznika
        InputStream is = part.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        int totalBytes = 0;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        logger.debug("Odczytano {} bajtów danych załącznika", totalBytes);

        // Ustawianie danych załącznika
        byte[] data = baos.toByteArray();
        attachment.setData(data);
        logger.debug("Ustawiono dane załącznika, rozmiar: {} bajtów", data.length);

        try {
            // Dodawanie załącznika do wiadomości
            mail.addAttachment(attachment);
            logger.debug("Dodano załącznik do wiadomości. Obecna liczba załączników: {}", mail.getAttachments().size());
        } catch (Exception e) {
            logger.error("Błąd podczas dodawania załącznika: {}", e.getMessage(), e);
            System.out.println("BŁĄD podczas dodawania załącznika: " + e.getMessage());
            e.printStackTrace();
        }
    }
}