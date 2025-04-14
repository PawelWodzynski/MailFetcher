package imap.mail.downloader;

import imap.mail.downloader.MailService;
import imap.mail.downloader.model.Mail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/mails")
@Tag(name = "Mail Controller", description = "Endpointy do zarządzania mailami")
public class MailController {

    private final MailService mailService;

    @Autowired
    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping("/fetch")
    @Operation(summary = "Pobierz i zapisz maile z serwera IMAP")
    public ResponseEntity<List<Mail>> fetchAndSaveEmails(
            @Parameter(description = "Host serwera IMAP") @RequestParam String host,
            @Parameter(description = "Nazwa użytkownika") @RequestParam String username,
            @Parameter(description = "Hasło") @RequestParam String password,
            @Parameter(description = "Port serwera (domyślnie 993)") @RequestParam(defaultValue = "993") int port,
            @Parameter(description = "Folder (domyślnie INBOX)") @RequestParam(defaultValue = "INBOX") String folder) {

        List<Mail> mails = mailService.fetchAndSaveEmails(host, port, username, password, folder);
        return ResponseEntity.ok(mails);
    }

    @GetMapping
    @Operation(summary = "Pobierz wszystkie zapisane maile")
    public ResponseEntity<List<Mail>> getAllSavedEmails() {
        List<Mail> mails = mailService.getAllSavedEmails();
        return ResponseEntity.ok(mails);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pobierz mail o określonym ID")
    public ResponseEntity<Mail> getMailById(@PathVariable String id) {
        return mailService.getMailById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search/title")
    @Operation(summary = "Wyszukaj maile po tytule")
    public ResponseEntity<List<Mail>> searchMailsByTitle(@RequestParam String query) {
        List<Mail> mails = mailService.searchMailsByTitle(query);
        return ResponseEntity.ok(mails);
    }

    @GetMapping("/search/sender")
    @Operation(summary = "Wyszukaj maile po adresie nadawcy")
    public ResponseEntity<List<Mail>> searchMailsBySender(@RequestParam String email) {
        List<Mail> mails = mailService.searchMailsBySender(email);
        return ResponseEntity.ok(mails);
    }

    @GetMapping("/{mailId}/attachment/{attachmentId}")
    @Operation(summary = "Pobierz załącznik z maila")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable String mailId,
            @PathVariable String attachmentId) {

        Optional<AttachmentDocument> attachment = mailService.getAttachmentById(attachmentId);

        if (attachment.isPresent() && attachment.get().getMailId().equals(mailId)) {
            AttachmentDocument attachmentDoc = attachment.get();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(attachmentDoc.getContentType()));
            headers.setContentDispositionFormData("attachment", attachmentDoc.getFileName());

            return new ResponseEntity<>(attachmentDoc.getFileData().getData(), headers, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }
}