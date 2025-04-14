package imap.mail.downloader.model;

import imap.mail.downloader.MailAttachment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Mail {
    private String title;
    private String recipientEmail;
    private String senderEmail;
    private String content;
    private Date sentDate;
    private String messageId; // Dodane pole MessageID
    private List<MailAttachment> attachments = new ArrayList<>();

    public Mail() {
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<MailAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<MailAttachment> attachments) {
        this.attachments = attachments;
    }

    public void addAttachment(MailAttachment attachment) {
        this.attachments.add(attachment);
    }
}