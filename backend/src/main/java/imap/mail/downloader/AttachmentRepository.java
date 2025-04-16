package imap.mail.downloader;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends MongoRepository<AttachmentDocument, String> {

    /**
     * Znajduje wszystkie załączniki dla określonego maila
     */
    List<AttachmentDocument> findByMailId(String mailId);

    /**
     * Usuwa wszystkie załączniki dla określonego maila
     */
    void deleteByMailId(String mailId);

    /**
     * Znajduje załącznik po ID maila i nazwie pliku
     */
    Optional<AttachmentDocument> findByMailIdAndFileName(String mailId, String fileName);

    /**
     * Sprawdza czy istnieje załącznik o podanym ID maila i nazwie pliku
     */
    boolean existsByMailIdAndFileName(String mailId, String fileName);

    /**
     * Znajduje załączniki o podanym rozmiarze dla danego maila
     */
    List<AttachmentDocument> findByMailIdAndSize(String mailId, long size);
}