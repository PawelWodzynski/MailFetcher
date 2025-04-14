package imap.mail.downloader;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface MailRepository extends MongoRepository<MailDocument, String> {

    List<MailDocument> findBySenderEmailContainingIgnoreCase(String senderEmail);

    List<MailDocument> findByTitleContainingIgnoreCase(String title);

    List<MailDocument> findByRecipientEmailContainingIgnoreCase(String recipientEmail);

    // Metody do wykrywania duplikatów

    /**
     * Sprawdza czy istnieje wiadomość o podanym Message-ID
     */
    boolean existsByMessageId(String messageId);

    /**
     * Znajduje wiadomość po Message-ID
     */
    MailDocument findByMessageId(String messageId);

    /**
     * Znajduje wiadomości po kombinacji kluczowych pól (gdy Message-ID nie jest dostępny)
     */
    List<MailDocument> findBySenderEmailAndRecipientEmailAndTitleAndSentDate(
            String senderEmail,
            String recipientEmail,
            String title,
            Date sentDate
    );

    /**
     * Znajduje wiadomości po kombinacji pól - bez odbiorcy (alternatywna metoda)
     */
    List<MailDocument> findBySenderEmailAndTitleAndSentDate(
            String senderEmail,
            String title,
            Date sentDate
    );

    /**
     * Sprawdza czy istnieje wiadomość o podanym tytule i dacie wysłania od danego nadawcy
     */
    boolean existsBySenderEmailAndTitleAndSentDate(
            String senderEmail,
            String title,
            Date sentDate
    );
}