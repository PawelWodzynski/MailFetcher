package imap.mail.downloader;

import org.bson.types.Binary;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "attachments")
@CompoundIndexes({
        @CompoundIndex(name = "mail_filename_idx",
                def = "{'mailId': 1, 'fileName': 1}",
                unique = true)
})
public class AttachmentDocument {

    @Id
    private String id;

    @Indexed
    private String mailId;       // ID wiadomości, do której należy załącznik

    private String fileName;
    private String contentType;
    private Binary fileData;     // Dane binarne załącznika
    private long size;

    public AttachmentDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMailId() {
        return mailId;
    }

    public void setMailId(String mailId) {
        this.mailId = mailId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Binary getFileData() {
        return fileData;
    }

    public void setFileData(Binary fileData) {
        this.fileData = fileData;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "AttachmentDocument{" +
                "id='" + id + '\'' +
                ", mailId='" + mailId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                '}';
    }
}