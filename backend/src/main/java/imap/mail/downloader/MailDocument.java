package imap.mail.downloader;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document(collection = "emails")
@CompoundIndexes({
        @CompoundIndex(name = "sender_title_date_idx",
                def = "{'senderEmail': 1, 'title': 1, 'sentDate': 1}",
                unique = false),
        @CompoundIndex(name = "sender_recipient_title_date_idx",
                def = "{'senderEmail': 1, 'recipientEmail': 1, 'title': 1, 'sentDate': 1}",
                unique = false)
})
public class MailDocument {

    @Id
    private String id;

    private String title;

    private String recipientEmail;

    @Indexed
    private String senderEmail;

    private String content;

    @Indexed
    private Date sentDate;

    private Date savedDate;

    @Indexed(unique = true, sparse = true)
    private String messageId; // Message ID z indeksem unikalności (sparse=true pozwala na null)

    private List<String> attachmentIds = new ArrayList<>();  // Referencje do dokumentów załączników

    public MailDocument() {
        this.savedDate = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getSentDate() {
        return sentDate;
    }

    public void setSentDate(Date sentDate) {
        this.sentDate = sentDate;
    }

    public Date getSavedDate() {
        return savedDate;
    }

    public void setSavedDate(Date savedDate) {
        this.savedDate = savedDate;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<String> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<String> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public void addAttachmentId(String attachmentId) {
        if (this.attachmentIds == null) {
            this.attachmentIds = new ArrayList<>();
        }
        this.attachmentIds.add(attachmentId);
    }

    @Override
    public String toString() {
        return "MailDocument{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", senderEmail='" + senderEmail + '\'' +
                ", sentDate=" + sentDate +
                ", messageId='" + messageId + '\'' +
                ", attachments=" + (attachmentIds != null ? attachmentIds.size() : 0) +
                '}';
    }
}